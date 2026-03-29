package alternative_bots_2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Splasher {
	private static final int SPREAD_ROUNDS = 8;
	private static final int REFILL_PERCENT = 10;

	static MapLocation spawnTower = null;
	static MapLocation enemyDiagonalTarget = null;
	static Direction spreadDir = Direction.CENTER;
	static int spawnRound = -1;

	public static void run(RobotController rc, RobotInfo[] enemies, RobotInfo[] allies) throws GameActionException {
		initIdentity(rc, allies);
		MapLocation refillTower = nearestKnownAllyTower(rc, allies);
		boolean refill = shouldRefill(rc);

		MapLocation objective;
		if (refill) {
			objective = refillTower;
		} else {
			objective = chooseObjective(rc);
		}
		Direction moveDir = Nav.getMoveDirection(rc, objective);

		if (!refill && rc.isActionReady()) {
			MapLocation preMovePaint = choosePreMovePaintTarget(rc, moveDir);
			if (preMovePaint != null && rc.canAttack(preMovePaint)) {
				rc.attack(preMovePaint);
			}
		}

		Nav.move(rc, moveDir);

		if (!refill && rc.isActionReady()) {
			MapLocation postMovePaint = chooseAnyEnemyOrNeutralToPaint(rc);
			if (postMovePaint != null && rc.canAttack(postMovePaint)) {
				rc.attack(postMovePaint);
			}
		}

		int refillFlag = 0;
		if (refill) refillFlag = 1;
		rc.setIndicatorString("splasher refill=" + refillFlag + " obj=" + formatLoc(objective) + " dir=" + formatDir(moveDir));
	}

	private static boolean shouldRefill(RobotController rc) {
		int cap = Math.max(1, rc.getType().paintCapacity);
		int percent = (rc.getPaint() * 100) / cap;
		return percent < REFILL_PERCENT;
	}

	private static void initIdentity(RobotController rc, RobotInfo[] allies) {
		if (spawnRound < 0) spawnRound = rc.getRoundNum();

		if (spawnTower == null) {
			for (RobotInfo ally : allies) {
				if (ally.getType().isTowerType()) { spawnTower = ally.getLocation(); break; }
			}
		}

		if (spreadDir == Direction.CENTER)
			spreadDir = Nav.DIRECTIONS[Math.floorMod(rc.getID(), Nav.DIRECTIONS.length)];

		if (enemyDiagonalTarget == null && spawnTower != null) {
			enemyDiagonalTarget = new MapLocation(
				rc.getMapWidth() - 1 - spawnTower.x,
				rc.getMapHeight() - 1 - spawnTower.y
			);
		}
	}

	private static MapLocation chooseObjective(RobotController rc) {
		MapLocation me = rc.getLocation();
		if (rc.getRoundNum() - spawnRound < SPREAD_ROUNDS) return me.add(spreadDir);
		if (enemyDiagonalTarget != null) return enemyDiagonalTarget;
		return me.add(spreadDir);
	}

	private static MapLocation choosePreMovePaintTarget(RobotController rc, Direction moveDir) throws GameActionException {
		MapLocation me = rc.getLocation();
		if (moveDir != null) {
			MapLocation ahead = me.add(moveDir);
			if (rc.canSenseLocation(ahead) && rc.canAttack(ahead)) {
				PaintType p = rc.senseMapInfo(ahead).getPaint();
				if (p == PaintType.EMPTY || p.isEnemy()) return ahead;
			}
		}
		return chooseAnyEnemyOrNeutralToPaint(rc);
	}

	private static MapLocation chooseAnyEnemyOrNeutralToPaint(RobotController rc) throws GameActionException {
		MapLocation me  = rc.getLocation();
		MapLocation best = null;
		int bestScore    = Integer.MIN_VALUE;
		for (MapInfo info : rc.senseNearbyMapInfos()) {
			if (info.isWall() || info.hasRuin()) continue;
			MapLocation loc = info.getMapLocation();
			if (!rc.canAttack(loc)) continue;
			PaintType p = info.getPaint();
			if (!(p == PaintType.EMPTY || p.isEnemy())) continue;
			int base;
			if (p.isEnemy()) {
				base = 20;
			} else {
				base = 14;
			}
			int score = base - me.distanceSquaredTo(loc);
			if (score > bestScore) { bestScore = score; best = loc; }
		}
		return best;
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

	private static String formatLoc(MapLocation loc) {
		if (loc == null) return "null";
		return "(" + loc.x + "," + loc.y + ")";
	}
	private static String formatDir(Direction d) {
		if (d == null || d == Direction.CENTER) return "C";
		return d.name();
	}
}
