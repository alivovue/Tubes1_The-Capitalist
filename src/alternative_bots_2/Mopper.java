package alternative_bots_2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Mopper {

    // Jaga area 6 tile dari tower spawn (distance squared = 36)
    private static final int GUARD_RADIUS_SQ = 36;

    static MapLocation homeTower = null;

    public static void run(RobotController rc, RobotInfo[] enemies, RobotInfo[] allies) throws GameActionException {
        if (homeTower == null) homeTower = nearestKnownAllyTower(rc, allies);

        // Mopper aktif cari musuh di area jaga tower (radius 6 tile).
        RobotInfo enemyNearTower = bestEnemyRobotNearTower(rc, enemies);
        MapLocation enemyPaint = bestEnemyPaintNearTower(rc);
        MapLocation objective;
        if (enemyNearTower != null) {
            objective = enemyNearTower.getLocation();
        } else if (enemyPaint != null) {
            objective = enemyPaint;
        } else {
            objective = chooseGuardPatrolTarget(rc);
        }

        if (enemyNearTower != null && rc.isActionReady() && rc.canAttack(enemyNearTower.getLocation())) {
            rc.attack(enemyNearTower.getLocation());
        } else if (enemyPaint != null && rc.isActionReady() && rc.canAttack(enemyPaint)) {
            rc.attack(enemyPaint);
        }

        Direction dir = Nav.getMoveDirection(rc, objective);
        Nav.move(rc, dir);

        // Post-move clean-up/attack
        if (rc.isActionReady() && enemyNearTower != null && rc.canAttack(enemyNearTower.getLocation())) {
            rc.attack(enemyNearTower.getLocation());
        } else if (rc.isActionReady() && enemyPaint != null && rc.canAttack(enemyPaint)) {
            rc.attack(enemyPaint);
        }

        int enemyFlag = 0;
        if (enemyNearTower != null) enemyFlag = 1;
        rc.setIndicatorString("mopper guard home=" + formatLoc(homeTower)
                + " obj=" + formatLoc(objective)
                + " enemy=" + enemyFlag);
    }

    private static RobotInfo bestEnemyRobotNearTower(RobotController rc, RobotInfo[] enemies) {
        if (homeTower == null) return null;

        RobotInfo best = null;
        int bestDistToTower = Integer.MAX_VALUE;
        int bestDistToMe = Integer.MAX_VALUE;
        MapLocation me = rc.getLocation();

        for (RobotInfo enemy : enemies) {
            MapLocation loc = enemy.getLocation();
            int dTower = loc.distanceSquaredTo(homeTower);
            if (dTower > GUARD_RADIUS_SQ) continue;

            int dMe = me.distanceSquaredTo(loc);
            if (best == null
                    || dTower < bestDistToTower
                    || (dTower == bestDistToTower && dMe < bestDistToMe)) {
                best = enemy;
                bestDistToTower = dTower;
                bestDistToMe = dMe;
            }
        }

        return best;
    }

    private static MapLocation bestEnemyPaintNearTower(RobotController rc) throws GameActionException {
        if (homeTower == null) return null;
        MapLocation me  = rc.getLocation();
        MapLocation best = null;
        int bestScore    = Integer.MIN_VALUE;
        for (MapInfo info : rc.senseNearbyMapInfos()) {
            if (info.isWall() || info.hasRuin()) continue;
            if (!info.getPaint().isEnemy()) continue;
            MapLocation loc = info.getMapLocation();
            if (!rc.canAttack(loc)) continue;
            if (loc.distanceSquaredTo(homeTower) > GUARD_RADIUS_SQ) continue;
            int score = 70 - me.distanceSquaredTo(loc) * 2 - loc.distanceSquaredTo(homeTower);
            if (score > bestScore) { bestScore = score; best = loc; }
        }
        return best;
    }

    private static MapLocation chooseGuardPatrolTarget(RobotController rc) {
        if (homeTower == null) return rc.getLocation();
        MapLocation me = rc.getLocation();
        if (me.distanceSquaredTo(homeTower) > GUARD_RADIUS_SQ) return homeTower;
        Direction patrolDir = Nav.DIRECTIONS[Math.floorMod(rc.getID(), Nav.DIRECTIONS.length)];
        MapLocation patrol = homeTower.add(patrolDir);
        if (patrol.distanceSquaredTo(homeTower) <= GUARD_RADIUS_SQ) return patrol;
        return homeTower;
    }

    private static MapLocation nearestKnownAllyTower(RobotController rc, RobotInfo[] allies) {
        MapLocation best = null; int bestDist = Integer.MAX_VALUE;
        for (RobotInfo ally : allies) {
            if (!ally.getType().isTowerType()) continue;
            int d = rc.getLocation().distanceSquaredTo(ally.getLocation());
            if (d < bestDist) { bestDist = d; best = ally.getLocation(); }
        }
        return best;
    }

    private static String formatLoc(MapLocation loc) {
        if (loc == null) return "null";
        return "(" + loc.x + "," + loc.y + ")";
    }
}
