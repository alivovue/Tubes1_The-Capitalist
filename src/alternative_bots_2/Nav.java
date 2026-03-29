package alternative_bots_2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class Nav {

    public static final Direction[] DIRECTIONS = {
            Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
            Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
    };

    public static final Direction[] CARDINAL_DIRECTIONS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private static MapLocation lastTarget = null;

    private static boolean tracing = false;
    private static Direction traceHeading = Direction.CENTER;
    private static Direction lastMove = Direction.CENTER;

    private static boolean followRightWall = true;

    public static Direction getMoveDirection(RobotController rc, MapLocation target) throws GameActionException {

        if (!rc.isMovementReady()) return null;
        if (target == null) return null;

        MapLocation me = rc.getLocation();

        if (me.equals(target)) return null;

        if (lastTarget == null || !lastTarget.equals(target)) {
            reset();
            lastTarget = target;

            followRightWall = (rc.getID() & 1) == 0;
        }

        Direction toTarget = me.directionTo(target);

        Direction left = toTarget.rotateLeft();
        Direction right = toTarget.rotateRight();

        if (!tracing) {

            Direction greedy = tryDirections(rc, toTarget, left, right);

            if (greedy != null) {
                traceHeading = greedy;
                return greedy;
            }

            tracing = true;
            if (toTarget == Direction.CENTER) {
                traceHeading = Direction.NORTH;
            } else {
                traceHeading = toTarget;
            }
        }

        Direction exit = tryTraceExit(rc, toTarget);

        if (exit != null) {

            tracing = false;
            traceHeading = exit;

            return exit;
        }

        Direction trace = traceMove(rc, target);

        if (trace != null) {
            traceHeading = trace;
        }

        return trace;
    }

    public static boolean move(RobotController rc, Direction d) throws GameActionException {

        if (d == null) return false;
        if (!rc.isMovementReady()) return false;
        if (!rc.canMove(d)) return false;

        rc.move(d);

        lastMove = d;

        return true;
    }

    public static boolean moveTowards(RobotController rc, MapLocation target) throws GameActionException {

        Direction d = getMoveDirection(rc, target);

        return move(rc, d);
    }

    private static Direction tryDirections(RobotController rc, Direction... dirs) {

        Direction fallback = null;

        for (Direction d : dirs) {

            if (!canStep(rc, d)) continue;

            if (fallback == null) fallback = d;

            if (!isReverse(d)) {
                return d;
            }
        }

        return fallback;
    }

    private static Direction tryTraceExit(RobotController rc, Direction targetDir) {

        Direction tracingDir;
        if (traceHeading == Direction.CENTER) {
            if (followRightWall) {
                tracingDir = targetDir.rotateRight();
            } else {
                tracingDir = targetDir.rotateLeft();
            }
        } else {
            tracingDir = traceHeading;
        }

        Direction mid = midpoint(targetDir, tracingDir);

        if (mid != Direction.CENTER && canStep(rc, mid)) {
            return mid;
        }

        return null;
    }

    private static Direction midpoint(Direction from, Direction to) {

        int cw = clockwiseSteps(from, to);
        int ccw = 8 - cw;

        int steps;
        boolean rotateRight;

        if (cw <= ccw) {

            steps = cw / 2;
            rotateRight = true;

        } else {

            steps = ccw / 2;
            rotateRight = false;
        }

        Direction d = from;

        for (int i = 0; i < steps; i++) {
            if (rotateRight) {
                d = d.rotateRight();
            } else {
                d = d.rotateLeft();
            }
        }

        return d;
    }

    private static Direction traceMove(RobotController rc, MapLocation target) throws GameActionException {

        MapLocation me = rc.getLocation();

        int curDist = me.distanceSquaredTo(target);

        Direction heading;
        if (traceHeading == Direction.CENTER) {
            heading = Direction.NORTH;
        } else {
            heading = traceHeading;
        }

        Direction d;
        if (followRightWall) {
            d = heading.rotateRight();
        } else {
            d = heading.rotateLeft();
        }

        Direction best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < 8; i++) {

            if (canStep(rc, d)) {

                MapLocation next = me.add(d);

                int score = 0;

                int nextDist = next.distanceSquaredTo(target);

                score += (curDist - nextDist) * 12;

                score -= i * 5;

                Direction sideDir;
                if (followRightWall) {
                    sideDir = d.rotateRight();
                } else {
                    sideDir = d.rotateLeft();
                }
                MapLocation side = next.add(sideDir);

                if (isWallLike(rc, side)) {
                    score += 10;
                }

                if (isReverse(d)) {
                    score -= 15;
                }

                if (score > bestScore) {

                    bestScore = score;
                    best = d;
                }
            }

            if (followRightWall) {
                d = d.rotateLeft();
            } else {
                d = d.rotateRight();
            }
        }

        return best;
    }

    private static boolean canStep(RobotController rc, Direction d) {

        return d != null && d != Direction.CENTER && rc.canMove(d);
    }

    private static boolean isWallLike(RobotController rc, MapLocation loc) throws GameActionException {

        if (!rc.canSenseLocation(loc)) return true;

        return rc.senseMapInfo(loc).isWall();
    }

    private static int clockwiseSteps(Direction from, Direction to) {

        Direction d = from;

        for (int i = 0; i < 8; i++) {

            if (d == to) return i;

            d = d.rotateRight();
        }

        return 0;
    }

    private static boolean isReverse(Direction d) {

        return lastMove != null
                && lastMove != Direction.CENTER
                && d == lastMove.opposite();
    }

    private static void reset() {

        tracing = false;

        traceHeading = Direction.CENTER;

        lastMove = Direction.CENTER;
    }
}
