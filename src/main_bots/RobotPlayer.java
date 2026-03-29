package main_bots;

import battlecode.common.*;

import java.util.Random;

public class RobotPlayer {

    static final Direction[] DIRECTIONS = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST
    };

    static final Direction[] CARDINALS = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    };

    static final int ENEMY_SIDE_BORDER_MARGIN = 1;

    static final int EXPLORE_MIN_TTL = 8;
    static final int EXPLORE_MAX_TTL = 16;
    static final int EXPLORE_TARGET_DISTANCE = 6;
    static final int EXPLORE_EDGE_LOOKAHEAD = 3;

    static final int CHIP_RESERVE = 1000;
    static final int MINIMUM_CHIP_UPGRADE_TWR = 3000;

    static final int SOLDIER_CHIP_COST = 250;
    static final int SOLDIER_PAINT_COST = 200;

    static final int MOPPER_CHIP_COST = 300;
    static final int MOPPER_PAINT_COST = 100;

    static final int SPLASHER_CHIP_COST = 400;
    static final int SPLASHER_PAINT_COST = 300;

    static final int EARLY_SOLDIER_ONLY_ROUNDS = 20;

    static final int TOTAL_TROOP_RATIO = 20;
    static final int SOLDIER_SPLASHER_TOTAL = 16;
    static final int FIXED_MOPPER_RATIO = 4;

    static final double TROOP_FORMULA_BASE = 8.0;
    static final double K_TOWER_RATIO = 0.4;
    static final double TROOP_FORMULA_MIN = 0.0;

    static final int RUIN_DEFENSE_ENEMY_RADIUS_SQ = 8;
    static final int RUIN_DEFENSE_ENEMY_THRESHOLD = 3;

    static final int RUIN_HASH_X_MULT = 31;
    static final int RUIN_HASH_Y_MULT = 17;
    static final int RUIN_HASH_OFFSET = 7;
    static final int RUIN_MONEY_MOD = 4;
    static final int RUIN_MONEY_MOD_TRIGGER = 0;

    static final int SOLDIER_REFILL_TARGET = 120;
    static final int SOLDIER_LOW_PAINT = 35;

    static final int MOPPER_REFILL_TARGET = 0;
    static final int MOPPER_LOW_PAINT = 0;

    static final int MOPPER_FOLLOW_TTL_RESET = 10;
    static final int MOPPER_FOLLOW_KEEP_DIST_SQ = 2;
    static final int MOPPER_FOLLOW_SPLASHER_LOW_PAINT = 140;
    static final int MOPPER_FOLLOW_SOLDIER_LOW_PAINT = 80;

    static final int MOPPER_MIN_KEEP_PAINT = 15;
    static final int MOPPER_NO_GIVE_BELOW = 25;
    static final int MOPPER_TRANSFER_RANGE_SQ = 2;
    static final int MOPPER_NEED_MIN = 10;

    static final int SUPPORT_SOLDIER_TARGET_PAINT = 100;
    static final int SUPPORT_SPLASHER_TARGET_PAINT = 170;
    static final int SUPPORT_MOPPER_TARGET_PAINT = 70;

    static final int SWING_LENGTH = 2;
    static final int SWING_WIDTH_HALF = 1;

    static final int SPLASHER_REFILL_TARGET = 0;
    static final int SPLASHER_LOW_PAINT = 0;

    static final int SPLASHER_TARGET_RADIUS_SQ = 4;
    static final int SPLASHER_TARGET_CLOSE_DIST_SQ = 2;

    static final int[] SPLASH_DX = {
            0,
            -1,  0,  1,
            -2, -1,  0,  1,  2,
            -1,  0,  1,
            0
    };

    static final int[] SPLASH_DY = {
            -2,
            -1, -1, -1,
            0,  0,  0,  0,  0,
            1,  1,  1,
            2
    };

    static final int MOVE_PROGRESS_SAFE_WEIGHT = 4;
    static final int MOVE_PROGRESS_AGGRO_WEIGHT = 10;

    static final int MOVE_SAFE_ALLY_SCORE = 90;
    static final int MOVE_SAFE_EMPTY_SCORE = 25;
    static final int MOVE_SAFE_ENEMY_SCORE = -90;

    static final int MOVE_AGGRO_ALLY_SCORE = 20;
    static final int MOVE_AGGRO_EMPTY_SCORE = 24;
    static final int MOVE_AGGRO_ENEMY_SCORE = 5;

    static final int MOVE_PREFERRED_DIR_BONUS = 12;
    static final int MOVE_ADJ_ALLY_PENALTY = 14;

    static Random rng;

    static MapLocation homeTower = null;
    static int spawnCycle = 0;

    static MapLocation assignedRuin = null;
    static Direction exploreDir = null;
    static int exploreTTL = 0;

    static MapLocation mopperFollowTarget = null;
    static int mopperFollowTTL = 0;

    static Direction splasherExploreDir = null;
    static int splasherExploreTTL = 0;

    public static void run(RobotController rc) throws GameActionException {
        if (rng == null) {
            rng = new Random(rc.getID());
        }

        while (true) {
            try {
                rememberNearestFriendlyTower(rc);

                UnitType type = rc.getType();

                if (isTowerType(type)) {
                    runTower(rc);
                } else if (type == UnitType.SOLDIER) {
                    runSoldier(rc);
                } else if (type == UnitType.MOPPER) {
                    runMopper(rc);
                } else if (type == UnitType.SPLASHER) {
                    runSplasher(rc);
                }

            } catch (GameActionException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    static void runTower(RobotController rc) throws GameActionException {
        if (tryUpgradeTower(rc)) {
            return;
        }

        RobotInfo target = chooseBestTowerAttackTarget(rc);
        if (target != null) {
            if (rc.canAttack(target.getLocation())) {
                rc.attack(target.getLocation());
            }
        }

        UnitType buildType = chooseGreedySpawnType(rc);
        if (buildType == null) {
            return;
        }

        if (!canAffordWithReserve(rc, buildType)) {
            return;
        }

        MapLocation targetLoc = chooseSpawnTargetForType(rc, buildType);
        MapLocation spawnLoc = chooseBestSpawnLocation(rc, buildType, targetLoc);

        if (spawnLoc != null) {
            if (rc.canBuildRobot(buildType, spawnLoc)) {
                rc.buildRobot(buildType, spawnLoc);
                spawnCycle++;
            }
        }
    }

    static boolean tryUpgradeTower(RobotController rc) throws GameActionException {
        if (!isTowerType(rc.getType())) {
            return false;
        }

        if (rc.getChips() < MINIMUM_CHIP_UPGRADE_TWR) {
            return false;
        }

        UnitType type = rc.getType();
        if (type == UnitType.LEVEL_THREE_PAINT_TOWER
                || type == UnitType.LEVEL_THREE_MONEY_TOWER
                || type == UnitType.LEVEL_THREE_DEFENSE_TOWER) {
            return false;
        }

        MapLocation me = rc.getLocation();
        if (rc.canUpgradeTower(me)) {
            rc.upgradeTower(me);
            return true;
        }

        return false;
    }

    static UnitType chooseGreedySpawnType(RobotController rc) throws GameActionException {
        if (rc.getChips() <= CHIP_RESERVE) {
            return null;
        }

        if (rc.getRoundNum() < EARLY_SOLDIER_ONLY_ROUNDS) {
            if (canAffordWithReserve(rc, UnitType.SOLDIER)) {
                return UnitType.SOLDIER;
            }
            return null;
        }

        SpawnWeights weights = computeSpawnWeights(rc);
        int phase = Math.floorMod(spawnCycle, TOTAL_TROOP_RATIO);

        UnitType desired;
        if (phase < weights.soldierWeight) {
            desired = UnitType.SOLDIER;
        } else if (phase < weights.soldierWeight + weights.splasherWeight) {
            desired = UnitType.SPLASHER;
        } else {
            desired = UnitType.MOPPER;
        }

        if (desired == UnitType.SOLDIER) {
            if (canAffordWithReserve(rc, UnitType.SOLDIER)) {
                return UnitType.SOLDIER;
            }
            if (canAffordWithReserve(rc, UnitType.MOPPER)) {
                return UnitType.MOPPER;
            }
            if (canAffordWithReserve(rc, UnitType.SPLASHER)) {
                return UnitType.SPLASHER;
            }
            return null;
        }

        if (desired == UnitType.SPLASHER) {
            if (canAffordWithReserve(rc, UnitType.SPLASHER)) {
                return UnitType.SPLASHER;
            }
            return null;
        }

        if (canAffordWithReserve(rc, UnitType.MOPPER)) {
            return UnitType.MOPPER;
        }
        if (canAffordWithReserve(rc, UnitType.SOLDIER)) {
            return UnitType.SOLDIER;
        }
        if (canAffordWithReserve(rc, UnitType.SPLASHER)) {
            return UnitType.SPLASHER;
        }

        return null;
    }

    static SpawnWeights computeSpawnWeights(RobotController rc) throws GameActionException {
        int p = rc.getNumberTowers();

        double cRaw = TROOP_FORMULA_BASE - K_TOWER_RATIO * p;
        double cClamped = clamp(cRaw, TROOP_FORMULA_MIN, SOLDIER_SPLASHER_TOTAL);

        int soldierWeight = (int) Math.round(cClamped);
        if (soldierWeight < 0) {
            soldierWeight = 0;
        }
        if (soldierWeight > SOLDIER_SPLASHER_TOTAL) {
            soldierWeight = SOLDIER_SPLASHER_TOTAL;
        }

        int splasherWeight = SOLDIER_SPLASHER_TOTAL - soldierWeight;
        int mopperWeight = FIXED_MOPPER_RATIO;

        return new SpawnWeights(soldierWeight, splasherWeight, mopperWeight);
    }

    static MapLocation chooseSpawnTargetForType(RobotController rc, UnitType type) throws GameActionException {
        if (type == UnitType.SPLASHER) {
            return chooseTowerSpawnTarget(rc);
        }

        if (type == UnitType.MOPPER) {
            MapLocation support = chooseBestSupportAllyTarget(rc);
            if (support != null) {
                return support;
            }

            MapLocation frontline = chooseFrontlineTarget(rc);
            if (frontline != null) {
                return frontline;
            }
        }

        return chooseTowerSpawnTarget(rc);
    }

    static MapLocation chooseTowerSpawnTarget(RobotController rc) throws GameActionException {
        MapLocation ruin = chooseBestVisibleOpenRuin(rc);
        if (ruin != null) {
            return ruin;
        }

        MapLocation enemyPaint = nearestVisibleEnemyPaint(rc);
        if (enemyPaint != null) {
            return enemyPaint;
        }

        return new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
    }

    static MapLocation chooseBestSpawnLocation(RobotController rc, UnitType type, MapLocation target) throws GameActionException {
        MapLocation me = rc.getLocation();

        MapLocation best = null;
        int bestCategory = Integer.MAX_VALUE;
        int bestDist = Integer.MAX_VALUE;
        int bestAdj = Integer.MAX_VALUE;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (dx * dx + dy * dy > 4) {
                    continue;
                }

                MapLocation loc = new MapLocation(me.x + dx, me.y + dy);
                if (!rc.canBuildRobot(type, loc)) {
                    continue;
                }

                PaintType p = PaintType.EMPTY;
                if (rc.canSenseLocation(loc)) {
                    p = rc.senseMapInfo(loc).getPaint();
                }

                int adj = countAdjacentAlliesAt(rc, loc);
                int category = spawnCategory(p, adj);

                int dist;
                if (target == null) {
                    dist = me.distanceSquaredTo(loc);
                } else {
                    dist = loc.distanceSquaredTo(target);
                }

                if (best == null
                        || category < bestCategory
                        || (category == bestCategory && dist < bestDist)
                        || (category == bestCategory && dist == bestDist && adj < bestAdj)) {
                    best = loc;
                    bestCategory = category;
                    bestDist = dist;
                    bestAdj = adj;
                }
            }
        }

        return best;
    }

    static RobotInfo chooseBestTowerAttackTarget(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();

        RobotInfo bestTower = null;
        int bestTowerHp = Integer.MAX_VALUE;
        int bestTowerDist = Integer.MAX_VALUE;

        RobotInfo bestUnit = null;
        int bestUnitHp = Integer.MAX_VALUE;
        int bestUnitDist = Integer.MAX_VALUE;

        for (RobotInfo ri : rc.senseNearbyRobots()) {
            if (ri.getTeam() == rc.getTeam()) {
                continue;
            }
            if (!rc.canAttack(ri.getLocation())) {
                continue;
            }

            int hp = ri.getHealth();
            int dist = me.distanceSquaredTo(ri.getLocation());

            if (isTowerType(ri.getType())) {
                if (bestTower == null
                        || hp < bestTowerHp
                        || (hp == bestTowerHp && dist < bestTowerDist)) {
                    bestTower = ri;
                    bestTowerHp = hp;
                    bestTowerDist = dist;
                }
            } else {
                if (bestUnit == null
                        || hp < bestUnitHp
                        || (hp == bestUnitHp && dist < bestUnitDist)) {
                    bestUnit = ri;
                    bestUnitHp = hp;
                    bestUnitDist = dist;
                }
            }
        }

        if (bestTower != null) {
            return bestTower;
        }
        return bestUnit;
    }

    static boolean canAffordWithReserve(RobotController rc, UnitType type) {
        int chipCost = getChipCost(type);
        int paintCost = getPaintCost(type);

        return rc.getChips() - chipCost > CHIP_RESERVE && rc.getPaint() >= paintCost;
    }

    static int getChipCost(UnitType type) {
        if (type == UnitType.SOLDIER) {
            return SOLDIER_CHIP_COST;
        }
        if (type == UnitType.MOPPER) {
            return MOPPER_CHIP_COST;
        }
        if (type == UnitType.SPLASHER) {
            return SPLASHER_CHIP_COST;
        }
        return 0;
    }

    static int getPaintCost(UnitType type) {
        if (type == UnitType.SOLDIER) {
            return SOLDIER_PAINT_COST;
        }
        if (type == UnitType.MOPPER) {
            return MOPPER_PAINT_COST;
        }
        if (type == UnitType.SPLASHER) {
            return SPLASHER_PAINT_COST;
        }
        return 0;
    }

    static void runSoldier(RobotController rc) throws GameActionException {
        tryAttackEnemyTower(rc);

        if (rc.getPaint() <= SOLDIER_LOW_PAINT) {
            soldierRefillMode(rc);
            return;
        }

        MapLocation visibleRuin = chooseBestVisibleOpenRuin(rc);
        if (visibleRuin != null) {
            assignedRuin = visibleRuin;
        }

        if (assignedRuin != null) {
            if (handleSoldierRuinObjective(rc)) {
                return;
            }
            assignedRuin = null;
        }

        exploreAndPaint(rc);
    }

    static void soldierRefillMode(RobotController rc) throws GameActionException {
        if (tryWithdrawPaint(rc, SOLDIER_REFILL_TARGET)) {
            return;
        }

        MapLocation refill = nearestVisibleFriendlyTower(rc);
        if (refill == null) {
            refill = homeTower;
        }
        if (refill == null) {
            refill = chooseExploreTarget(rc);
        }

        moveGreedy(rc, refill, null, true);
        tryWithdrawPaint(rc, SOLDIER_REFILL_TARGET);
    }

    static boolean handleSoldierRuinObjective(RobotController rc) throws GameActionException {
        MapLocation ruin = assignedRuin;
        if (ruin == null) {
            return false;
        }

        if (rc.canSenseLocation(ruin)) {
            MapInfo ruinInfo = rc.senseMapInfo(ruin);
            if (!ruinInfo.hasRuin()) {
                return false;
            }

            RobotInfo occupant = rc.senseRobotAtLocation(ruin);
            if (occupant != null) {
                if (isTowerType(occupant.getType())) {
                    return false;
                }
            }
        }

        UnitType towerType = chooseTowerTypeForRuin(rc, ruin);

        if (rc.canCompleteTowerPattern(towerType, ruin)) {
            rc.completeTowerPattern(towerType, ruin);
            return true;
        }

        if (rc.canMarkTowerPattern(towerType, ruin)) {
            rc.markTowerPattern(towerType, ruin);
        }

        PaintJob job = chooseBestSoldierPatternPaintJob(rc, ruin);
        if (job == null) {
            moveGreedy(rc, ruin, null, true);
            tryPaintExplorePath(rc);
            return true;
        }

        if (!rc.canAttack(job.loc)) {
            moveGreedy(rc, job.loc, null, true);
        }

        job = chooseBestSoldierPatternPaintJob(rc, ruin);
        if (job != null) {
            if (rc.isActionReady() && rc.canAttack(job.loc)) {
                rc.attack(job.loc, job.useSecondary);
            }
        }

        if (rc.canCompleteTowerPattern(towerType, ruin)) {
            rc.completeTowerPattern(towerType, ruin);
        }

        return true;
    }

    static UnitType chooseTowerTypeForRuin(RobotController rc, MapLocation ruin) throws GameActionException {
        int enemiesNear = 0;
        RobotInfo[] robots = rc.senseNearbyRobots();

        for (RobotInfo ri : robots) {
            if (ri.getTeam() != rc.getTeam()) {
                if (ri.getLocation().distanceSquaredTo(ruin) <= RUIN_DEFENSE_ENEMY_RADIUS_SQ) {
                    enemiesNear++;
                }
            }
        }

        if (enemiesNear >= RUIN_DEFENSE_ENEMY_THRESHOLD) {
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }

        int h = Math.abs(
                ruin.x * RUIN_HASH_X_MULT
                        + ruin.y * RUIN_HASH_Y_MULT
                        + RUIN_HASH_OFFSET
        ) % RUIN_MONEY_MOD;

        if (h == RUIN_MONEY_MOD_TRIGGER) {
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }
        return UnitType.LEVEL_ONE_PAINT_TOWER;
    }

    static PaintJob chooseBestSoldierPatternPaintJob(RobotController rc, MapLocation ruin) throws GameActionException {
        MapLocation me = rc.getLocation();

        PaintJob bestAttackable = null;
        int bestAttackableDist = Integer.MAX_VALUE;
        int bestAttackableShape = Integer.MAX_VALUE;

        PaintJob bestAny = null;
        int bestAnyDist = Integer.MAX_VALUE;
        int bestAnyShape = Integer.MAX_VALUE;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                MapLocation loc = new MapLocation(ruin.x + dx, ruin.y + dy);

                if (!rc.onTheMap(loc)) {
                    continue;
                }
                if (loc.equals(ruin)) {
                    continue;
                }
                if (!rc.canSenseLocation(loc)) {
                    continue;
                }

                MapInfo mi = rc.senseMapInfo(loc);
                if (mi.hasRuin() || mi.isWall()) {
                    continue;
                }

                PaintType desired = desiredTowerPaint(mi);
                if (desired == PaintType.EMPTY) {
                    continue;
                }

                PaintType cur = mi.getPaint();
                boolean paintableBySoldier = (cur == PaintType.EMPTY || cur.isAlly());
                if (!paintableBySoldier) {
                    continue;
                }
                if (cur == desired) {
                    continue;
                }

                boolean useSecondary = false;
                if (desired == PaintType.ALLY_SECONDARY) {
                    useSecondary = true;
                }

                int dist = me.distanceSquaredTo(loc);
                int shape = Math.abs(dx) + Math.abs(dy);

                if (rc.canAttack(loc)) {
                    if (bestAttackable == null
                            || dist < bestAttackableDist
                            || (dist == bestAttackableDist && shape < bestAttackableShape)) {
                        bestAttackable = new PaintJob(loc, useSecondary);
                        bestAttackableDist = dist;
                        bestAttackableShape = shape;
                    }
                } else {
                    if (bestAny == null
                            || dist < bestAnyDist
                            || (dist == bestAnyDist && shape < bestAnyShape)) {
                        bestAny = new PaintJob(loc, useSecondary);
                        bestAnyDist = dist;
                        bestAnyShape = shape;
                    }
                }
            }
        }

        if (bestAttackable != null) {
            return bestAttackable;
        }
        return bestAny;
    }

    static PaintType desiredTowerPaint(MapInfo mi) {
        PaintType mark = mi.getMark();
        if (mark == PaintType.ALLY_PRIMARY) {
            return PaintType.ALLY_PRIMARY;
        }
        return PaintType.ALLY_SECONDARY;
    }

    static void exploreAndPaint(RobotController rc) throws GameActionException {
        refreshExploreDirection(rc);

        MapLocation target = rc.getLocation().translate(
                exploreDir.dx * EXPLORE_TARGET_DISTANCE,
                exploreDir.dy * EXPLORE_TARGET_DISTANCE
        );

        if (rc.isMovementReady()) {
            Direction step = chooseGreedyMoveDirection(rc, target, exploreDir, true);
            if (step != null) {
                if (rc.canMove(step)) {
                    rc.move(step);
                    exploreTTL--;
                }
            } else {
                resetExploreDirection();
            }
        }

        tryPaintExplorePath(rc);
        tryAttackEnemyTower(rc);
    }

    static void tryPaintExplorePath(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) {
            return;
        }

        MapLocation me = rc.getLocation();

        if (rc.canSenseLocation(me)) {
            MapInfo mi = rc.senseMapInfo(me);
            if (mi.getPaint() == PaintType.EMPTY) {
                if (rc.canAttack(me)) {
                    rc.attack(me, false);
                    return;
                }
            }
        }

        MapLocation bestForward = null;
        int bestForwardDist = Integer.MAX_VALUE;

        MapLocation bestAny = null;
        int bestAnyDist = Integer.MAX_VALUE;

        for (MapInfo mi : rc.senseNearbyMapInfos()) {
            MapLocation loc = mi.getMapLocation();

            if (!rc.canAttack(loc)) {
                continue;
            }
            if (mi.getPaint() != PaintType.EMPTY) {
                continue;
            }

            int dist = me.distanceSquaredTo(loc);

            if (exploreDir != null) {
                if (me.directionTo(loc) == exploreDir) {
                    if (bestForward == null || dist < bestForwardDist) {
                        bestForward = loc;
                        bestForwardDist = dist;
                    }
                }
            }

            if (bestAny == null || dist < bestAnyDist) {
                bestAny = loc;
                bestAnyDist = dist;
            }
        }

        if (bestForward != null) {
            rc.attack(bestForward, false);
            return;
        }

        if (bestAny != null) {
            rc.attack(bestAny, false);
        }
    }

    static void tryAttackEnemyTower(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) {
            return;
        }

        RobotInfo[] robots = rc.senseNearbyRobots();
        RobotInfo best = null;
        int bestHp = Integer.MAX_VALUE;

        for (RobotInfo ri : robots) {
            if (ri.getTeam() != rc.getTeam()
                    && isTowerType(ri.getType())
                    && rc.canAttack(ri.getLocation())) {
                if (ri.getHealth() < bestHp) {
                    bestHp = ri.getHealth();
                    best = ri;
                }
            }
        }

        if (best != null) {
            rc.attack(best.getLocation(), false);
        }
    }

    static void runMopper(RobotController rc) throws GameActionException {
        if (rc.getPaint() <= MOPPER_LOW_PAINT) {
            mopperRefillMode(rc);
            return;
        }

        if (rc.isActionReady()) {
            if (!tryGivePaintToBestAlly(rc)) {
                tryMopBestEnemyPaint(rc);
            }
        }

        MapLocation target = chooseMopperFollowTarget(rc);
        boolean avoidEnemyPaint = false;

        if (target == null) {
            target = chooseBestSupportAllyTarget(rc);
            avoidEnemyPaint = false;
        }
        if (target == null) {
            target = chooseFrontlineTarget(rc);
            avoidEnemyPaint = false;
        }
        if (target == null) {
            target = chooseBestVisibleOpenRuin(rc);
            avoidEnemyPaint = false;
        }
        if (target == null) {
            target = chooseExploreTarget(rc);
            avoidEnemyPaint = true;
        }

        moveGreedy(rc, target, null, avoidEnemyPaint);

        if (rc.isActionReady()) {
            if (!tryGivePaintToBestAlly(rc)) {
                tryMopBestEnemyPaint(rc);
            }
        }
    }

    static void mopperRefillMode(RobotController rc) throws GameActionException {
        if (tryWithdrawPaint(rc, MOPPER_REFILL_TARGET)) {
            return;
        }

        MapLocation refill = nearestVisibleFriendlyTower(rc);
        if (refill == null) {
            refill = homeTower;
        }
        if (refill == null) {
            refill = chooseExploreTarget(rc);
        }

        moveGreedy(rc, refill, null, true);
        tryWithdrawPaint(rc, MOPPER_REFILL_TARGET);
    }

    static MapLocation chooseMopperFollowTarget(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapLocation enemySide = chooseEnemySidePoint(rc);

        MapLocation best = null;
        int bestCategory = Integer.MAX_VALUE;
        int bestEnemySideDist = Integer.MAX_VALUE;
        int bestMeDist = Integer.MAX_VALUE;

        for (RobotInfo ri : rc.senseNearbyRobots()) {
            if (ri.getTeam() != rc.getTeam()) {
                continue;
            }
            if (ri.getLocation().equals(me)) {
                continue;
            }
            if (isTowerType(ri.getType())) {
                continue;
            }
            if (ri.getType() != UnitType.SPLASHER && ri.getType() != UnitType.SOLDIER) {
                continue;
            }

            int category = followCategory(rc, ri);
            int enemySideDist = ri.getLocation().distanceSquaredTo(enemySide);
            int meDist = me.distanceSquaredTo(ri.getLocation());

            if (best == null
                    || category < bestCategory
                    || (category == bestCategory && enemySideDist < bestEnemySideDist)
                    || (category == bestCategory && enemySideDist == bestEnemySideDist && meDist < bestMeDist)) {
                best = ri.getLocation();
                bestCategory = category;
                bestEnemySideDist = enemySideDist;
                bestMeDist = meDist;
            }
        }

        if (best != null) {
            mopperFollowTarget = best;
            mopperFollowTTL = MOPPER_FOLLOW_TTL_RESET;
            return best;
        }

        if (mopperFollowTarget != null && mopperFollowTTL > 0) {
            mopperFollowTTL--;

            if (me.distanceSquaredTo(mopperFollowTarget) > MOPPER_FOLLOW_KEEP_DIST_SQ) {
                return mopperFollowTarget;
            }
        }

        mopperFollowTarget = null;
        return null;
    }

    static boolean tryGivePaintToBestAlly(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) {
            return false;
        }
        if (rc.getPaint() <= MOPPER_NO_GIVE_BELOW) {
            return false;
        }

        RobotInfo best = null;
        int bestCategory = Integer.MAX_VALUE;
        int bestNeed = Integer.MIN_VALUE;
        int bestPaint = Integer.MAX_VALUE;

        for (RobotInfo ri : rc.senseNearbyRobots()) {
            if (ri.getTeam() != rc.getTeam()) {
                continue;
            }
            if (ri.getLocation().equals(rc.getLocation())) {
                continue;
            }
            if (rc.getLocation().distanceSquaredTo(ri.getLocation()) > MOPPER_TRANSFER_RANGE_SQ) {
                continue;
            }
            if (isTowerType(ri.getType())) {
                continue;
            }

            int target = supportTargetPaint(ri.getType());
            int need = target - ri.getPaintAmount();

            if (need <= MOPPER_NEED_MIN) {
                continue;
            }

            int category = givePaintCategory(rc, ri);
            int paint = ri.getPaintAmount();

            if (best == null
                    || category < bestCategory
                    || (category == bestCategory && need > bestNeed)
                    || (category == bestCategory && need == bestNeed && paint < bestPaint)) {
                best = ri;
                bestCategory = category;
                bestNeed = need;
                bestPaint = paint;
            }
        }

        if (best != null) {
            int give = Math.min(bestNeed, rc.getPaint() - MOPPER_MIN_KEEP_PAINT);
            if (give > 0) {
                if (rc.canTransferPaint(best.getLocation(), give)) {
                    rc.transferPaint(best.getLocation(), give);
                    return true;
                }
            }
        }

        return false;
    }

    static boolean tryMopBestEnemyPaint(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) {
            return false;
        }

        MapLocation me = rc.getLocation();
        MapLocation ruin = chooseBestVisibleOpenRuin(rc);

        MapLocation best = null;
        int bestCategory = Integer.MAX_VALUE;
        int bestDist = Integer.MAX_VALUE;

        for (MapInfo mi : rc.senseNearbyMapInfos()) {
            MapLocation loc = mi.getMapLocation();

            if (!rc.canAttack(loc)) {
                continue;
            }
            if (!mi.getPaint().isEnemy()) {
                continue;
            }

            int cluster = countEnemyPaintAround(rc, loc, 2);
            boolean nearRuin = false;
            if (ruin != null) {
                if (ruin.distanceSquaredTo(loc) <= 4) {
                    nearRuin = true;
                }
            }

            int category;
            if (nearRuin) {
                category = 0;
            } else if (cluster >= 3) {
                category = 1;
            } else if (cluster >= 1) {
                category = 2;
            } else {
                category = 3;
            }

            int dist = me.distanceSquaredTo(loc);

            if (best == null
                    || category < bestCategory
                    || (category == bestCategory && dist < bestDist)) {
                best = loc;
                bestCategory = category;
                bestDist = dist;
            }
        }

        if (best != null) {
            rc.attack(best);
            return true;
        }

        Direction swing = chooseBestSwingDirection(rc);
        if (swing != null) {
            if (rc.canMopSwing(swing)) {
                rc.mopSwing(swing);
                return true;
            }
        }

        return false;
    }

    static Direction chooseBestSwingDirection(RobotController rc) throws GameActionException {
        for (Direction dir : CARDINALS) {
            if (rc.canMopSwing(dir)) {
                if (countEnemiesInSwing(rc, dir) >= 2) {
                    return dir;
                }
            }
        }

        for (Direction dir : CARDINALS) {
            if (rc.canMopSwing(dir)) {
                if (countEnemiesInSwing(rc, dir) >= 1) {
                    return dir;
                }
            }
        }

        return null;
    }

    static int countEnemiesInSwing(RobotController rc, Direction dir) throws GameActionException {
        MapLocation me = rc.getLocation();

        int dx = 0;
        int dy = 0;
        int px = 0;
        int py = 0;

        if (dir == Direction.NORTH) {
            dx = 0;
            dy = 1;
            px = 1;
            py = 0;
        } else if (dir == Direction.SOUTH) {
            dx = 0;
            dy = -1;
            px = 1;
            py = 0;
        } else if (dir == Direction.EAST) {
            dx = 1;
            dy = 0;
            px = 0;
            py = 1;
        } else if (dir == Direction.WEST) {
            dx = -1;
            dy = 0;
            px = 0;
            py = 1;
        }

        int count = 0;

        for (int step = 1; step <= SWING_LENGTH; step++) {
            int cx = me.x + dx * step;
            int cy = me.y + dy * step;

            for (int off = -SWING_WIDTH_HALF; off <= SWING_WIDTH_HALF; off++) {
                MapLocation loc = new MapLocation(cx + px * off, cy + py * off);

                if (!rc.onTheMap(loc) || !rc.canSenseLocation(loc)) {
                    continue;
                }

                RobotInfo ri = rc.senseRobotAtLocation(loc);
                if (ri != null) {
                    if (ri.getTeam() != rc.getTeam() && !isTowerType(ri.getType())) {
                        count++;
                    }
                }
            }
        }

        return count;
    }

    static void runSplasher(RobotController rc) throws GameActionException {
        if (rc.getPaint() <= SPLASHER_LOW_PAINT) {
            genericRefillMode(rc, SPLASHER_REFILL_TARGET);
            return;
        }

        MapLocation enemyPaint = nearestVisibleEnemyPaint(rc);

        if (enemyPaint != null) {
            moveSplasherToEnemyPaint(rc, enemyPaint);
        } else {
            moveSplasherRandomExplore(rc);
        }

        if (rc.isActionReady()) {
            MapLocation splash = chooseBestSplasherTarget(rc);
            if (splash != null) {
                if (rc.canAttack(splash)) {
                    rc.attack(splash);
                }
            }
        }
    }

    static void moveSplasherRandomExplore(RobotController rc) throws GameActionException {
        refreshSplasherExploreDirection(rc);

        MapLocation target = rc.getLocation().translate(
                splasherExploreDir.dx * EXPLORE_TARGET_DISTANCE,
                splasherExploreDir.dy * EXPLORE_TARGET_DISTANCE
        );

        if (rc.isMovementReady()) {
            Direction step = chooseGreedyMoveDirection(rc, target, splasherExploreDir, true);

            if (step != null) {
                if (rc.canMove(step)) {
                    rc.move(step);
                    splasherExploreTTL--;
                }
            } else {
                resetSplasherExploreDirection();
            }
        }
    }

    static void refreshSplasherExploreDirection(RobotController rc) {
        if (splasherExploreDir == null || splasherExploreTTL <= 0) {
            resetSplasherExploreDirection();
            return;
        }

        MapLocation me = rc.getLocation();
        MapLocation ahead = me.translate(
                splasherExploreDir.dx * EXPLORE_EDGE_LOOKAHEAD,
                splasherExploreDir.dy * EXPLORE_EDGE_LOOKAHEAD
        );

        if (!rc.onTheMap(ahead)) {
            resetSplasherExploreDirection();
        }
    }

    static void resetSplasherExploreDirection() {
        splasherExploreDir = DIRECTIONS[rng.nextInt(DIRECTIONS.length)];
        splasherExploreTTL = EXPLORE_MIN_TTL + rng.nextInt(EXPLORE_MAX_TTL - EXPLORE_MIN_TTL + 1);
    }

    static void moveSplasherToEnemyPaint(RobotController rc, MapLocation target) throws GameActionException {
        if (!rc.isMovementReady() || target == null) {
            return;
        }

        MapLocation me = rc.getLocation();
        Direction best = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction d : DIRECTIONS) {
            if (!rc.canMove(d)) {
                continue;
            }

            MapLocation nxt = me.add(d);
            int score = 0;

            int progress = me.distanceSquaredTo(target) - nxt.distanceSquaredTo(target);
            score += 12 * progress;

            if (rc.canSenseLocation(nxt)) {
                PaintType p = rc.senseMapInfo(nxt).getPaint();
                if (p.isEnemy()) {
                    score += 80;
                } else if (p == PaintType.EMPTY) {
                    score += 15;
                } else if (p.isAlly()) {
                    score -= 10;
                }
            }

            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }

        if (best != null) {
            if (rc.canMove(best)) {
                rc.move(best);
            }
        }
    }

    static MapLocation chooseBestSplasherTarget(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapInfo[] infos = rc.senseNearbyMapInfos();

        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;
        int bestDist = Integer.MAX_VALUE;

        for (MapInfo mi : infos) {
            MapLocation center = mi.getMapLocation();

            if (!rc.canAttack(center)) {
                continue;
            }

            PaintType centerPaint = mi.getPaint();
            if (!centerPaint.isEnemy() && centerPaint != PaintType.EMPTY) {
                continue;
            }

            int score = 0;
            boolean hasEnemy = false;

            for (int i = 0; i < SPLASH_DX.length; i++) {
                MapLocation loc = new MapLocation(center.x + SPLASH_DX[i], center.y + SPLASH_DY[i]);

                if (!rc.onTheMap(loc) || !rc.canSenseLocation(loc)) {
                    continue;
                }

                PaintType p = rc.senseMapInfo(loc).getPaint();

                if (p.isEnemy()) {
                    hasEnemy = true;
                    score += 14;

                    int distSq = SPLASH_DX[i] * SPLASH_DX[i] + SPLASH_DY[i] * SPLASH_DY[i];
                    if (distSq <= SPLASHER_TARGET_CLOSE_DIST_SQ) {
                        score += 10;
                    }
                } else if (p.isAlly()) {
                    score -= 11;
                }
            }

            if (!hasEnemy) {
                continue;
            }

            int dist = me.distanceSquaredTo(center);

            if (best == null || score > bestScore || (score == bestScore && dist < bestDist)) {
                best = center;
                bestScore = score;
                bestDist = dist;
            }
        }

        return best;
    }

    static void moveGreedy(RobotController rc, MapLocation target, Direction preferredDir, boolean avoidEnemyPaint) throws GameActionException {
        if (!rc.isMovementReady()) {
            return;
        }

        Direction step = chooseGreedyMoveDirection(rc, target, preferredDir, avoidEnemyPaint);
        if (step != null) {
            if (rc.canMove(step)) {
                rc.move(step);
            }
        }
    }

    static Direction chooseGreedyMoveDirection(RobotController rc, MapLocation target, Direction preferredDir, boolean avoidEnemyPaint) throws GameActionException {
        MapLocation me = rc.getLocation();
        Direction best = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction d : DIRECTIONS) {
            if (!rc.canMove(d)) {
                continue;
            }

            MapLocation nxt = me.add(d);
            int score = 0;

            if (target != null) {
                int progress = me.distanceSquaredTo(target) - nxt.distanceSquaredTo(target);
                if (avoidEnemyPaint) {
                    score += MOVE_PROGRESS_SAFE_WEIGHT * progress;
                } else {
                    score += MOVE_PROGRESS_AGGRO_WEIGHT * progress;
                }
            }

            if (rc.canSenseLocation(nxt)) {
                PaintType p = rc.senseMapInfo(nxt).getPaint();

                if (avoidEnemyPaint) {
                    if (p.isAlly()) {
                        score += MOVE_SAFE_ALLY_SCORE;
                    } else if (p == PaintType.EMPTY) {
                        score += MOVE_SAFE_EMPTY_SCORE;
                    } else if (p.isEnemy()) {
                        score += MOVE_SAFE_ENEMY_SCORE;
                    }
                } else {
                    if (p.isAlly()) {
                        score += MOVE_AGGRO_ALLY_SCORE;
                    } else if (p == PaintType.EMPTY) {
                        score += MOVE_AGGRO_EMPTY_SCORE;
                    } else if (p.isEnemy()) {
                        score += MOVE_AGGRO_ENEMY_SCORE;
                    }
                }
            }

            if (preferredDir != null) {
                if (d == preferredDir) {
                    score += MOVE_PREFERRED_DIR_BONUS;
                }
            }

            score -= MOVE_ADJ_ALLY_PENALTY * countAdjacentAlliesAt(rc, nxt);

            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }

        return best;
    }

    static void refreshExploreDirection(RobotController rc) {
        if (exploreDir == null || exploreTTL <= 0) {
            exploreDir = DIRECTIONS[rng.nextInt(DIRECTIONS.length)];
            exploreTTL = EXPLORE_MIN_TTL + rng.nextInt(EXPLORE_MAX_TTL - EXPLORE_MIN_TTL + 1);
            return;
        }

        MapLocation me = rc.getLocation();
        MapLocation ahead = me.translate(
                exploreDir.dx * EXPLORE_EDGE_LOOKAHEAD,
                exploreDir.dy * EXPLORE_EDGE_LOOKAHEAD
        );

        if (!rc.onTheMap(ahead)) {
            resetExploreDirection();
        }
    }

    static void resetExploreDirection() {
        exploreDir = DIRECTIONS[rng.nextInt(DIRECTIONS.length)];
        exploreTTL = EXPLORE_MIN_TTL + rng.nextInt(EXPLORE_MAX_TTL - EXPLORE_MIN_TTL + 1);
    }

    static MapLocation chooseExploreTarget(RobotController rc) {
        refreshExploreDirection(rc);
        return rc.getLocation().translate(
                exploreDir.dx * EXPLORE_TARGET_DISTANCE,
                exploreDir.dy * EXPLORE_TARGET_DISTANCE
        );
    }

    static MapLocation chooseEnemySidePoint(RobotController rc) {
        MapLocation anchor;
        if (homeTower != null) {
            anchor = homeTower;
        } else {
            anchor = rc.getLocation();
        }

        int tx = rc.getMapWidth() - 1 - anchor.x;
        int ty = rc.getMapHeight() - 1 - anchor.y;

        tx = Math.max(ENEMY_SIDE_BORDER_MARGIN, Math.min(rc.getMapWidth() - 1 - ENEMY_SIDE_BORDER_MARGIN, tx));
        ty = Math.max(ENEMY_SIDE_BORDER_MARGIN, Math.min(rc.getMapHeight() - 1 - ENEMY_SIDE_BORDER_MARGIN, ty));

        return new MapLocation(tx, ty);
    }

    static void genericRefillMode(RobotController rc, int targetPaint) throws GameActionException {
        if (tryWithdrawPaint(rc, targetPaint)) {
            return;
        }

        MapLocation refill = nearestVisibleFriendlyTower(rc);
        if (refill == null) {
            refill = homeTower;
        }
        if (refill == null) {
            refill = chooseExploreTarget(rc);
        }

        moveGreedy(rc, refill, null, true);
        tryWithdrawPaint(rc, targetPaint);
    }

    static boolean tryWithdrawPaint(RobotController rc, int targetPaint) throws GameActionException {
        int need = targetPaint - rc.getPaint();
        if (need <= 0) {
            return false;
        }

        RobotInfo[] robots = rc.senseNearbyRobots();
        RobotInfo bestTower = null;
        int bestAvail = 0;

        for (RobotInfo ri : robots) {
            if (ri.getTeam() != rc.getTeam()) {
                continue;
            }
            if (!isTowerType(ri.getType())) {
                continue;
            }
            if (rc.getLocation().distanceSquaredTo(ri.getLocation()) > 2) {
                continue;
            }

            int avail = ri.getPaintAmount();
            if (avail > bestAvail) {
                bestAvail = avail;
                bestTower = ri;
            }
        }

        if (bestTower == null) {
            return false;
        }

        int amount = Math.min(need, bestAvail);
        if (amount <= 0) {
            return false;
        }

        if (rc.canTransferPaint(bestTower.getLocation(), -amount)) {
            rc.transferPaint(bestTower.getLocation(), -amount);
            return true;
        }

        return false;
    }

    static MapLocation chooseBestVisibleOpenRuin(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();

        MapLocation cleanRuin = null;
        int cleanDist = Integer.MAX_VALUE;

        MapLocation dirtyRuin = null;
        int dirtyEnemyCount = Integer.MAX_VALUE;
        int dirtyDist = Integer.MAX_VALUE;

        for (MapInfo mi : rc.senseNearbyMapInfos()) {
            if (!mi.hasRuin()) {
                continue;
            }

            MapLocation ruin = mi.getMapLocation();
            RobotInfo occupant = rc.senseRobotAtLocation(ruin);

            if (occupant != null) {
                if (isTowerType(occupant.getType())) {
                    continue;
                }
            }

            int enemyPaint = countEnemyPaintAround(rc, ruin, 2);
            int dist = me.distanceSquaredTo(ruin);

            if (enemyPaint == 0) {
                if (cleanRuin == null || dist < cleanDist) {
                    cleanRuin = ruin;
                    cleanDist = dist;
                }
            } else {
                if (dirtyRuin == null
                        || enemyPaint < dirtyEnemyCount
                        || (enemyPaint == dirtyEnemyCount && dist < dirtyDist)) {
                    dirtyRuin = ruin;
                    dirtyEnemyCount = enemyPaint;
                    dirtyDist = dist;
                }
            }
        }

        if (cleanRuin != null) {
            return cleanRuin;
        }
        return dirtyRuin;
    }

    static MapLocation chooseFrontlineTarget(RobotController rc) throws GameActionException {
        MapLocation enemyTower = nearestVisibleEnemyTower(rc);
        if (enemyTower != null) {
            return enemyTower;
        }

        MapLocation denseEnemyPaint = nearestEnemyPaintWithAtLeast(rc, 2);
        if (denseEnemyPaint != null) {
            return denseEnemyPaint;
        }

        MapLocation enemyUnit = nearestVisibleEnemyUnit(rc);
        if (enemyUnit != null) {
            return enemyUnit;
        }

        MapLocation enemyPaint = nearestVisibleEnemyPaint(rc);
        if (enemyPaint != null) {
            return enemyPaint;
        }

        MapLocation emptyNearEnemy = nearestEmptyNearEnemyPaint(rc);
        if (emptyNearEnemy != null) {
            return emptyNearEnemy;
        }

        MapLocation ruin = chooseBestVisibleOpenRuin(rc);
        if (ruin != null) {
            return ruin;
        }

        return new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
    }

    static MapLocation chooseBestSupportAllyTarget(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();

        MapLocation best = null;
        int bestCategory = Integer.MAX_VALUE;
        int bestNeed = Integer.MIN_VALUE;
        int bestDist = Integer.MAX_VALUE;

        for (RobotInfo ri : rc.senseNearbyRobots()) {
            if (ri.getTeam() != rc.getTeam()) {
                continue;
            }
            if (ri.getLocation().equals(me)) {
                continue;
            }
            if (isTowerType(ri.getType())) {
                continue;
            }

            int targetPaint = supportTargetPaint(ri.getType());
            int need = targetPaint - ri.getPaintAmount();

            if (need <= 0) {
                continue;
            }

            int category = supportTargetCategory(rc, ri);
            int dist = me.distanceSquaredTo(ri.getLocation());

            if (best == null
                    || category < bestCategory
                    || (category == bestCategory && need > bestNeed)
                    || (category == bestCategory && need == bestNeed && dist < bestDist)) {
                best = ri.getLocation();
                bestCategory = category;
                bestNeed = need;
                bestDist = dist;
            }
        }

        return best;
    }

    static int spawnCategory(PaintType p, int adj) {
        if (p.isAlly() && adj == 0) {
            return 0;
        }
        if (p == PaintType.EMPTY && adj == 0) {
            return 1;
        }
        if (p.isAlly()) {
            return 2;
        }
        if (p == PaintType.EMPTY) {
            return 3;
        }
        return 4;
    }

    static int supportTargetPaint(UnitType type) {
        if (type == UnitType.SOLDIER) {
            return SUPPORT_SOLDIER_TARGET_PAINT;
        }
        if (type == UnitType.SPLASHER) {
            return SUPPORT_SPLASHER_TARGET_PAINT;
        }
        return SUPPORT_MOPPER_TARGET_PAINT;
    }

    static int followCategory(RobotController rc, RobotInfo ri) throws GameActionException {
        MapLocation loc = ri.getLocation();
        PaintType p = PaintType.EMPTY;

        if (rc.canSenseLocation(loc)) {
            p = rc.senseMapInfo(loc).getPaint();
        }

        boolean onEnemy = p.isEnemy();
        boolean onEmpty = (p == PaintType.EMPTY);

        if (ri.getType() == UnitType.SPLASHER) {
            if (onEnemy) {
                return 0;
            }
            if (ri.getPaintAmount() < MOPPER_FOLLOW_SPLASHER_LOW_PAINT) {
                return 1;
            }
            if (onEmpty) {
                return 2;
            }
            return 3;
        }

        if (ri.getType() == UnitType.SOLDIER) {
            if (onEnemy) {
                return 4;
            }
            if (ri.getPaintAmount() < MOPPER_FOLLOW_SOLDIER_LOW_PAINT) {
                return 5;
            }
            if (onEmpty) {
                return 6;
            }
            return 7;
        }

        return 99;
    }

    static int givePaintCategory(RobotController rc, RobotInfo ri) throws GameActionException {
        boolean frontline = countEnemyPaintAround(rc, ri.getLocation(), 2) > 0;

        if (ri.getType() == UnitType.SOLDIER && frontline) {
            return 0;
        }
        if (ri.getType() == UnitType.SPLASHER && frontline) {
            return 1;
        }
        if (ri.getType() == UnitType.SOLDIER) {
            return 2;
        }
        if (ri.getType() == UnitType.SPLASHER) {
            return 3;
        }
        return 4;
    }

    static int supportTargetCategory(RobotController rc, RobotInfo ri) throws GameActionException {
        boolean frontline = countEnemyPaintAround(rc, ri.getLocation(), 2) > 0;

        if (ri.getType() == UnitType.SOLDIER && frontline) {
            return 0;
        }
        if (ri.getType() == UnitType.SPLASHER && frontline) {
            return 1;
        }
        if (ri.getType() == UnitType.SOLDIER) {
            return 2;
        }
        if (ri.getType() == UnitType.SPLASHER) {
            return 3;
        }
        return 4;
    }

    static MapLocation nearestVisibleEnemyTower(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (RobotInfo ri : rc.senseNearbyRobots()) {
            if (ri.getTeam() == rc.getTeam()) {
                continue;
            }
            if (!isTowerType(ri.getType())) {
                continue;
            }

            int dist = me.distanceSquaredTo(ri.getLocation());
            if (best == null || dist < bestDist) {
                best = ri.getLocation();
                bestDist = dist;
            }
        }

        return best;
    }

    static MapLocation nearestVisibleEnemyUnit(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (RobotInfo ri : rc.senseNearbyRobots()) {
            if (ri.getTeam() == rc.getTeam()) {
                continue;
            }
            if (isTowerType(ri.getType())) {
                continue;
            }

            int dist = me.distanceSquaredTo(ri.getLocation());
            if (best == null || dist < bestDist) {
                best = ri.getLocation();
                bestDist = dist;
            }
        }

        return best;
    }

    static MapLocation nearestEnemyPaintWithAtLeast(RobotController rc, int minAround) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (MapInfo mi : rc.senseNearbyMapInfos()) {
            if (!mi.getPaint().isEnemy()) {
                continue;
            }

            MapLocation loc = mi.getMapLocation();
            int around = countEnemyPaintAround(rc, loc, 2);

            if (around < minAround) {
                continue;
            }

            int dist = me.distanceSquaredTo(loc);
            if (best == null || dist < bestDist) {
                best = loc;
                bestDist = dist;
            }
        }

        return best;
    }

    static MapLocation nearestEmptyNearEnemyPaint(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (MapInfo mi : rc.senseNearbyMapInfos()) {
            if (mi.getPaint() != PaintType.EMPTY) {
                continue;
            }

            MapLocation loc = mi.getMapLocation();
            if (countEnemyPaintAround(rc, loc, 2) <= 0) {
                continue;
            }

            int dist = me.distanceSquaredTo(loc);
            if (best == null || dist < bestDist) {
                best = loc;
                bestDist = dist;
            }
        }

        return best;
    }

    static int countEnemyPaintAround(RobotController rc, MapLocation center, int rad) throws GameActionException {
        int cnt = 0;
        int r2 = rad * rad;

        for (MapInfo mi : rc.senseNearbyMapInfos()) {
            if (center.distanceSquaredTo(mi.getMapLocation()) <= r2) {
                if (mi.getPaint().isEnemy()) {
                    cnt++;
                }
            }
        }

        return cnt;
    }

    static boolean isTowerType(UnitType type) {
        return type != UnitType.SOLDIER
                && type != UnitType.MOPPER
                && type != UnitType.SPLASHER;
    }

    static void rememberNearestFriendlyTower(RobotController rc) throws GameActionException {
        RobotInfo[] robots = rc.senseNearbyRobots();
        MapLocation me = rc.getLocation();

        MapLocation best = homeTower;
        int bestDist;
        if (best == null) {
            bestDist = Integer.MAX_VALUE;
        } else {
            bestDist = me.distanceSquaredTo(best);
        }

        for (RobotInfo ri : robots) {
            if (ri.getTeam() == rc.getTeam() && isTowerType(ri.getType())) {
                int d = me.distanceSquaredTo(ri.getLocation());
                if (d < bestDist) {
                    bestDist = d;
                    best = ri.getLocation();
                }
            }
        }

        if (best != null) {
            homeTower = best;
        }
    }

    static MapLocation nearestVisibleFriendlyTower(RobotController rc) throws GameActionException {
        RobotInfo[] robots = rc.senseNearbyRobots();
        MapLocation me = rc.getLocation();

        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (RobotInfo ri : robots) {
            if (ri.getTeam() == rc.getTeam() && isTowerType(ri.getType())) {
                int d = me.distanceSquaredTo(ri.getLocation());
                if (d < bestDist) {
                    bestDist = d;
                    best = ri.getLocation();
                }
            }
        }

        return best;
    }

    static MapLocation nearestVisibleEnemyPaint(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (MapInfo mi : rc.senseNearbyMapInfos()) {
            if (mi.getPaint().isEnemy()) {
                int d = me.distanceSquaredTo(mi.getMapLocation());
                if (best == null || d < bestDist) {
                    bestDist = d;
                    best = mi.getMapLocation();
                }
            }
        }

        return best;
    }

    static int countAdjacentAlliesAt(RobotController rc, MapLocation loc) throws GameActionException {
        int cnt = 0;

        for (Direction d : DIRECTIONS) {
            MapLocation adj = loc.add(d);

            if (!rc.onTheMap(adj) || !rc.canSenseLocation(adj)) {
                continue;
            }

            RobotInfo ri = rc.senseRobotAtLocation(adj);
            if (ri != null && ri.getTeam() == rc.getTeam()) {
                cnt++;
            }
        }

        return cnt;
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static class PaintJob {
        MapLocation loc;
        boolean useSecondary;

        PaintJob(MapLocation loc, boolean useSecondary) {
            this.loc = loc;
            this.useSecondary = useSecondary;
        }
    }

    static class SpawnWeights {
        int soldierWeight;
        int splasherWeight;
        int mopperWeight;

        SpawnWeights(int soldierWeight, int splasherWeight, int mopperWeight) {
            this.soldierWeight = soldierWeight;
            this.splasherWeight = splasherWeight;
            this.mopperWeight = mopperWeight;
        }
    }
}