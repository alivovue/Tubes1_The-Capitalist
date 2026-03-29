package alternative_bots_1;

import static alternative_bots_1.bot.Cache.*;
import static alternative_bots_1.bot.Mopper.*;
import static alternative_bots_1.bot.Soldier.*;
import static alternative_bots_1.bot.Splasher.*;
import static alternative_bots_1.bot.Tower.*;
import static alternative_bots_1.includes.GameConfig.*;
import static alternative_bots_1.includes.ScoreConfig.*;

import alternative_bots_1.includes.BotPlans;
import alternative_bots_1.includes.TowerPlans;
import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class RobotPlayer {

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        System.out.println("Main bot ready");
        while (true) {
            try {
                switch(rc.getType()) {
                    case SOLDIER: handleSoldier(rc); break;
                    case MOPPER: handleMopper(rc); break;
                    case SPLASHER: handleSplasher(rc); break;
                    default : handleTower(rc); break;
                }
            }
            catch (GameActionException e) {
                e.printStackTrace();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            finally {
                Clock.yield();
            }
        }
    }
}