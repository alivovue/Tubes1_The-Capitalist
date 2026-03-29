package alternative_bots_1.bot;

import static alternative_bots_1.bot.Cache.*;
import static alternative_bots_1.includes.GameConfig.*;
import static alternative_bots_1.includes.ScoreConfig.*;

import alternative_bots_1.includes.TowerPlans;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Tower {
    static int TOWER_INIT_SOLDIER_BUILT = 0;

    public static void handleTower(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        cacheMap(rc, nearbyTiles, nearbyAllies, nearbyEnemies);

        int currentIndex = rc.getLocation().x * CACHE_MAP_H + rc.getLocation().y;

        // initialization for tower
        if (rc.getRoundNum() < STAGE_EARLY_ROUND && TOWER_INIT_SOLDIER_BUILT < TOWER_INIT_SOLDIER) {
            for (Direction dir : directions) {
                MapLocation mapLocation = rc.getLocation().add(dir);
                if (rc.canBuildRobot(UnitType.SOLDIER, mapLocation)) {
                    rc.buildRobot(UnitType.SOLDIER, mapLocation);
                    TOWER_INIT_SOLDIER_BUILT++;
                    towerSpawnedSoldiers[currentIndex]++;
                    return;
                }
            }
        }

        TowerPlans bestPlan = calculateBestPlanTower(rc, nearbyTiles, nearbyAllies, nearbyEnemies);
        executePlanTower(rc, bestPlan);
    }

    public static TowerPlans calculateBestPlanTower(RobotController rc, MapInfo[] nearbyTiles, RobotInfo[] nearbyAllies, RobotInfo[] nearbyEnemies) throws GameActionException {
        MapLocation currentLocation = rc.getLocation();
        int currentIndex = currentLocation.x * CACHE_MAP_H + currentLocation.y;
        int round = rc.getRoundNum();
        boolean isEarly = round < STAGE_EARLY_ROUND;
        boolean isMid = round < STAGE_MID_ROUND;

        // objective only create bots according to ratio
        // placement depends on availability and tile color

        int soldierRatio;
        int splasherRatio;
        int mopperRatio;
        int spawnRatioMultiplier;

        if (isEarly) {
            soldierRatio = TOWER_SCORE_SOLDIER_SPAWN_RATIO_EARLY;
            splasherRatio = TOWER_SCORE_SPLASHER_SPAWN_RATIO_EARLY;
            mopperRatio = TOWER_SCORE_MOPPER_SPAWN_RATIO_EARLY;
            spawnRatioMultiplier = TOWER_SCORE_SPAWN_RATIO_MULTIPLIER_EARLY;
        }
        else if (isMid) {
            soldierRatio = TOWER_SCORE_SOLDIER_SPAWN_RATIO_MID;
            splasherRatio = TOWER_SCORE_SPLASHER_SPAWN_RATIO_MID;
            mopperRatio = TOWER_SCORE_MOPPER_SPAWN_RATIO_MID;
            spawnRatioMultiplier = TOWER_SCORE_SPAWN_RATIO_MULTIPLIER_MID;
        }
        else {
            soldierRatio = TOWER_SCORE_SOLDIER_SPAWN_RATIO_LATE;
            splasherRatio = TOWER_SCORE_SPLASHER_SPAWN_RATIO_LATE;
            mopperRatio = TOWER_SCORE_MOPPER_SPAWN_RATIO_LATE;
            spawnRatioMultiplier = TOWER_SCORE_SPAWN_RATIO_MULTIPLIER_LATE;
        }

        int ratioSum = soldierRatio + splasherRatio + mopperRatio;

        int spawnedSoldiers = towerSpawnedSoldiers[currentIndex];
        int spawnedMoppers = towerSpawnedMoppers[currentIndex];
        int spawnedSplashers = towerSpawnedSplashers[currentIndex];
        int totalSpawned = spawnedSoldiers + spawnedMoppers + spawnedSplashers;

        TowerPlans bestPlan = new TowerPlans(ACTION_NONE, null, null, Integer.MIN_VALUE);

        int soldierSpawnScoreBase = (soldierRatio * (totalSpawned + 1) - spawnedSoldiers * ratioSum) * spawnRatioMultiplier;
        int splasherSpawnScoreBase = (splasherRatio * (totalSpawned + 1) - spawnedSplashers * ratioSum) * spawnRatioMultiplier;
        int mopperSpawnScoreBase = (mopperRatio * (totalSpawned + 1) - spawnedMoppers * ratioSum) * spawnRatioMultiplier;

        UnitType desiredSpawnType;
        int desiredActionType;
        int desiredSpawnScoreBase;

        if (soldierSpawnScoreBase >= splasherSpawnScoreBase && soldierSpawnScoreBase >= mopperSpawnScoreBase) {
            desiredSpawnType = UnitType.SOLDIER;
            desiredActionType = ACTION_TOWER_BUILD_SOLDIER;
            desiredSpawnScoreBase = soldierSpawnScoreBase;
        }
        else if (splasherSpawnScoreBase >= mopperSpawnScoreBase) {
            desiredSpawnType = UnitType.SPLASHER;
            desiredActionType = ACTION_TOWER_BUILD_SPLASHER;
            desiredSpawnScoreBase = splasherSpawnScoreBase;
        }
        else {
            desiredSpawnType = UnitType.MOPPER;
            desiredActionType = ACTION_TOWER_BUILD_MOPPER;
            desiredSpawnScoreBase = mopperSpawnScoreBase;
        }

        MapLocation bestSpawnLocation = null;
        int bestSpawnLocationScore = Integer.MIN_VALUE;

        for (Direction dir : directions) {
            MapLocation spawnLocation = currentLocation.add(dir);
            if (!rc.canBuildRobot(desiredSpawnType, spawnLocation)) {
                continue;
            }

            int locationScore = 0;

            if (rc.canSenseLocation(spawnLocation)) {
                PaintType paint = rc.senseMapInfo(spawnLocation).getPaint();
                if (paint.isAlly()) {
                    if (isEarly) {
                        locationScore += TOWER_SCORE_SPAWN_LOCATION_ALLY_TILE_EARLY;
                    }
                    else if (isMid) {
                        locationScore += TOWER_SCORE_SPAWN_LOCATION_ALLY_TILE_MID;
                    }
                    else {
                        locationScore += TOWER_SCORE_SPAWN_LOCATION_ALLY_TILE_LATE;
                    }
                }
                else if (paint == PaintType.EMPTY) {
                    if (isEarly) {
                        locationScore += TOWER_SCORE_SPAWN_LOCATION_EMPTY_TILE_EARLY;
                    }
                    else if (isMid) {
                        locationScore += TOWER_SCORE_SPAWN_LOCATION_EMPTY_TILE_MID;
                    }
                    else {
                        locationScore += TOWER_SCORE_SPAWN_LOCATION_EMPTY_TILE_LATE;
                    }
                }
                else {
                    if (isEarly) {
                        locationScore -= TOWER_SCORE_SPAWN_LOCATION_ENEMY_TILE_EARLY;
                    }
                    else if (isMid) {
                        locationScore -= TOWER_SCORE_SPAWN_LOCATION_ENEMY_TILE_MID;
                    }
                    else {
                        locationScore -= TOWER_SCORE_SPAWN_LOCATION_ENEMY_TILE_LATE;
                    }
                }
            }

            if (locationScore > bestSpawnLocationScore) {
                bestSpawnLocationScore = locationScore;
                bestSpawnLocation = spawnLocation;
            }
        }

        if (bestSpawnLocation != null) {
            int totalScore = desiredSpawnScoreBase + bestSpawnLocationScore;
            bestPlan = new TowerPlans(desiredActionType, desiredSpawnType, bestSpawnLocation, totalScore);
        }

        return bestPlan;
    }

    public static void executePlanTower(RobotController rc, TowerPlans towerPlan) throws GameActionException {
        if (towerPlan == null) {
            return;
        }
        if (towerPlan.actionType == ACTION_NONE) {
            return;
        }
        if (towerPlan.spawnLocation == null) {
            return;
        }

        MapLocation currentLocation = rc.getLocation();
        int currentIndex = currentLocation.x * CACHE_MAP_H + currentLocation.y;

        if (towerPlan.actionType == ACTION_TOWER_BUILD_SOLDIER) {
            if (rc.canBuildRobot(UnitType.SOLDIER, towerPlan.spawnLocation)) {
                System.out.println("WOULD BUILD A SOLDIER AT " + towerPlan.spawnLocation);
                rc.buildRobot(UnitType.SOLDIER, towerPlan.spawnLocation);
                towerSpawnedSoldiers[currentIndex]++;
            }
            return;
        }

        if (towerPlan.actionType == ACTION_TOWER_BUILD_MOPPER) {
            if (rc.canBuildRobot(UnitType.MOPPER, towerPlan.spawnLocation)) {
                System.out.println("WOULD BUILD A MOPPER AT " + towerPlan.spawnLocation);
                rc.buildRobot(UnitType.MOPPER, towerPlan.spawnLocation);
                towerSpawnedMoppers[currentIndex]++;
            }
            return;
        }

        if (towerPlan.actionType == ACTION_TOWER_BUILD_SPLASHER) {
            if (rc.canBuildRobot(UnitType.SPLASHER, towerPlan.spawnLocation)) {
                System.out.println("WOULD BUILD A SPLASHER AT " + towerPlan.spawnLocation);
                rc.buildRobot(UnitType.SPLASHER, towerPlan.spawnLocation);
                towerSpawnedSplashers[currentIndex]++;
            }
            return;
        }
    }
}