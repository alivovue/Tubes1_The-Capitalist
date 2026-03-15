package alternatif-bots-2;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class RobotPlayer {

    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            try {
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());

                switch (rc.getType()) {
                    case SOLDIER -> Soldier.run(rc, enemies, allies);
                    case SPLASHER -> Splasher.run(rc, enemies, allies);
                    case MOPPER -> Mopper.run(rc, enemies, allies);
                    default -> Tower.run(rc);
                }
            } catch (GameActionException | RuntimeException ignored) {
            } finally {
                Clock.yield();
            }
        }
    }
}
