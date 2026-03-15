package alternatif-bots-2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Tower {
    static final int PRESSURE_END_ROUND = 80;

    private static final int EARLY_END_ROUND = 300;
    private static final int LATE_START_ROUND = 1000;
    private static final int MSG_RUIN_WORKING = 1;
    private static final int MSG_RUIN_NEED_HELP = 2;
    private static final int MSG_RUIN_ENEMY_MARK = 3;
    private static final int MSG_ASSIST_RUIN = 4;
    private static final int HELP_ASSIGN_RADIUS_SQ = 20;
    private static final int HELP_LIFETIME = 25;
    private static final int DEFENSE_MOPPER_NEAR_RADIUS_SQ = 36;
    private static int buildCount = 0;

    private static MapLocation activeHelpRuin = null;
    private static int activeHelpMode = 0;
    private static int activeHelpUntilRound = -1;

    public static void run(RobotController rc) throws GameActionException {
        if (rc.getRoundNum() >= PRESSURE_END_ROUND) {
            readRuinMessages(rc);
            assignNearbySoldiersToRuin(rc);

            if (activeHelpRuin != null && rc.getRoundNum() > activeHelpUntilRound) {
                activeHelpRuin = null;
                activeHelpMode = 0;
            }
        }

        if (!rc.isActionReady()) return;

        UnitType spawnType = chooseSpawnType(rc);
        if (spawnType == null) return;
        if (!canAfford(rc, spawnType)) return;

        if (trySpawn(rc, spawnType)) {
            buildCount++;
        }
    }

    private static UnitType chooseSpawnType(RobotController rc) throws GameActionException {
        if (isTowerUnderAttack(rc) && !hasNearbyAllyMopper(rc)) {
            return UnitType.MOPPER;
        }

        if (rc.getRoundNum() < PRESSURE_END_ROUND) {
            return UnitType.SOLDIER;
        }

        if (rc.getPaint() < 100 || rc.getChips() < 250) return null;

        Phase phase = getPhase(rc);

        if (rc.getRoundNum() % 3 == 0) {
            UnitType minType = chooseMinimumNeededType(rc, phase);
            if (minType != null) return minType;
        }

        int currentBuildNumber = buildCount + 1;
        if (currentBuildNumber == 3) return UnitType.MOPPER;

        if (activeHelpMode == MSG_RUIN_ENEMY_MARK) return UnitType.SPLASHER;

        if (phase == Phase.EARLY) {
            if (currentBuildNumber % 4 == 0) return UnitType.SPLASHER;
            return UnitType.SOLDIER;
        }
        if (phase == Phase.MID) {
            if (currentBuildNumber % 3 == 0) return UnitType.SPLASHER;
            return UnitType.SOLDIER;
        }
        if (currentBuildNumber % 2 == 0) return UnitType.SPLASHER;
        return UnitType.SOLDIER;
    }

    private static UnitType chooseMinimumNeededType(RobotController rc, Phase phase) throws GameActionException {
        int soldiers = 0;
        int splashers = 0;

        for (RobotInfo ally : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (ally.getType() == UnitType.SOLDIER) soldiers++;
            else if (ally.getType() == UnitType.SPLASHER) splashers++;
        }

        int minSoldier;
        int minSplasher;
        boolean splasherPriority;

        switch (phase) {
            case EARLY -> { minSoldier = 7;  minSplasher = 3; splasherPriority = false; }
            case MID   -> { minSoldier = 10; minSplasher = 5; splasherPriority = true; }
            default    -> { minSoldier = 14; minSplasher = 8; splasherPriority = true; }
        }

        boolean needSoldier  = soldiers < minSoldier;
        boolean needSplasher = splashers < minSplasher;
        if (!needSoldier && !needSplasher) return null;

        if (splasherPriority) {
            if (needSplasher) return UnitType.SPLASHER;
            return UnitType.SOLDIER;
        }
        if (needSoldier) return UnitType.SOLDIER;
        return UnitType.SPLASHER;
    }

    private static boolean canAfford(RobotController rc, UnitType type) {
        return rc.getChips() >= type.moneyCost && rc.getPaint() >= type.paintCost;
    }

    private static boolean hasNearbyAllyMopper(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        for (RobotInfo ally : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (ally.getType() != UnitType.MOPPER) continue;
            if (ally.getLocation().distanceSquaredTo(me) <= DEFENSE_MOPPER_NEAR_RADIUS_SQ) return true;
        }
        return false;
    }

    private static boolean isTowerUnderAttack(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        for (RobotInfo enemy : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
            // enemy bisa menyerang lokasi tower sekarang
            if (enemy.getLocation().distanceSquaredTo(me) <= enemy.getType().actionRadiusSquared) {
                return true;
            }
        }
        return false;
    }

    private static boolean trySpawn(RobotController rc, UnitType type) throws GameActionException {
        for (Direction d : Nav.DIRECTIONS) {
            MapLocation spawn = rc.getLocation().add(d);
            if (!rc.canBuildRobot(type, spawn)) continue;
            rc.buildRobot(type, spawn);
            return true;
        }
        return false;
    }

    private static Phase getPhase(RobotController rc) {
        if (rc.getRoundNum() < EARLY_END_ROUND) return Phase.EARLY;
        if (rc.getRoundNum() < LATE_START_ROUND) return Phase.MID;
        return Phase.LATE;
    }

    private static void readRuinMessages(RobotController rc) throws GameActionException {
        for (Message m : rc.readMessages(-1)) {
            int raw = m.getBytes();
            int tag = decodeTag(raw);
            if (tag != MSG_RUIN_WORKING && tag != MSG_RUIN_NEED_HELP && tag != MSG_RUIN_ENEMY_MARK) {
                continue;
            }
            activeHelpRuin = decodeLoc(raw);
            activeHelpMode = tag;
            activeHelpUntilRound = rc.getRoundNum() + HELP_LIFETIME;
        }
    }

    private static void assignNearbySoldiersToRuin(RobotController rc) throws GameActionException {
        if (activeHelpRuin == null) return;
        if (activeHelpMode != MSG_RUIN_NEED_HELP && activeHelpMode != MSG_RUIN_ENEMY_MARK) return;

        int msg = encode(MSG_ASSIST_RUIN, activeHelpRuin);
        for (RobotInfo ally : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (ally.getType() != UnitType.SOLDIER) continue;
            if (ally.getLocation().distanceSquaredTo(activeHelpRuin) > HELP_ASSIGN_RADIUS_SQ) continue;
            if (!rc.canSendMessage(ally.getLocation(), msg)) continue;
            rc.sendMessage(ally.getLocation(), msg);
        }
    }

    private static int encode(int tag, MapLocation loc) {
        return (tag << 20) | ((loc.x & 1023) << 10) | (loc.y & 1023);
    }

    private static int decodeTag(int raw) { return (raw >>> 20) & 1023; }

    private static MapLocation decodeLoc(int raw) {
        return new MapLocation((raw >>> 10) & 1023, raw & 1023);
    }

    private enum Phase { EARLY, MID, LATE }
}
