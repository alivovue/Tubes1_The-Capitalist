package alternatif-bots-2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Soldier {

    private static final int PRESSURE_END_ROUND = 80;
    private static final int PRESSURE_REFILL_PERCENT = 10;

    private enum State { REFILL, EXPLORE, BUILD, ASSIST }
    private enum Phase { EARLY, MID, LATE }

    private static final int MSG_RUIN_WORKING  = 1;
    private static final int MSG_RUIN_NEED_HELP = 2;
    private static final int MSG_RUIN_ENEMY_MARK = 3;
    private static final int MSG_ASSIST_RUIN   = 4;
    private static final int MSG_COOLDOWN      = 8;
    private static final int ASSIST_EXPIRE     = 35;
    private static final int BUILD_PAINT_BUFFER = 10;
    private static final int REFILL_PERCENT    = 20;
    private static final int REFILL_MAX_TOWER_STEP_DIST = 20;
    private static final int BUILD_CACHE_MAX_TILES = 64;
    private static final int BUILD_CACHE_RESYNC_STALL = 3;
    private static final int IGNORE_RUIN_ROUNDS = 20;
    private static final int PRESSURE_TARGET_REACHED_DIST_SQ = 9;

    static MapLocation spawnTower    = null;
    static MapLocation enemyBase     = null;
    static MapLocation exploreTarget = null;
    static MapLocation assistTarget  = null;
    static MapLocation refillTowerLock = null;
    static MapLocation[] pressureTargets = new MapLocation[0];
    static int pressureTargetIdx = 0;
    static MapLocation ignoredRuin = null;
    static int ignoredRuinUntilRound = -1;
    static int assistExpireRound     = -1;
    static int lastMsgRound          = -999;
    static boolean reachedFirstTarget = false;
    static boolean triedDiagonal      = false;
    static boolean triedFallback1     = false;

    static MapLocation cachedRuin = null;
    static MapLocation[] cachedPatternLocs = new MapLocation[BUILD_CACHE_MAX_TILES];
    static boolean[] cachedPatternSecondary = new boolean[BUILD_CACHE_MAX_TILES];
    static int cachedPatternCount = 0;
    static int buildStallTurns = 0;

    public static void run(RobotController rc, RobotInfo[] enemies, RobotInfo[] allies) throws GameActionException {

        if (spawnTower == null) {
            for (RobotInfo ally : allies) {
                if (ally.getType().isTowerType()) { spawnTower = ally.getLocation(); break; }
            }
            if (spawnTower == null) return;
        }
        if (enemyBase == null) enemyBase = getEstimatedEnemyBase(rc);
        if (refillTowerLock == null) refillTowerLock = chooseRefillTowerLock(rc, allies);
        if (exploreTarget == null) exploreTarget = chooseSpreadTarget(rc);

        if (rc.getRoundNum() < PRESSURE_END_ROUND) {
            refreshPressureTargets(rc, allies);
            runPressureMode(rc, enemies, allies);
            return;
        }


        readMessages(rc);
        if (assistTarget != null && rc.getRoundNum() > assistExpireRound) {
            assistTarget = null;
        }
        if (ignoredRuin != null && rc.getRoundNum() > ignoredRuinUntilRound) {
            ignoredRuin = null;
        }

        Phase phase = getPhase(rc);
        RobotInfo tower       = nearestEnemyTower(rc, enemies);
        MapLocation nearestTower = chooseRefillTowerLock(rc, allies);
        MapLocation ruin      = chooseRuinTarget(rc, allies);
        State state           = State.EXPLORE;

        if (shouldRefill(rc, nearestTower)) {
            state = State.REFILL;
            ruin  = null;
        }

        if (state != State.REFILL && ruin != null) {
            if (hasEnemyPaintNearMarkedPattern(rc, ruin)) {
                if (rc.isActionReady()) markAndPaintOneTowerTile(rc, ruin);
                trySendTowerMessage(rc, allies, MSG_RUIN_ENEMY_MARK, ruin);
                ruin = null;
            } else if (hasEnoughPaintToBuild(rc)) {
                state = State.BUILD;
                trySendTowerMessage(rc, allies, MSG_RUIN_WORKING, ruin);
            } else {
                trySendTowerMessage(rc, allies, MSG_RUIN_NEED_HELP, ruin);
            }
        } else if (state != State.REFILL && assistTarget != null) {
            state = State.ASSIST;
        }

        if (state == State.EXPLORE) updateExploreTarget(rc, tower, phase);

        MapLocation objective = switch (state) {
            case REFILL  -> nearestTower;
            case BUILD   -> ruin;
            case ASSIST  -> assistTarget;
            case EXPLORE -> exploreTarget;
        };

        if (state == State.BUILD && rc.isActionReady()) runBuildAction(rc, ruin, allies);

        if (tower != null && holdOrRepositionOnAllyForTowerAttack(rc, tower.getLocation())) {
            if (rc.isActionReady() && rc.canAttack(tower.getLocation())) {
                rc.attack(tower.getLocation());
            }
            rc.setIndicatorString("phase=" + phase + " state=" + state
                    + " HOLD_TOWER=" + formatLocation(tower.getLocation()));
            return;
        }

        Direction nextDir = Nav.getMoveDirection(rc, objective);
        if (rc.isActionReady() && nextDir != null) paintNextTile(rc, nextDir);
        Nav.move(rc, nextDir);

        if (state == State.BUILD && rc.isActionReady()) runBuildAction(rc, ruin, allies);
        if (state != State.BUILD) {
            resetBuildCache();
        }

        rc.setIndicatorString("phase=" + phase + " state=" + state
                + " obj=" + formatLocation(objective) + " ruin=" + formatLocation(ruin)
                + " next=" + formatDirection(nextDir));

        if (tower != null && rc.canAttack(tower.getLocation())) rc.attack(tower.getLocation());
    }

    private static void runPressureMode(RobotController rc, RobotInfo[] enemies, RobotInfo[] allies)
            throws GameActionException {
        MapLocation nearestTower = chooseRefillTowerLock(rc, allies);
        if (shouldRefillPressure(rc, nearestTower)) {
            Direction d = Nav.getMoveDirection(rc, nearestTower);
            if (rc.isActionReady() && d != null) paintNextTile(rc, d);
            Nav.move(rc, d);
            rc.setIndicatorString("pressure REFILL");
            return;
        }

        RobotInfo enemyTower = nearestEnemyTower(rc, enemies);
        MapLocation objective;
        if (enemyTower != null) {
            objective = enemyTower.getLocation();
        } else {
            objective = getPressureObjectiveAndAdvanceIfReached(rc);
            if (objective == null) objective = enemyBase;
        }

        if (enemyTower != null && holdOrRepositionOnAllyForTowerAttack(rc, enemyTower.getLocation())) {
            if (rc.isActionReady() && rc.canAttack(enemyTower.getLocation())) {
                rc.attack(enemyTower.getLocation());
            }
            rc.setIndicatorString("pressure HOLD_TOWER=" + formatLocation(enemyTower.getLocation()));
            return;
        }

        Direction nextDir = Nav.getMoveDirection(rc, objective);

        if (rc.isActionReady() && nextDir != null) paintNextTile(rc, nextDir);

        Nav.move(rc, nextDir);

        if (rc.isActionReady() && enemyTower != null && rc.canAttack(enemyTower.getLocation())) {
            rc.attack(enemyTower.getLocation());
        }

        rc.setIndicatorString("pressure RUSH → " + formatLocation(objective)
            + " p=" + pressureTargetIdx + "/" + Math.max(1, pressureTargets.length)
            + " prim=" + getPressurePrimaryLabel(rc));
    }

    private static boolean holdOrRepositionOnAllyForTowerAttack(RobotController rc, MapLocation towerLoc)
            throws GameActionException {
        if (towerLoc == null) return false;

        MapLocation me = rc.getLocation();

        if (rc.canAttack(towerLoc) && isAllyPaintTile(rc, me)) {
            return true;
        }

        if (!rc.isMovementReady()) return false;

        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction d : Nav.DIRECTIONS) {
            if (!rc.canMove(d)) continue;
            MapLocation nxt = me.add(d);
            if (!isAllyPaintTile(rc, nxt)) continue;
            if (!canAttackFromLocation(rc, nxt, towerLoc)) continue;

            int score = 0;
            score += me.distanceSquaredTo(towerLoc) - nxt.distanceSquaredTo(towerLoc);

            if (score > bestScore) {
                bestScore = score;
                bestDir = d;
            }
        }

        if (bestDir != null && rc.canMove(bestDir)) {
            rc.move(bestDir);
            return true;
        }

        return false;
    }

    private static boolean isAllyPaintTile(RobotController rc, MapLocation loc) throws GameActionException {
        if (loc == null) return false;
        if (!rc.onTheMap(loc)) return false;
        if (!rc.canSenseLocation(loc)) return false;
        return rc.senseMapInfo(loc).getPaint().isAlly();
    }

    private static boolean canAttackFromLocation(RobotController rc, MapLocation from, MapLocation target) {
        if (from == null || target == null) return false;
        return from.distanceSquaredTo(target) <= rc.getType().actionRadiusSquared;
    }

    private static void refreshPressureTargets(RobotController rc, RobotInfo[] allies) {
        MapLocation oldCurrent = getCurrentPressureTarget();
        pressureTargets = buildPressureTargets(rc, allies);
        pressureTargetIdx = 0;
        if (oldCurrent != null) {
            for (int i = 0; i < pressureTargets.length; i++) {
                if (oldCurrent.equals(pressureTargets[i])) {
                    pressureTargetIdx = i;
                    break;
                }
            }
        }
    }

    private static MapLocation[] buildPressureTargets(RobotController rc, RobotInfo[] allies) {
        MapLocation diag = getEstimatedEnemyBase(rc);
        MapLocation vertical = getVerticalSymmetryTarget(rc);
        MapLocation horizontal = getHorizontalSymmetryTarget(rc);
        MapLocation otherTower = getOtherAllyTower(allies);

        boolean sameX = false;
        boolean sameY = false;
        for (RobotInfo ally : allies) {
            if (!ally.getType().isTowerType()) continue;
            MapLocation loc = ally.getLocation();
            if (loc.equals(spawnTower)) continue;
            if (loc.x == spawnTower.x) sameX = true;
            if (loc.y == spawnTower.y) sameY = true;
        }

        MapLocation primary;
        MapLocation second;
        MapLocation third;

        if (sameX && !sameY) {
            primary = vertical;
            second = diag;
            third = horizontal;
        } else if (sameY && !sameX) {
            primary = horizontal;
            second = diag;
            third = vertical;
        } else {
            primary = diag;
            int dx;
            int dy;
            if (otherTower != null) {
                dx = Math.abs(otherTower.x - spawnTower.x);
                dy = Math.abs(otherTower.y - spawnTower.y);
            } else {
                dx = Math.abs((rc.getMapWidth() - 1) - 2 * spawnTower.x);
                dy = Math.abs((rc.getMapHeight() - 1) - 2 * spawnTower.y);
            }
            if (dx < dy) {
                second = vertical;
                third = horizontal;
            } else {
                second = horizontal;
                third = vertical;
            }
        }

        MapLocation[] temp = new MapLocation[3];
        int count = 0;
        for (MapLocation cand : new MapLocation[] { primary, second, third }) {
            if (cand == null) continue;
            boolean duplicate = false;
            for (int i = 0; i < count; i++) {
                if (cand.equals(temp[i])) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) temp[count++] = cand;
        }

        if (count == 0) return new MapLocation[] { diag };

        MapLocation[] compact = new MapLocation[count];
        for (int i = 0; i < count; i++) compact[i] = temp[i];
        return compact;
    }

    private static MapLocation getOtherAllyTower(RobotInfo[] allies) {
        if (spawnTower == null) return null;
        for (RobotInfo ally : allies) {
            if (!ally.getType().isTowerType()) continue;
            MapLocation loc = ally.getLocation();
            if (!loc.equals(spawnTower)) return loc;
        }
        return null;
    }

    private static String getPressurePrimaryLabel(RobotController rc) {
        if (pressureTargets.length == 0) return "N";
        MapLocation p = pressureTargets[0];
        if (p.equals(getEstimatedEnemyBase(rc))) return "D";
        if (p.equals(getVerticalSymmetryTarget(rc))) return "V";
        if (p.equals(getHorizontalSymmetryTarget(rc))) return "H";
        return "?";
    }

    private static MapLocation getVerticalSymmetryTarget(RobotController rc) {
        int w = rc.getMapWidth();
        return new MapLocation(w - 1 - spawnTower.x, spawnTower.y);
    }

    private static MapLocation getHorizontalSymmetryTarget(RobotController rc) {
        int h = rc.getMapHeight();
        return new MapLocation(spawnTower.x, h - 1 - spawnTower.y);
    }

    private static MapLocation getCurrentPressureTarget() {
        if (pressureTargets.length == 0) return null;
        int idx = Math.min(pressureTargetIdx, pressureTargets.length - 1);
        return pressureTargets[idx];
    }

    private static void advancePressureTarget() {
        if (pressureTargets.length == 0) return;
        if (pressureTargetIdx < pressureTargets.length - 1) pressureTargetIdx++;
    }

    private static MapLocation getPressureObjectiveAndAdvanceIfReached(RobotController rc) {
        MapLocation objective = getCurrentPressureTarget();

        while (objective != null
                && rc.getLocation().distanceSquaredTo(objective) <= PRESSURE_TARGET_REACHED_DIST_SQ
                && pressureTargetIdx < pressureTargets.length - 1) {
            advancePressureTarget();
            objective = getCurrentPressureTarget();
        }

        return objective;
    }

    private static boolean shouldRefillPressure(RobotController rc, MapLocation tower) {
        if (tower == null) return false;
        int cap     = Math.max(1, rc.getType().paintCapacity);
        int percent = (rc.getPaint() * 100) / cap;
        if (percent >= PRESSURE_REFILL_PERCENT) return false;
        return stepDistance(rc.getLocation(), tower) <= REFILL_MAX_TOWER_STEP_DIST;
    }


    private static void updateExploreTarget(RobotController rc, RobotInfo tower, Phase phase) {
        if (phase == Phase.EARLY) {
            if (tower != null) {
                exploreTarget = tower.getLocation();
            } else {
                exploreTarget = getEstimatedEnemyBase(rc);
            }
            return;
        }
        if (!reachedFirstTarget && rc.getLocation().distanceSquaredTo(exploreTarget) <= 8) {
            reachedFirstTarget = true;
            exploreTarget = getEstimatedEnemyBase(rc);
        }
        if (reachedFirstTarget && !triedDiagonal && rc.getLocation().distanceSquaredTo(exploreTarget) <= 4) {
            triedDiagonal = true;
            if (tower == null) exploreTarget = getFallback1(rc);
        }
        if (triedDiagonal && !triedFallback1 && rc.getLocation().distanceSquaredTo(exploreTarget) <= 4) {
            triedFallback1 = true;
            if (tower == null) exploreTarget = getFallback2(rc);
        }
    }

    private static Phase getPhase(RobotController rc) {
        if (rc.getRoundNum() < 300)  return Phase.EARLY;
        if (rc.getRoundNum() < 1000) return Phase.MID;
        return Phase.LATE;
    }

    private static MapLocation chooseRuinTarget(RobotController rc, RobotInfo[] allies) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapLocation ruin : rc.senseNearbyRuins(-1)) {
            if (isTowerOnRuin(rc, ruin)) continue;
            if (!isAllyTerritory(ruin)) continue;
            if (ignoredRuin != null && ignoredRuin.equals(ruin) && rc.getRoundNum() <= ignoredRuinUntilRound) continue;
            if (!isPrimaryBuilderForRuin(rc, allies, ruin)) continue;
            int d = me.distanceSquaredTo(ruin);
            if (d < bestDist) { bestDist = d; best = ruin; }
        }
        return best;
    }

    private static boolean isPrimaryBuilderForRuin(RobotController rc, RobotInfo[] allies, MapLocation ruin) {
        int myDist = rc.getLocation().distanceSquaredTo(ruin);
        int myId = rc.getID();

        for (RobotInfo ally : allies) {
            if (ally.getType() != UnitType.SOLDIER) continue;
            if (ally.getID() == myId) continue;

            int d = ally.getLocation().distanceSquaredTo(ruin);
            if (d < myDist) return false;
            if (d == myDist && ally.getID() < myId) return false;
        }
        return true;
    }

    private static boolean isTowerOnRuin(RobotController rc, MapLocation ruin) throws GameActionException {
        if (!rc.canSenseLocation(ruin) || !rc.canSenseRobotAtLocation(ruin)) return false;
        RobotInfo info = rc.senseRobotAtLocation(ruin);
        return info != null && info.getType().isTowerType();
    }

    private static boolean hasEnemyPaintNearMarkedPattern(RobotController rc, MapLocation ruin) throws GameActionException {
        for (MapInfo tile : rc.senseNearbyMapInfos(ruin, 8)) {
            if (tile.getMark() == PaintType.EMPTY) continue;
            if (tile.getPaint().isEnemy()) return true;
        }
        return false;
    }

    private static boolean hasEnoughPaintToBuild(RobotController rc) {
        return rc.getPaint() >= UnitType.LEVEL_ONE_PAINT_TOWER.paintCost + BUILD_PAINT_BUFFER;
    }

    private static boolean shouldRefill(RobotController rc, MapLocation tower) {
        if (tower == null) return false;
        int cap     = Math.max(1, rc.getType().paintCapacity);
        int percent = (rc.getPaint() * 100) / cap;
        if (percent >= REFILL_PERCENT) return false;
        return stepDistance(rc.getLocation(), tower) <= REFILL_MAX_TOWER_STEP_DIST;
    }

    private static MapLocation chooseRefillTowerLock(RobotController rc, RobotInfo[] allies) {
        if (isStillValidTower(rc, refillTowerLock)) {
            return refillTowerLock;
        }

        MapLocation bestPaintTower = null;
        int bestPaintDist = Integer.MAX_VALUE;
        for (RobotInfo ally : allies) {
            if (!isPaintTowerType(ally.getType())) continue;
            int d = rc.getLocation().distanceSquaredTo(ally.getLocation());
            if (d < bestPaintDist) {
                bestPaintDist = d;
                bestPaintTower = ally.getLocation();
            }
        }

        if (bestPaintTower != null) {
            refillTowerLock = bestPaintTower;
            return refillTowerLock;
        }

        if (spawnTower != null) {
            refillTowerLock = spawnTower;
            return refillTowerLock;
        }

        refillTowerLock = nearestKnownAllyTower(rc, allies);
        return refillTowerLock;
    }

    private static boolean isStillValidTower(RobotController rc, MapLocation loc) {
        if (loc == null) return false;
        if (!rc.canSenseLocation(loc)) return true;
        return rc.canSenseRobotAtLocation(loc);
    }

    private static boolean isPaintTowerType(UnitType t) {
        return t == UnitType.LEVEL_ONE_PAINT_TOWER
            || t == UnitType.LEVEL_TWO_PAINT_TOWER
            || t == UnitType.LEVEL_THREE_PAINT_TOWER;
    }

    private static void markAndPaintOneTowerTile(RobotController rc, MapLocation ruin) throws GameActionException {
        if (rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruin))
            rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruin);
        for (MapInfo tile : rc.senseNearbyMapInfos(ruin, 8)) {
            if (tile.getMark() == PaintType.EMPTY) continue;
            if (!rc.canAttack(tile.getMapLocation())) continue;
            rc.attack(tile.getMapLocation(), tile.getMark() == PaintType.ALLY_SECONDARY);
            return;
        }
    }

    private static void runBuildAction(RobotController rc, MapLocation ruin, RobotInfo[] allies) throws GameActionException {
        if (ruin == null) return;

        if (cachedRuin == null || !cachedRuin.equals(ruin)) {
            resetBuildCache();
            cachedRuin = ruin;
        }

        if (hasEnemyPaintNearMarkedPattern(rc, ruin)) {
            trySendTowerMessage(rc, allies, MSG_RUIN_ENEMY_MARK, ruin);
            ignoredRuin = ruin;
            ignoredRuinUntilRound = rc.getRoundNum() + IGNORE_RUIN_ROUNDS;
            resetBuildCache();
            return;
        }

        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruin)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruin);
            if (assistTarget != null && assistTarget.equals(ruin)) assistTarget = null;
            resetBuildCache();
            return;
        }

        if (cachedPatternCount == 0) {
            refreshBuildPatternCache(rc, ruin, false);
        }

        if (paintFromCachedPattern(rc)) {
            buildStallTurns = 0;
            return;
        }

        buildStallTurns++;
        if (buildStallTurns >= BUILD_CACHE_RESYNC_STALL) {
            refreshBuildPatternCache(rc, ruin, true);
            buildStallTurns = 0;
            paintFromCachedPattern(rc);
        }
    }

    private static void refreshBuildPatternCache(RobotController rc, MapLocation ruin, boolean forceRemark)
            throws GameActionException {
        if (ruin == null) return;

        if (forceRemark || cachedPatternCount == 0) {
            if (rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruin)) {
                rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruin);
            }
        }

        cachedPatternCount = 0;
        for (MapInfo tile : rc.senseNearbyMapInfos(ruin, 8)) {
            PaintType mark = tile.getMark();
            if (mark == PaintType.EMPTY) continue;
            if (cachedPatternCount >= BUILD_CACHE_MAX_TILES) break;
            cachedPatternLocs[cachedPatternCount] = tile.getMapLocation();
            cachedPatternSecondary[cachedPatternCount] = (mark == PaintType.ALLY_SECONDARY);
            cachedPatternCount++;
        }
    }

    private static boolean paintFromCachedPattern(RobotController rc) throws GameActionException {
        for (int i = 0; i < cachedPatternCount; i++) {
            MapLocation loc = cachedPatternLocs[i];
            if (loc == null) continue;
            if (!rc.canAttack(loc)) continue;
            if (!rc.canSenseLocation(loc)) continue;

            MapInfo info = rc.senseMapInfo(loc);
            if (info.getPaint().isEnemy()) continue;

            PaintType desired;
            if (cachedPatternSecondary[i]) {
                desired = PaintType.ALLY_SECONDARY;
            } else {
                desired = PaintType.ALLY_PRIMARY;
            }
            if (info.getPaint() == desired) continue;

            rc.attack(loc, cachedPatternSecondary[i]);
            return true;
        }
        return false;
    }

    private static void resetBuildCache() {
        cachedRuin = null;
        cachedPatternCount = 0;
        buildStallTurns = 0;
    }

    private static void trySendTowerMessage(RobotController rc, RobotInfo[] allies, int tag, MapLocation loc)
            throws GameActionException {
        if (loc == null) return;
        if (rc.getRoundNum() - lastMsgRound < MSG_COOLDOWN) return;
        int msg  = encode(tag, loc);
        boolean sent = false;
        if (spawnTower != null && rc.canSendMessage(spawnTower, msg)) {
            rc.sendMessage(spawnTower, msg);
            sent = true;
        } else {
            for (RobotInfo ally : allies) {
                if (!ally.getType().isTowerType()) continue;
                if (!rc.canSendMessage(ally.getLocation(), msg)) continue;
                rc.sendMessage(ally.getLocation(), msg);
                sent = true;
                break;
            }
        }
        if (sent) lastMsgRound = rc.getRoundNum();
    }

    private static void readMessages(RobotController rc) throws GameActionException {
        for (Message m : rc.readMessages(-1)) {
            int raw = m.getBytes();
            if (decodeTag(raw) != MSG_ASSIST_RUIN) continue;
            MapLocation loc = decodeLoc(raw);
            if (loc == null) continue;
            if (!isAllyTerritory(loc)) continue;
            assistTarget      = loc;
            assistExpireRound = rc.getRoundNum() + ASSIST_EXPIRE;
        }
    }

    private static boolean isAllyTerritory(MapLocation loc) {
        if (loc == null || spawnTower == null || enemyBase == null) return false;
        return loc.distanceSquaredTo(spawnTower) <= loc.distanceSquaredTo(enemyBase);
    }

    private static int encode(int tag, MapLocation loc) {
        return (tag << 20) | ((loc.x & 1023) << 10) | (loc.y & 1023);
    }
    private static int decodeTag(int raw) { return (raw >>> 20) & 1023; }
    private static MapLocation decodeLoc(int raw) {
        return new MapLocation((raw >>> 10) & 1023, raw & 1023);
    }

    private static MapLocation nearestKnownAllyTower(RobotController rc, RobotInfo[] allies) {
        MapLocation best = spawnTower;
        int bestDist;
        if (best == null) {
            bestDist = Integer.MAX_VALUE;
        } else {
            bestDist = rc.getLocation().distanceSquaredTo(best);
        }
        for (RobotInfo ally : allies) {
            if (!ally.getType().isTowerType()) continue;
            int d = rc.getLocation().distanceSquaredTo(ally.getLocation());
            if (d < bestDist) { bestDist = d; best = ally.getLocation(); }
        }
        return best;
    }

    private static int stepDistance(MapLocation a, MapLocation b) {
        if (a == null || b == null) return Integer.MAX_VALUE / 4;
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y));
    }

    private static MapLocation chooseSpreadTarget(RobotController rc) {
        int maxX = rc.getMapWidth() - 1;
        int maxY = rc.getMapHeight() - 1;
        MapLocation[] candidates = {
            new MapLocation(maxX - spawnTower.x, spawnTower.y),
            new MapLocation(spawnTower.x, maxY - spawnTower.y),
            new MapLocation(maxX - spawnTower.x, maxY - spawnTower.y)
        };
        return candidates[Math.floorMod(rc.getID() / 2, candidates.length)];
    }

    private static MapLocation getEstimatedEnemyBase(RobotController rc) {
        int w = rc.getMapWidth(); int h = rc.getMapHeight();
        return new MapLocation(w - 1 - spawnTower.x, h - 1 - spawnTower.y);
    }

    private static MapLocation getFallback1(RobotController rc) {
        int w = rc.getMapWidth(); int h = rc.getMapHeight();
        int dx = Math.abs(w - 1 - 2 * spawnTower.x);
        int dy = Math.abs(h - 1 - 2 * spawnTower.y);
        if (dx < dy) return new MapLocation(spawnTower.x, h - 1 - spawnTower.y);
        return new MapLocation(w - 1 - spawnTower.x, spawnTower.y);
    }

    private static MapLocation getFallback2(RobotController rc) {
        int w = rc.getMapWidth(); int h = rc.getMapHeight();
        int dx = Math.abs(w - 1 - 2 * spawnTower.x);
        int dy = Math.abs(h - 1 - 2 * spawnTower.y);
        if (dx < dy) return new MapLocation(w - 1 - spawnTower.x, spawnTower.y);
        return new MapLocation(spawnTower.x, h - 1 - spawnTower.y);
    }

    private static void paintNextTile(RobotController rc, Direction dir) throws GameActionException {
        MapLocation next = rc.getLocation().add(dir);
        if (!rc.canAttack(next)) return;
        MapInfo info = rc.senseMapInfo(next);
        if (info.getPaint() == PaintType.EMPTY || info.getPaint().isEnemy()) rc.attack(next);
    }

    private static RobotInfo nearestEnemyTower(RobotController rc, RobotInfo[] enemies) {
        RobotInfo best = null; int bestDist = Integer.MAX_VALUE;
        for (RobotInfo e : enemies) {
            if (!e.getType().isTowerType()) continue;
            int d = rc.getLocation().distanceSquaredTo(e.getLocation());
            if (d < bestDist) { bestDist = d; best = e; }
        }
        return best;
    }

    private static String formatLocation(MapLocation loc) {
        if (loc == null) return "null";
        return "(" + loc.x + "," + loc.y + ")";
    }
    private static String formatDirection(Direction d) {
        if (d == null || d == Direction.CENTER) return "C";
        return d.name();
    }
}
