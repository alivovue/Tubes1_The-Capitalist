package alternative-bots-1.bot;

import static alternative-bots-1.bot.Cache.*;
import static alternative-bots-1.includes.GameConfig.*;
import static alternative-bots-1.includes.ScoreConfig.*;

import alternative-bots-1.includes.BotPlans;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Splasher {
    static Direction SPLASHER_DIRECTION = null;

    public static void handleSplasher(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        cacheMap(rc, nearbyTiles, nearbyAllies, nearbyEnemies);

        BotPlans bestPlan = calculateBestPlanSplasher(rc, nearbyTiles, nearbyAllies, nearbyEnemies);

        executePlanSplasher(rc, bestPlan);
    }

    static void initializeSplasher(int currentX, int currentY, MapLocation currentLocation) {
        int nearestTowerX = -1;
        int nearestTowerY = -1;
        int bestDist = Integer.MAX_VALUE;

        for (int i = 0 ; i < knownAllyTowerCount ; i++) {
            int idx = knownAllyTowers[i];
            if ((tileFlags[idx] & TILE_ALLY_TOWER) == 0) {
                continue;
            }

            int x = idx / CACHE_MAP_H;
            int y = idx % CACHE_MAP_H;
            int dx = currentX - x;
            int dy = currentY - y;
            int dist = dx * dx + dy * dy;

            if (dist < bestDist) {
                bestDist = dist;
                nearestTowerX = x;
                nearestTowerY = y;
            }
        }

        if (nearestTowerX != -1) {
            SPLASHER_DIRECTION = new MapLocation(nearestTowerX, nearestTowerY).directionTo(currentLocation);
        }
        else {
            SPLASHER_DIRECTION = Direction.NORTH;
        }

        if (SPLASHER_DIRECTION == Direction.CENTER) {
            SPLASHER_DIRECTION = Direction.NORTH;
        }
    }

    public static BotPlans calculateBestPlanSplasher(RobotController rc, MapInfo[] nearbyTiles, RobotInfo[] nearbyAllies, RobotInfo[] nearbyEnemies) throws GameActionException {
        MapLocation currentLocation = rc.getLocation();
        int currentX = currentLocation.x;
        int currentY = currentLocation.y;
        int round = rc.getRoundNum();
        boolean isEarly = round < STAGE_EARLY_ROUND;
        boolean isMid = round < STAGE_MID_ROUND;

        // no data yet, initialize 
        if (SPLASHER_DIRECTION == null) {
            initializeSplasher(currentX, currentY, currentLocation);
        }

        // same as soldier 
        if (rc.getPaint() <= SPLASHER_PAINT_RETREAT_THRESHOLD) {
            int nearestAnyTowerX = -1;
            int nearestAnyTowerY = -1;
            int nearestAnyTowerDist = Integer.MAX_VALUE;
            boolean nearestAnyTowerSensed = false;
            RobotInfo nearestAnyTowerInfo = null;

            for (int i = 0 ; i < knownAllyTowerCount ; i++) {
                int idx = knownAllyTowers[i];
                if ((tileFlags[idx] & TILE_ALLY_TOWER) == 0) {
                    continue;
                }

                int x = idx / CACHE_MAP_H;
                int y = idx % CACHE_MAP_H;
                int dx = currentX - x;
                int dy = currentY - y;
                int dist = dx * dx + dy * dy;

                if (dist < nearestAnyTowerDist) {
                    nearestAnyTowerDist = dist;
                    nearestAnyTowerX = x;
                    nearestAnyTowerY = y;

                    MapLocation towerLoc = new MapLocation(x, y);
                    if (rc.canSenseLocation(towerLoc)) {
                        nearestAnyTowerSensed = true;
                        nearestAnyTowerInfo = rc.senseRobotAtLocation(towerLoc);
                    }
                    else {
                        nearestAnyTowerSensed = false;
                        nearestAnyTowerInfo = null;
                    }
                }
            }

            MapLocation retreatTarget = null;

            // nearest tower part
            if (nearestAnyTowerX != -1) {
                boolean nearestTowerUsable = true;

                if (nearestAnyTowerSensed) {
                    if (nearestAnyTowerInfo == null || !nearestAnyTowerInfo.getType().isTowerType() || nearestAnyTowerInfo.getTeam() != rc.getTeam()) {
                        nearestTowerUsable = false;
                    }
                    else {
                        UnitType sensedTowerType = nearestAnyTowerInfo.getType();
                        int paintAmount = nearestAnyTowerInfo.getPaintAmount();

                        if (sensedTowerType == UnitType.LEVEL_ONE_PAINT_TOWER || sensedTowerType == UnitType.LEVEL_TWO_PAINT_TOWER || sensedTowerType == UnitType.LEVEL_THREE_PAINT_TOWER) {
                            if (paintAmount <= 300) {
                                nearestTowerUsable = false;
                            }
                        }
                        else {
                            if (paintAmount <= 0) {
                                nearestTowerUsable = false;
                            }
                        }
                    }
                }

                if (nearestTowerUsable) {
                    retreatTarget = new MapLocation(nearestAnyTowerX, nearestAnyTowerY);
                }
            }

            // nearest paint tower part
            if (retreatTarget == null) {
                int bestPaintTowerDist = Integer.MAX_VALUE;
                int bestPaintTowerX = -1;
                int bestPaintTowerY = -1;

                for (int i = 0 ; i < knownAllyTowerCount ; i++) {
                    int idx = knownAllyTowers[i];
                    if ((tileFlags[idx] & TILE_ALLY_TOWER) == 0) {
                        continue;
                    }

                    UnitType towerType = knownAllyTowerTypes[i];
                    if (towerType != UnitType.LEVEL_ONE_PAINT_TOWER && towerType != UnitType.LEVEL_TWO_PAINT_TOWER && towerType != UnitType.LEVEL_THREE_PAINT_TOWER) {
                        continue;
                    }

                    int x = idx / CACHE_MAP_H;
                    int y = idx % CACHE_MAP_H;
                    MapLocation towerLoc = new MapLocation(x, y);

                    if (rc.canSenseLocation(towerLoc)) {
                        RobotInfo info = rc.senseRobotAtLocation(towerLoc);
                        if (info == null || !info.getType().isTowerType() || info.getTeam() != rc.getTeam() || info.getPaintAmount() <= 300) {
                            continue;
                        }
                    }

                    int dx = currentX - x;
                    int dy = currentY - y;
                    int dist = dx * dx + dy * dy;
                    if (dist < bestPaintTowerDist) {
                        bestPaintTowerDist = dist;
                        bestPaintTowerX = x;
                        bestPaintTowerY = y;
                    }
                }

                if (bestPaintTowerX != -1) {
                    retreatTarget = new MapLocation(bestPaintTowerX, bestPaintTowerY);
                }
            }

            // choose movement part
            if (retreatTarget != null) {
                Direction bestRetreatDir = Direction.CENTER;
                int bestRetreatScore = Integer.MIN_VALUE;
                int bestRetreatActionType = ACTION_NONE;
                MapLocation bestRetreatActionLocation = null;

                Direction[] retreatCandidates = {
                    Direction.CENTER,
                    Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
                    Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
                };

                for (Direction dir : retreatCandidates) {
                    if (dir != Direction.CENTER && !rc.canMove(dir)) {
                        continue;
                    }

                    MapLocation nextLocation;
                    if (dir == Direction.CENTER) {
                        nextLocation = currentLocation;
                    }
                    else {
                        nextLocation = currentLocation.add(dir);
                    }

                    int nextX = nextLocation.x;
                    int nextY = nextLocation.y;
                    int score = 0;
                    int candidateActionType = ACTION_NONE;
                    MapLocation candidateActionLocation = null;

                    if (rc.canSenseLocation(nextLocation)) {
                        PaintType paint = rc.senseMapInfo(nextLocation).getPaint();
                        if (paint.isAlly()) {
                            if (isEarly) {
                                score += SPLASHER_SCORE_RETREAT_ALLY_PAINT_TILE_EARLY;
                            }
                            else if (isMid) {
                                score += SPLASHER_SCORE_RETREAT_ALLY_PAINT_TILE_MID;
                            }
                            else {
                                score += SPLASHER_SCORE_RETREAT_ALLY_PAINT_TILE_LATE;
                            }
                        }
                        else if (paint == PaintType.EMPTY) {
                            if (isEarly) {
                                score += SPLASHER_SCORE_RETREAT_EMPTY_TILE_EARLY;
                            }
                            else if (isMid) {
                                score += SPLASHER_SCORE_RETREAT_EMPTY_TILE_MID;
                            }
                            else {
                                score += SPLASHER_SCORE_RETREAT_EMPTY_TILE_LATE;
                            }
                        }
                        else {
                            if (isEarly) {
                                score -= SPLASHER_SCORE_RETREAT_ENEMY_PAINT_TILE_EARLY;
                            }
                            else if (isMid) {
                                score -= SPLASHER_SCORE_RETREAT_ENEMY_PAINT_TILE_MID;
                            }
                            else {
                                score -= SPLASHER_SCORE_RETREAT_ENEMY_PAINT_TILE_LATE;
                            }
                        }
                    }

                    int before = currentLocation.distanceSquaredTo(retreatTarget);
                    int after = nextLocation.distanceSquaredTo(retreatTarget);

                    if (after < before) {
                        if (isEarly) {
                            score += SPLASHER_SCORE_RETREAT_CLOSER_TO_TARGET_EARLY;
                        }
                        else if (isMid) {
                            score += SPLASHER_SCORE_RETREAT_CLOSER_TO_TARGET_MID;
                        }
                        else {
                            score += SPLASHER_SCORE_RETREAT_CLOSER_TO_TARGET_LATE;
                        }
                    }
                    else if (after == before) {
                        if (isEarly) {
                            score += SPLASHER_SCORE_RETREAT_SAME_DISTANCE_EARLY;
                        }
                        else if (isMid) {
                            score += SPLASHER_SCORE_RETREAT_SAME_DISTANCE_MID;
                        }
                        else {
                            score += SPLASHER_SCORE_RETREAT_SAME_DISTANCE_LATE;
                        }
                    }
                    else {
                        if (isEarly) {
                            score -= SPLASHER_SCORE_RETREAT_FARTHER_FROM_TARGET_EARLY;
                        }
                        else if (isMid) {
                            score -= SPLASHER_SCORE_RETREAT_FARTHER_FROM_TARGET_MID;
                        }
                        else {
                            score -= SPLASHER_SCORE_RETREAT_FARTHER_FROM_TARGET_LATE;
                        }
                    }

                    if (isEarly) {
                        score -= SPLASHER_SCORE_RETREAT_DISTANCE_PENALTY_EARLY * after;
                    }
                    else if (isMid) {
                        score -= SPLASHER_SCORE_RETREAT_DISTANCE_PENALTY_MID * after;
                    }
                    else {
                        score -= SPLASHER_SCORE_RETREAT_DISTANCE_PENALTY_LATE * after;
                    }

                    if (after <= 2) {
                        if (isEarly) {
                            score += SPLASHER_SCORE_RETREAT_IN_PAINT_TRANSFER_RANGE_EARLY;
                        }
                        else if (isMid) {
                            score += SPLASHER_SCORE_RETREAT_IN_PAINT_TRANSFER_RANGE_MID;
                        }
                        else {
                            score += SPLASHER_SCORE_RETREAT_IN_PAINT_TRANSFER_RANGE_LATE;
                        }
                        candidateActionType = ACTION_SOLDIER_REQUEST_PAINT;
                        candidateActionLocation = retreatTarget;
                    }

                    int nextIndex = nextX * CACHE_MAP_H + nextY;
                    int lastVisited = tileLastVisitedRound[nextIndex];
                    if (lastVisited > 0) {
                        int age = round - lastVisited;
                        if (age <= 6) {
                            if (isEarly) {
                                score -= SPLASHER_SCORE_RETREAT_RECENT_VISIT_PENALTY_6_EARLY;
                            }
                            else if (isMid) {
                                score -= SPLASHER_SCORE_RETREAT_RECENT_VISIT_PENALTY_6_MID;
                            }
                            else {
                                score -= SPLASHER_SCORE_RETREAT_RECENT_VISIT_PENALTY_6_LATE;
                            }
                        }
                        else if (age <= 12) {
                            if (isEarly) {
                                score -= SPLASHER_SCORE_RETREAT_RECENT_VISIT_PENALTY_12_EARLY;
                            }
                            else if (isMid) {
                                score -= SPLASHER_SCORE_RETREAT_RECENT_VISIT_PENALTY_12_MID;
                            }
                            else {
                                score -= SPLASHER_SCORE_RETREAT_RECENT_VISIT_PENALTY_12_LATE;
                            }
                        }
                    }

                    for (RobotInfo enemy : nearbyEnemies) {
                        int enemyBefore = currentLocation.distanceSquaredTo(enemy.getLocation());
                        int enemyAfter = nextLocation.distanceSquaredTo(enemy.getLocation());
                        if (enemyAfter > enemyBefore) {
                            if (isEarly) {
                                score += SPLASHER_SCORE_RETREAT_FARTHER_FROM_ENEMY_EARLY;
                            }
                            else if (isMid) {
                                score += SPLASHER_SCORE_RETREAT_FARTHER_FROM_ENEMY_MID;
                            }
                            else {
                                score += SPLASHER_SCORE_RETREAT_FARTHER_FROM_ENEMY_LATE;
                            }
                        }
                        else if (enemyAfter < enemyBefore) {
                            if (isEarly) {
                                score -= SPLASHER_SCORE_RETREAT_CLOSER_TO_ENEMY_EARLY;
                            }
                            else if (isMid) {
                                score -= SPLASHER_SCORE_RETREAT_CLOSER_TO_ENEMY_MID;
                            }
                            else {
                                score -= SPLASHER_SCORE_RETREAT_CLOSER_TO_ENEMY_LATE;
                            }
                        }
                    }

                    if (score > bestRetreatScore) {
                        bestRetreatScore = score;
                        bestRetreatDir = dir;
                        bestRetreatActionType = candidateActionType;
                        bestRetreatActionLocation = candidateActionLocation;
                    }
                }

                return new BotPlans(
                    bestRetreatDir,
                    bestRetreatActionType,
                    bestRetreatActionLocation,
                    false,
                    bestRetreatScore,
                    null
                );
            }
        }

        Direction[] moveCandidates = {
            Direction.CENTER,
            Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
            Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
        };

        Direction splasherLeft = SPLASHER_DIRECTION.rotateLeft();
        Direction splasherRight = SPLASHER_DIRECTION.rotateRight();

        // find objectives, priority : 
        // generally, no wall, can be attacked now, no allied tower, HEAVILY PENALIZE ATTACKING CORRECT MARK PAINT ID = 2
        // enemy tower gets highest scoring
        // enemy tiles next
        // empty tiles next
        int objectiveType = ACTION_NONE;
        MapLocation objectiveLocation = null;
        boolean objectiveUseSecondaryColor = false;
        int objectiveScore = Integer.MIN_VALUE;
        UnitType objectiveTowerType = null;

        for (MapInfo tile : nearbyTiles) {
            MapLocation tileLocation = tile.getMapLocation();

            if (tile.isWall()) {
                continue;
            }

            boolean canAttackTile = rc.canAttack(tileLocation);
            if (!canAttackTile) {
                continue;
            }

            RobotInfo occupant = null;
            boolean enemyTower = false;
            boolean alliedTower = false;

            if (rc.canSenseLocation(tileLocation)) {
                occupant = rc.senseRobotAtLocation(tileLocation);

                if (occupant != null && occupant.getType().isTowerType()) {
                    if (occupant.getTeam() == rc.getTeam()) {
                        alliedTower = true;
                    }
                    else if (occupant.getTeam() == rc.getTeam().opponent()) {
                        enemyTower = true;
                    }
                }
            }

            if (alliedTower) {
                continue;
            }

            int score = Integer.MIN_VALUE;

            if (enemyTower) {
                // attack tower
                if (isEarly) {
                    score = SPLASHER_SCORE_ATTACK_ENEMY_TOWER_EARLY;
                }
                else if (isMid) {
                    score = SPLASHER_SCORE_ATTACK_ENEMY_TOWER_MID;
                }
                else {
                    score = SPLASHER_SCORE_ATTACK_ENEMY_TOWER_LATE;
                }
            }
            else {
                // attack tile
                PaintType paint = tile.getPaint();

                if (paint == PaintType.EMPTY) {
                    if (isEarly) {
                        score = SPLASHER_SCORE_ATTACK_EMPTY_TILE_EARLY;
                    }
                    else if (isMid) {
                        score = SPLASHER_SCORE_ATTACK_EMPTY_TILE_MID;
                    }
                    else {
                        score = SPLASHER_SCORE_ATTACK_EMPTY_TILE_LATE;
                    }
                }
                else if (!paint.isAlly()) {
                    if (isEarly) {
                        score = SPLASHER_SCORE_ATTACK_ENEMY_TILE_EARLY;
                    }
                    else if (isMid) {
                        score = SPLASHER_SCORE_ATTACK_ENEMY_TILE_MID;
                    }
                    else {
                        score = SPLASHER_SCORE_ATTACK_ENEMY_TILE_LATE;
                    }
                }
                else {
                    continue;
                }
            }

            if (isEarly || isMid) {
                for (int dx = -1 ; dx <= 1 ; dx++) {
                    int splashX = tileLocation.x + dx;
                    if (splashX < 0 || splashX >= CACHE_MAP_W) {
                        continue;
                    }

                    for (int dy = -1 ; dy <= 1 ; dy++) {
                        int splashY = tileLocation.y + dy;
                        if (splashY < 0 || splashY >= CACHE_MAP_H) {
                            continue;
                        }

                        MapLocation splashLoc = new MapLocation(splashX, splashY);
                        if (!rc.canSenseLocation(splashLoc)) {
                            continue;
                        }

                        MapInfo splashTile = rc.senseMapInfo(splashLoc);
                        PaintType splashMark = splashTile.getMark();
                        PaintType splashPaint = splashTile.getPaint();

                        // check secondary, if yes then penalize
                        if (splashMark == PaintType.ALLY_SECONDARY && splashPaint == PaintType.ALLY_SECONDARY) {
                            if (isEarly) {
                                score -= SPLASHER_SCORE_ATTACK_ALLY_SECONDARY_SPLASH_PENALTY_EARLY;
                            }
                            else {
                                score -= SPLASHER_SCORE_ATTACK_ALLY_SECONDARY_SPLASH_PENALTY_MID;
                            }
                        }
                    }
                }
            }

            if (tileLocation.equals(currentLocation)) {
                if (isEarly) {
                    score += SPLASHER_SCORE_ATTACK_CURRENT_TILE_BONUS_EARLY;
                }
                else if (isMid) {
                    score += SPLASHER_SCORE_ATTACK_CURRENT_TILE_BONUS_MID;
                }
                else {
                    score += SPLASHER_SCORE_ATTACK_CURRENT_TILE_BONUS_LATE;
                }
            }

            if (score > objectiveScore) {
                objectiveScore = score;
                objectiveType = ACTION_SPLASHER_ATTACK;
                objectiveLocation = tileLocation;
                objectiveUseSecondaryColor = false;
                objectiveTowerType = null;
            }
        }

        int objectiveDistBefore = -1;
        if (objectiveLocation != null) {
            objectiveDistBefore = currentLocation.distanceSquaredTo(objectiveLocation);
        }

        // choosing steps part, priority : 
        // generally, same as soldiers and other bots 
        // closer to objective then better
        // in late game, moving closer to enemy tower the better 
        // if no target at all, explore (unseen and stuff)
        int enemyTowerTargetX = -1;
        int enemyTowerTargetY = -1;
        int enemyTowerTargetDist = Integer.MAX_VALUE;

        int exploreTargetX = -1;
        int exploreTargetY = -1;

        if (objectiveLocation == null) {
            // find good exploration part
            if (!isEarly && !isMid) {
                for (int i = 0 ; i < knownEnemyTowerCount ; i++) {
                    int idx = knownEnemyTowers[i];
                    if ((tileFlags[idx] & TILE_ENEMY_TOWER) == 0) {
                        continue;
                    }

                    int x = idx / CACHE_MAP_H;
                    int y = idx % CACHE_MAP_H;
                    int dx = currentX - x;
                    int dy = currentY - y;
                    int dist = dx * dx + dy * dy;

                    if (dist < enemyTowerTargetDist) {
                        enemyTowerTargetDist = dist;
                        enemyTowerTargetX = x;
                        enemyTowerTargetY = y;
                    }
                }
            }

            if (enemyTowerTargetX == -1) {
                int bestExploreScore = Integer.MIN_VALUE;

                for (int dx = -4 ; dx <= 4 ; dx++) {
                    int scanX = currentX + dx;
                    if (scanX < 0 || scanX >= CACHE_MAP_W) {
                        continue;
                    }

                    int dx2 = dx * dx;

                    for (int dy = -4 ; dy <= 4 ; dy++) {
                        int dist2 = dx2 + dy * dy;
                        if (dist2 > 20) {
                            continue;
                        }

                        int scanY = currentY + dy;
                        if (scanY < 0 || scanY >= CACHE_MAP_H) {
                            continue;
                        }

                        int scanIndex = scanX * CACHE_MAP_H + scanY;
                        if ((tileFlags[scanIndex] & TILE_WALL) != 0) {
                            continue;
                        }

                        int score;

                        if ((tileFlags[scanIndex] & TILE_SEEN) == 0) {
                            if (isEarly) {
                                score = SPLASHER_SCORE_EXPLORE_UNSEEN_TILE_EARLY;
                            }
                            else if (isMid) {
                                score = SPLASHER_SCORE_EXPLORE_UNSEEN_TILE_MID;
                            }
                            else {
                                score = SPLASHER_SCORE_EXPLORE_UNSEEN_TILE_LATE;
                            }
                        }
                        else {
                            int stale = round - tileLastSeenRound[scanIndex];
                            if (stale > 60) {
                                if (isEarly) {
                                    score = SPLASHER_SCORE_EXPLORE_STALE_TILE_60_EARLY;
                                }
                                else if (isMid) {
                                    score = SPLASHER_SCORE_EXPLORE_STALE_TILE_60_MID;
                                }
                                else {
                                    score = SPLASHER_SCORE_EXPLORE_STALE_TILE_60_LATE;
                                }
                            }
                            else if (stale > 25) {
                                if (isEarly) {
                                    score = SPLASHER_SCORE_EXPLORE_STALE_TILE_25_EARLY;
                                }
                                else if (isMid) {
                                    score = SPLASHER_SCORE_EXPLORE_STALE_TILE_25_MID;
                                }
                                else {
                                    score = SPLASHER_SCORE_EXPLORE_STALE_TILE_25_LATE;
                                }
                            }
                            else {
                                continue;
                            }
                        }

                        int lastVisited = tileLastVisitedRound[scanIndex];
                        if (lastVisited > 0) {
                            int age = round - lastVisited;
                            if (age <= 6) {
                                if (isEarly) {
                                    score -= SPLASHER_SCORE_EXPLORE_RECENT_VISIT_PENALTY_6_EARLY;
                                }
                                else if (isMid) {
                                    score -= SPLASHER_SCORE_EXPLORE_RECENT_VISIT_PENALTY_6_MID;
                                }
                                else {
                                    score -= SPLASHER_SCORE_EXPLORE_RECENT_VISIT_PENALTY_6_LATE;
                                }
                            }
                            else if (age <= 12) {
                                if (isEarly) {
                                    score -= SPLASHER_SCORE_EXPLORE_RECENT_VISIT_PENALTY_12_EARLY;
                                }
                                else if (isMid) {
                                    score -= SPLASHER_SCORE_EXPLORE_RECENT_VISIT_PENALTY_12_MID;
                                }
                                else {
                                    score -= SPLASHER_SCORE_EXPLORE_RECENT_VISIT_PENALTY_12_LATE;
                                }
                            }
                            else if (age <= 25) {
                                if (isEarly) {
                                    score -= SPLASHER_SCORE_EXPLORE_RECENT_VISIT_PENALTY_25_EARLY;
                                }
                                else if (isMid) {
                                    score -= SPLASHER_SCORE_EXPLORE_RECENT_VISIT_PENALTY_25_MID;
                                }
                                else {
                                    score -= SPLASHER_SCORE_EXPLORE_RECENT_VISIT_PENALTY_25_LATE;
                                }
                            }
                        }
                        else {
                            if (isEarly) {
                                score += SPLASHER_SCORE_EXPLORE_NEVER_VISITED_BONUS_EARLY;
                            }
                            else if (isMid) {
                                score += SPLASHER_SCORE_EXPLORE_NEVER_VISITED_BONUS_MID;
                            }
                            else {
                                score += SPLASHER_SCORE_EXPLORE_NEVER_VISITED_BONUS_LATE;
                            }
                        }

                        if (isEarly) {
                            score -= SPLASHER_SCORE_EXPLORE_DISTANCE_PENALTY_EARLY * dist2;
                        }
                        else if (isMid) {
                            score -= SPLASHER_SCORE_EXPLORE_DISTANCE_PENALTY_MID * dist2;
                        }
                        else {
                            score -= SPLASHER_SCORE_EXPLORE_DISTANCE_PENALTY_LATE * dist2;
                        }

                        if (scanX == 0 || scanX == CACHE_MAP_W - 1) {
                            if (isEarly) {
                                score -= SPLASHER_SCORE_EXPLORE_EDGE_PENALTY_EARLY;
                            }
                            else if (isMid) {
                                score -= SPLASHER_SCORE_EXPLORE_EDGE_PENALTY_MID;
                            }
                            else {
                                score -= SPLASHER_SCORE_EXPLORE_EDGE_PENALTY_LATE;
                            }
                        }
                        if (scanY == 0 || scanY == CACHE_MAP_H - 1) {
                            if (isEarly) {
                                score -= SPLASHER_SCORE_EXPLORE_EDGE_PENALTY_EARLY;
                            }
                            else if (isMid) {
                                score -= SPLASHER_SCORE_EXPLORE_EDGE_PENALTY_MID;
                            }
                            else {
                                score -= SPLASHER_SCORE_EXPLORE_EDGE_PENALTY_LATE;
                            }
                        }

                        if (score > bestExploreScore) {
                            bestExploreScore = score;
                            exploreTargetX = scanX;
                            exploreTargetY = scanY;
                        }
                    }
                }
            }
        }

        BotPlans bestPlan = new BotPlans(Direction.CENTER, objectiveType, objectiveLocation, objectiveUseSecondaryColor, Integer.MIN_VALUE, objectiveTowerType);

        for (Direction moveDirection : moveCandidates) {
            if (moveDirection != Direction.CENTER && !rc.canMove(moveDirection)) {
                continue;
            }

            MapLocation nextLocation;
            if (moveDirection == Direction.CENTER) {
                nextLocation = currentLocation;
            }
            else {
                nextLocation = currentLocation.add(moveDirection);
            }

            int nextX = nextLocation.x;
            int nextY = nextLocation.y;
            int moveScore = 0;

            if (rc.canSenseLocation(nextLocation)) {
                PaintType paint = rc.senseMapInfo(nextLocation).getPaint();

                // as usual, check paint
                if (isEarly) {
                    if (paint == PaintType.EMPTY) {
                        moveScore += SPLASHER_SCORE_MOVE_EMPTY_TILE_EARLY;
                    }
                    else if (paint.isAlly()) {
                        moveScore += SPLASHER_SCORE_MOVE_ALLY_TILE_EARLY;
                    }
                    else {
                        moveScore -= SPLASHER_SCORE_MOVE_ENEMY_TILE_EARLY;
                    }
                }
                else if (isMid) {
                    if (paint == PaintType.EMPTY) {
                        moveScore += SPLASHER_SCORE_MOVE_EMPTY_TILE_MID;
                    }
                    else if (paint.isAlly()) {
                        moveScore += SPLASHER_SCORE_MOVE_ALLY_TILE_MID;
                    }
                    else {
                        moveScore -= SPLASHER_SCORE_MOVE_ENEMY_TILE_MID;
                    }
                }
                else {
                    if (paint == PaintType.EMPTY) {
                        moveScore += SPLASHER_SCORE_MOVE_EMPTY_TILE_LATE;
                    }
                    else if (paint.isAlly()) {
                        moveScore += SPLASHER_SCORE_MOVE_ALLY_TILE_LATE;
                    }
                    else {
                        moveScore -= SPLASHER_SCORE_MOVE_ENEMY_TILE_LATE;
                    }
                }
            }

            if (moveDirection == SPLASHER_DIRECTION) {
                if (isEarly) {
                    moveScore += SPLASHER_SCORE_MOVE_FORWARD_EARLY;
                }
                else if (isMid) {
                    moveScore += SPLASHER_SCORE_MOVE_FORWARD_MID;
                }
                else {
                    moveScore += SPLASHER_SCORE_MOVE_FORWARD_LATE;
                }
            }
            else if (moveDirection == splasherLeft || moveDirection == splasherRight) {
                if (isEarly) {
                    moveScore += SPLASHER_SCORE_MOVE_SIDE_EARLY;
                }
                else if (isMid) {
                    moveScore += SPLASHER_SCORE_MOVE_SIDE_MID;
                }
                else {
                    moveScore += SPLASHER_SCORE_MOVE_SIDE_LATE;
                }
            }
            else if (moveDirection != Direction.CENTER) {
                if (isEarly) {
                    moveScore -= SPLASHER_SCORE_MOVE_OTHER_DIRECTION_PENALTY_EARLY;
                }
                else if (isMid) {
                    moveScore -= SPLASHER_SCORE_MOVE_OTHER_DIRECTION_PENALTY_MID;
                }
                else {
                    moveScore -= SPLASHER_SCORE_MOVE_OTHER_DIRECTION_PENALTY_LATE;
                }
            }

            if (objectiveLocation != null) {
                // go to objective
                int after = nextLocation.distanceSquaredTo(objectiveLocation);

                if (after < objectiveDistBefore) {
                    if (isEarly) {
                        moveScore += SPLASHER_SCORE_MOVE_OBJECTIVE_CLOSER_EARLY;
                    }
                    else if (isMid) {
                        moveScore += SPLASHER_SCORE_MOVE_OBJECTIVE_CLOSER_MID;
                    }
                    else {
                        moveScore += SPLASHER_SCORE_MOVE_OBJECTIVE_CLOSER_LATE;
                    }
                }
                else if (after > objectiveDistBefore) {
                    if (isEarly) {
                        moveScore -= SPLASHER_SCORE_MOVE_OBJECTIVE_FARTHER_EARLY;
                    }
                    else if (isMid) {
                        moveScore -= SPLASHER_SCORE_MOVE_OBJECTIVE_FARTHER_MID;
                    }
                    else {
                        moveScore -= SPLASHER_SCORE_MOVE_OBJECTIVE_FARTHER_LATE;
                    }
                }
            }
            else if (enemyTowerTargetX != -1) {
                // enemy tower
                int before = (currentX - enemyTowerTargetX) * (currentX - enemyTowerTargetX) + (currentY - enemyTowerTargetY) * (currentY - enemyTowerTargetY);
                int after = (nextX - enemyTowerTargetX) * (nextX - enemyTowerTargetX) + (nextY - enemyTowerTargetY) * (nextY - enemyTowerTargetY);

                if (after < before) {
                    if (isEarly) {
                        moveScore += SPLASHER_SCORE_MOVE_ENEMY_TOWER_TARGET_CLOSER_EARLY;
                    }
                    else if (isMid) {
                        moveScore += SPLASHER_SCORE_MOVE_ENEMY_TOWER_TARGET_CLOSER_MID;
                    }
                    else {
                        moveScore += SPLASHER_SCORE_MOVE_ENEMY_TOWER_TARGET_CLOSER_LATE;
                    }
                }
                else if (after == before) {
                    if (isEarly) {
                        moveScore += SPLASHER_SCORE_MOVE_ENEMY_TOWER_TARGET_SAME_DISTANCE_EARLY;
                    }
                    else if (isMid) {
                        moveScore += SPLASHER_SCORE_MOVE_ENEMY_TOWER_TARGET_SAME_DISTANCE_MID;
                    }
                    else {
                        moveScore += SPLASHER_SCORE_MOVE_ENEMY_TOWER_TARGET_SAME_DISTANCE_LATE;
                    }
                }
                else {
                    if (isEarly) {
                        moveScore -= SPLASHER_SCORE_MOVE_ENEMY_TOWER_TARGET_FARTHER_EARLY;
                    }
                    else if (isMid) {
                        moveScore -= SPLASHER_SCORE_MOVE_ENEMY_TOWER_TARGET_FARTHER_MID;
                    }
                    else {
                        moveScore -= SPLASHER_SCORE_MOVE_ENEMY_TOWER_TARGET_FARTHER_LATE;
                    }
                }

                if (isEarly) {
                    moveScore -= SPLASHER_SCORE_MOVE_ENEMY_TOWER_TARGET_DISTANCE_PENALTY_EARLY * after;
                }
                else if (isMid) {
                    moveScore -= SPLASHER_SCORE_MOVE_ENEMY_TOWER_TARGET_DISTANCE_PENALTY_MID * after;
                }
                else {
                    moveScore -= SPLASHER_SCORE_MOVE_ENEMY_TOWER_TARGET_DISTANCE_PENALTY_LATE * after;
                }
            }
            else if (exploreTargetX != -1) {
                // to exploration
                int before = (currentX - exploreTargetX) * (currentX - exploreTargetX) + (currentY - exploreTargetY) * (currentY - exploreTargetY);
                int after = (nextX - exploreTargetX) * (nextX - exploreTargetX) + (nextY - exploreTargetY) * (nextY - exploreTargetY);

                if (after < before) {
                    if (isEarly) {
                        moveScore += SPLASHER_SCORE_MOVE_EXPLORE_TARGET_CLOSER_EARLY;
                    }
                    else if (isMid) {
                        moveScore += SPLASHER_SCORE_MOVE_EXPLORE_TARGET_CLOSER_MID;
                    }
                    else {
                        moveScore += SPLASHER_SCORE_MOVE_EXPLORE_TARGET_CLOSER_LATE;
                    }
                }
                else if (after == before) {
                    if (isEarly) {
                        moveScore += SPLASHER_SCORE_MOVE_EXPLORE_TARGET_SAME_DISTANCE_EARLY;
                    }
                    else if (isMid) {
                        moveScore += SPLASHER_SCORE_MOVE_EXPLORE_TARGET_SAME_DISTANCE_MID;
                    }
                    else {
                        moveScore += SPLASHER_SCORE_MOVE_EXPLORE_TARGET_SAME_DISTANCE_LATE;
                    }
                }
                else {
                    if (isEarly) {
                        moveScore -= SPLASHER_SCORE_MOVE_EXPLORE_TARGET_FARTHER_EARLY;
                    }
                    else if (isMid) {
                        moveScore -= SPLASHER_SCORE_MOVE_EXPLORE_TARGET_FARTHER_MID;
                    }
                    else {
                        moveScore -= SPLASHER_SCORE_MOVE_EXPLORE_TARGET_FARTHER_LATE;
                    }
                }

                if (isEarly) {
                    moveScore -= SPLASHER_SCORE_MOVE_EXPLORE_TARGET_DISTANCE_PENALTY_EARLY * after;
                }
                else if (isMid) {
                    moveScore -= SPLASHER_SCORE_MOVE_EXPLORE_TARGET_DISTANCE_PENALTY_MID * after;
                }
                else {
                    moveScore -= SPLASHER_SCORE_MOVE_EXPLORE_TARGET_DISTANCE_PENALTY_LATE * after;
                }
            }
            else {
                // if nothing else, go to unseen
                int nextIndex = nextX * CACHE_MAP_H + nextY;

                if ((tileFlags[nextIndex] & TILE_SEEN) == 0) {
                    if (isEarly) {
                        moveScore += SPLASHER_SCORE_MOVE_UNSEEN_TILE_EARLY;
                    }
                    else if (isMid) {
                        moveScore += SPLASHER_SCORE_MOVE_UNSEEN_TILE_MID;
                    }
                    else {
                        moveScore += SPLASHER_SCORE_MOVE_UNSEEN_TILE_LATE;
                    }
                }
                else {
                    int stale = round - tileLastSeenRound[nextIndex];
                    if (stale > 40) {
                        if (isEarly) {
                            moveScore += SPLASHER_SCORE_MOVE_STALE_TILE_40_EARLY;
                        }
                        else if (isMid) {
                            moveScore += SPLASHER_SCORE_MOVE_STALE_TILE_40_MID;
                        }
                        else {
                            moveScore += SPLASHER_SCORE_MOVE_STALE_TILE_40_LATE;
                        }
                    }
                    else if (stale > 20) {
                        if (isEarly) {
                            moveScore += SPLASHER_SCORE_MOVE_STALE_TILE_20_EARLY;
                        }
                        else if (isMid) {
                            moveScore += SPLASHER_SCORE_MOVE_STALE_TILE_20_MID;
                        }
                        else {
                            moveScore += SPLASHER_SCORE_MOVE_STALE_TILE_20_LATE;
                        }
                    }
                }
            }

            int nextIndex = nextX * CACHE_MAP_H + nextY;
            int lastVisited = tileLastVisitedRound[nextIndex];
            if (lastVisited > 0) {
                int age = round - lastVisited;
                if (age <= 6) {
                    if (isEarly) {
                        moveScore -= SPLASHER_SCORE_MOVE_RECENT_VISIT_PENALTY_6_EARLY;
                    }
                    else if (isMid) {
                        moveScore -= SPLASHER_SCORE_MOVE_RECENT_VISIT_PENALTY_6_MID;
                    }
                    else {
                        moveScore -= SPLASHER_SCORE_MOVE_RECENT_VISIT_PENALTY_6_LATE;
                    }
                }
                else if (age <= 12) {
                    if (isEarly) {
                        moveScore -= SPLASHER_SCORE_MOVE_RECENT_VISIT_PENALTY_12_EARLY;
                    }
                    else if (isMid) {
                        moveScore -= SPLASHER_SCORE_MOVE_RECENT_VISIT_PENALTY_12_MID;
                    }
                    else {
                        moveScore -= SPLASHER_SCORE_MOVE_RECENT_VISIT_PENALTY_12_LATE;
                    }
                }
            }
            else {
                if (isEarly) {
                    moveScore += SPLASHER_SCORE_MOVE_NEVER_VISITED_BONUS_EARLY;
                }
                else if (isMid) {
                    moveScore += SPLASHER_SCORE_MOVE_NEVER_VISITED_BONUS_MID;
                }
                else {
                    moveScore += SPLASHER_SCORE_MOVE_NEVER_VISITED_BONUS_LATE;
                }
            }

            if (moveDirection == SPLASHER_DIRECTION) {
                if (isEarly) {
                    moveScore += SPLASHER_SCORE_MOVE_FORWARD_EXPLORE_EARLY;
                }
                else if (isMid) {
                    moveScore += SPLASHER_SCORE_MOVE_FORWARD_EXPLORE_MID;
                }
                else {
                    moveScore += SPLASHER_SCORE_MOVE_FORWARD_EXPLORE_LATE;
                }
            }
            else if (moveDirection == splasherLeft || moveDirection == splasherRight) {
                if (isEarly) {
                    moveScore += SPLASHER_SCORE_MOVE_SIDE_EXPLORE_EARLY;
                }
                else if (isMid) {
                    moveScore += SPLASHER_SCORE_MOVE_SIDE_EXPLORE_MID;
                }
                else {
                    moveScore += SPLASHER_SCORE_MOVE_SIDE_EXPLORE_LATE;
                }
            }

            if (nearbyAllies.length >= 3 && moveDirection == Direction.CENTER) {
                if (isEarly) {
                    moveScore -= SPLASHER_SCORE_MOVE_CENTER_CROWD_PENALTY_EARLY;
                }
                else if (isMid) {
                    moveScore -= SPLASHER_SCORE_MOVE_CENTER_CROWD_PENALTY_MID;
                }
                else {
                    moveScore -= SPLASHER_SCORE_MOVE_CENTER_CROWD_PENALTY_LATE;
                }
            }

            if (nextX == 0 || nextX == CACHE_MAP_W - 1) {
                if (isEarly) {
                    moveScore -= SPLASHER_SCORE_MOVE_EDGE_PENALTY_EARLY;
                }
                else if (isMid) {
                    moveScore -= SPLASHER_SCORE_MOVE_EDGE_PENALTY_MID;
                }
                else {
                    moveScore -= SPLASHER_SCORE_MOVE_EDGE_PENALTY_LATE;
                }
            }
            if (nextY == 0 || nextY == CACHE_MAP_H - 1) {
                if (isEarly) {
                    moveScore -= SPLASHER_SCORE_MOVE_EDGE_PENALTY_EARLY;
                }
                else if (isMid) {
                    moveScore -= SPLASHER_SCORE_MOVE_EDGE_PENALTY_MID;
                }
                else {
                    moveScore -= SPLASHER_SCORE_MOVE_EDGE_PENALTY_LATE;
                }
            }

            int totalScore = moveScore + Math.max(0, objectiveScore);

            if (totalScore > bestPlan.score) {
                bestPlan = new BotPlans(moveDirection, objectiveType, objectiveLocation, objectiveUseSecondaryColor, totalScore, objectiveTowerType);
            }
        }

        return bestPlan;
    }

    public static void executePlanSplasher(RobotController rc, BotPlans botPlans) throws GameActionException {
        if (botPlans == null) {
            return;
        }

        if (botPlans.moveDirection != null && botPlans.moveDirection != Direction.CENTER && rc.canMove(botPlans.moveDirection)) {
            rc.move(botPlans.moveDirection);
            SPLASHER_DIRECTION = botPlans.moveDirection;

            MapLocation newLoc = rc.getLocation();
            int idx = newLoc.x * CACHE_MAP_H + newLoc.y;
            tileLastVisitedRound[idx] = rc.getRoundNum();
        }

        if (botPlans.actionType == ACTION_NONE) {
            return;
        }

        if (botPlans.actionType == ACTION_SOLDIER_REQUEST_PAINT) {
            if (botPlans.actionLocation != null) {
                if (rc.canTransferPaint(botPlans.actionLocation, -100)) {
                    rc.transferPaint(botPlans.actionLocation, -100);
                }
                else if (rc.canTransferPaint(botPlans.actionLocation, -50)) {
                    rc.transferPaint(botPlans.actionLocation, -50);
                }
                else if (rc.canTransferPaint(botPlans.actionLocation, -25)) {
                    rc.transferPaint(botPlans.actionLocation, -25);
                }
                else if (rc.canTransferPaint(botPlans.actionLocation, -10)) {
                    rc.transferPaint(botPlans.actionLocation, -10);
                }
                else if (rc.canTransferPaint(botPlans.actionLocation, -5)) {
                    rc.transferPaint(botPlans.actionLocation, -5);
                }
                else if (rc.canTransferPaint(botPlans.actionLocation, -1)) {
                    rc.transferPaint(botPlans.actionLocation, -1);
                }
            }
            return;
        }

        if (botPlans.actionType == ACTION_SPLASHER_ATTACK) {
            if (botPlans.actionLocation != null && rc.canAttack(botPlans.actionLocation)) {
                rc.attack(botPlans.actionLocation);
            }
            return;
        }
    }
}