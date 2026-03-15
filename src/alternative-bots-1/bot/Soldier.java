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

public class Soldier {
    // soldier handlers

    static boolean SOLDIER_INITIALIZED = false;
    static Direction SOLDIER_DIRECTION = null;

    public static void handleSoldier(RobotController rc) throws GameActionException {
        // initialize new soldiers
        if (!SOLDIER_INITIALIZED) {
            initializeSoldier(rc);
            SOLDIER_INITIALIZED = true;
        }

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        cacheMap(rc, nearbyTiles, nearbyAllies, nearbyEnemies);

        BotPlans bestPlan = calculateBestPlanSoldier(rc, nearbyTiles, nearbyAllies, nearbyEnemies);

        executePlan(rc, bestPlan);
    }

    public static void initializeSoldier(RobotController rc) throws GameActionException {
        MapLocation currentLocation = rc.getLocation();
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());

        MapLocation nearestTower = null;
        int bestDist = Integer.MAX_VALUE;

        for (RobotInfo ally : allies) {
            if (ally.getType().isTowerType()) {
                int dist = currentLocation.distanceSquaredTo(ally.getLocation());
                if (dist < bestDist) {
                    bestDist = dist;
                    nearestTower = ally.getLocation();
                }
            }
        }

        if (nearestTower != null) {
            SOLDIER_DIRECTION = nearestTower.directionTo(currentLocation);
        }
        else {
            SOLDIER_DIRECTION = Direction.NORTH;
        }

        if (SOLDIER_DIRECTION == Direction.CENTER) {
            SOLDIER_DIRECTION = Direction.NORTH;
        }
    }

    public static BotPlans calculateBestPlanSoldier(RobotController rc, MapInfo[] nearbyTiles, RobotInfo[] nearbyAllies, RobotInfo[] nearbyEnemies) throws GameActionException {
        MapLocation currentLocation = rc.getLocation();
        int currentX = currentLocation.x;
        int currentY = currentLocation.y;
        int round = rc.getRoundNum();
        boolean isEarly = round < STAGE_EARLY_ROUND;
        boolean isMid = round < STAGE_MID_ROUND;

        if (SOLDIER_DIRECTION == null) {
            SOLDIER_DIRECTION = Direction.NORTH;
        }

        // emergency list : 
        // - low paint 
        // behavior : 
        // find nearest allied tower
        // if nearest tower paint empty, then find nearest paint tower
        // if nearest paint tower empty, ignore low paint and kamikaze
        if (rc.getPaint() <= SOLDIER_PAINT_RETREAT_THRESHOLD) {
            MapLocation nearestAnyTower = null;
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
                MapLocation towerLoc = new MapLocation(x, y);
                int dist = currentLocation.distanceSquaredTo(towerLoc);

                if (dist < nearestAnyTowerDist) {
                    nearestAnyTowerDist = dist;
                    nearestAnyTower = towerLoc;

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

            if (nearestAnyTower != null) {
                boolean nearestTowerUsable = true;

                // nearest tower : check if empty, if not then lock in
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
                    retreatTarget = nearestAnyTower;
                }
            }

            // fall here if nearest tower empty
            // nearest paint tower : if not empty then lock in
            if (retreatTarget == null) {
                int bestPaintTowerDist = Integer.MAX_VALUE;

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

                    int dist = currentLocation.distanceSquaredTo(towerLoc);
                    if (dist < bestPaintTowerDist) {
                        bestPaintTowerDist = dist;
                        retreatTarget = towerLoc;
                    }
                }
            }

            // if found available tower, calculate best movement, scoring : 
            // love allied tiles muach
            // meh empty tile
            // hate enemy tiles eewwwwwww
            // add score if movement gets closer to tower, minus if farther
            // add if movement gets away from enemy, minus if closer
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

                    int score = 0;
                    int candidateActionType = ACTION_NONE;
                    MapLocation candidateActionLocation = null;

                    if (rc.canSenseLocation(nextLocation)) {
                        PaintType paint = rc.senseMapInfo(nextLocation).getPaint();
                        if (paint.isAlly()) {
                            if (isEarly) {
                                score += SOLDIER_SCORE_RETREAT_ALLY_PAINT_TILE_EARLY;
                            }
                            else if (isMid) {
                                score += SOLDIER_SCORE_RETREAT_ALLY_PAINT_TILE_MID;
                            }
                            else {
                                score += SOLDIER_SCORE_RETREAT_ALLY_PAINT_TILE_LATE;
                            }
                        }
                        else if (paint == PaintType.EMPTY) {
                            if (isEarly) {
                                score += SOLDIER_SCORE_RETREAT_EMPTY_TILE_EARLY;
                            }
                            else if (isMid) {
                                score += SOLDIER_SCORE_RETREAT_EMPTY_TILE_MID;
                            }
                            else {
                                score += SOLDIER_SCORE_RETREAT_EMPTY_TILE_LATE;
                            }
                        }
                        else {
                            if (isEarly) {
                                score -= SOLDIER_SCORE_RETREAT_ENEMY_PAINT_TILE_EARLY;
                            }
                            else if (isMid) {
                                score -= SOLDIER_SCORE_RETREAT_ENEMY_PAINT_TILE_MID;
                            }
                            else {
                                score -= SOLDIER_SCORE_RETREAT_ENEMY_PAINT_TILE_LATE;
                            }
                        }
                    }

                    int before = currentLocation.distanceSquaredTo(retreatTarget);
                    int after = nextLocation.distanceSquaredTo(retreatTarget);

                    if (after < before) {
                        if (isEarly) {
                            score += SOLDIER_SCORE_RETREAT_CLOSER_TO_TARGET_EARLY;
                        }
                        else if (isMid) {
                            score += SOLDIER_SCORE_RETREAT_CLOSER_TO_TARGET_MID;
                        }
                        else {
                            score += SOLDIER_SCORE_RETREAT_CLOSER_TO_TARGET_LATE;
                        }
                    }
                    else if (after == before) {
                        if (isEarly) {
                            score += SOLDIER_SCORE_RETREAT_SAME_DISTANCE_EARLY;
                        }
                        else if (isMid) {
                            score += SOLDIER_SCORE_RETREAT_SAME_DISTANCE_MID;
                        }
                        else {
                            score += SOLDIER_SCORE_RETREAT_SAME_DISTANCE_LATE;
                        }
                    }
                    else {
                        if (isEarly) {
                            score -= SOLDIER_SCORE_RETREAT_FARTHER_FROM_TARGET_EARLY;
                        }
                        else if (isMid) {
                            score -= SOLDIER_SCORE_RETREAT_FARTHER_FROM_TARGET_MID;
                        }
                        else {
                            score -= SOLDIER_SCORE_RETREAT_FARTHER_FROM_TARGET_LATE;
                        }
                    }

                    if (isEarly) {
                        score -= SOLDIER_SCORE_RETREAT_DISTANCE_PENALTY_EARLY * after;
                    }
                    else if (isMid) {
                        score -= SOLDIER_SCORE_RETREAT_DISTANCE_PENALTY_MID * after;
                    }
                    else {
                        score -= SOLDIER_SCORE_RETREAT_DISTANCE_PENALTY_LATE * after;
                    }

                    if (after <= 2) {
                        if (isEarly) {
                            score += SOLDIER_SCORE_RETREAT_IN_PAINT_TRANSFER_RANGE_EARLY;
                        }
                        else if (isMid) {
                            score += SOLDIER_SCORE_RETREAT_IN_PAINT_TRANSFER_RANGE_MID;
                        }
                        else {
                            score += SOLDIER_SCORE_RETREAT_IN_PAINT_TRANSFER_RANGE_LATE;
                        }
                        candidateActionType = ACTION_SOLDIER_REQUEST_PAINT;
                        candidateActionLocation = retreatTarget;
                    }

                    int nextIndex = nextLocation.x * CACHE_MAP_H + nextLocation.y;
                    int lastVisited = tileLastVisitedRound[nextIndex];
                    if (lastVisited > 0) {
                        int age = round - lastVisited;
                        if (age <= 6) {
                            if (isEarly) {
                                score -= SOLDIER_SCORE_RETREAT_RECENT_VISIT_PENALTY_6_EARLY;
                            }
                            else if (isMid) {
                                score -= SOLDIER_SCORE_RETREAT_RECENT_VISIT_PENALTY_6_MID;
                            }
                            else {
                                score -= SOLDIER_SCORE_RETREAT_RECENT_VISIT_PENALTY_6_LATE;
                            }
                        }
                        else if (age <= 12) {
                            if (isEarly) {
                                score -= SOLDIER_SCORE_RETREAT_RECENT_VISIT_PENALTY_12_EARLY;
                            }
                            else if (isMid) {
                                score -= SOLDIER_SCORE_RETREAT_RECENT_VISIT_PENALTY_12_MID;
                            }
                            else {
                                score -= SOLDIER_SCORE_RETREAT_RECENT_VISIT_PENALTY_12_LATE;
                            }
                        }
                    }

                    for (RobotInfo enemy : nearbyEnemies) {
                        int enemyBefore = currentLocation.distanceSquaredTo(enemy.getLocation());
                        int enemyAfter = nextLocation.distanceSquaredTo(enemy.getLocation());
                        if (enemyAfter > enemyBefore) {
                            if (isEarly) {
                                score += SOLDIER_SCORE_RETREAT_FARTHER_FROM_ENEMY_EARLY;
                            }
                            else if (isMid) {
                                score += SOLDIER_SCORE_RETREAT_FARTHER_FROM_ENEMY_MID;
                            }
                            else {
                                score += SOLDIER_SCORE_RETREAT_FARTHER_FROM_ENEMY_LATE;
                            }
                        }
                        else if (enemyAfter < enemyBefore) {
                            if (isEarly) {
                                score -= SOLDIER_SCORE_RETREAT_CLOSER_TO_ENEMY_EARLY;
                            }
                            else if (isMid) {
                                score -= SOLDIER_SCORE_RETREAT_CLOSER_TO_ENEMY_MID;
                            }
                            else {
                                score -= SOLDIER_SCORE_RETREAT_CLOSER_TO_ENEMY_LATE;
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

        Direction soldierLeft = SOLDIER_DIRECTION.rotateLeft();
        Direction soldierRight = SOLDIER_DIRECTION.rotateRight();

        // to choose the tower that should be built right about now
        UnitType targetTowerType;
        if (rc.getNumberTowers() >= 0 && rc.getNumberTowers() % 3 == 2) {
            targetTowerType = UnitType.LEVEL_ONE_PAINT_TOWER;
        }
        else {
            targetTowerType = UnitType.LEVEL_ONE_MONEY_TOWER;
        }

        // find best objective, priority : 
        // complete ruin
        // fill in marks
        // mark ruins
        // go to visible ruin 
        int objectiveType = ACTION_NONE;
        MapLocation objectiveLocation = null;
        boolean objectiveUseSecondaryColor = false;
        int objectiveScore = Integer.MIN_VALUE;
        UnitType objectiveTowerType = null;

        for (MapInfo tile : nearbyTiles) {
            MapLocation tileLocation = tile.getMapLocation();
            RobotInfo occupant = null;
            boolean occupiedByTower = false;

            if (tile.hasRuin() && rc.canSenseLocation(tileLocation)) {
                occupant = rc.senseRobotAtLocation(tileLocation);
                occupiedByTower = occupant != null && occupant.getType().isTowerType();
            }

            // completable ruin
            if (!occupiedByTower && tile.hasRuin() && targetTowerType != null && rc.canCompleteTowerPattern(targetTowerType, tileLocation)) {
                int score;
                if (isEarly) {
                    score = SOLDIER_SCORE_COMPLETABLE_RUIN_EARLY;
                }
                else if (isMid) {
                    score = SOLDIER_SCORE_COMPLETABLE_RUIN_MID;
                }
                else {
                    score = SOLDIER_SCORE_COMPLETABLE_RUIN_LATE;
                }

                if (score > objectiveScore) {
                    objectiveScore = score;
                    objectiveType = ACTION_SOLDIER_COMPLETE_TOWER;
                    objectiveLocation = tileLocation;
                    objectiveUseSecondaryColor = false;
                    objectiveTowerType = targetTowerType;
                }
                continue;
            }

            // marked tile that should be filled correctly
            if (tile.getMark() != PaintType.EMPTY && tile.getMark() != tile.getPaint() && rc.canAttack(tileLocation)) {
                boolean useSecondary;
                if (tile.getMark() == PaintType.ALLY_SECONDARY) {
                    useSecondary = true;
                }
                else {
                    useSecondary = false;
                }

                int score;
                if (isEarly) {
                    score = SOLDIER_SCORE_MARKED_TILE_TO_FILL_EARLY;
                }
                else if (isMid) {
                    score = SOLDIER_SCORE_MARKED_TILE_TO_FILL_MID;
                }
                else {
                    score = SOLDIER_SCORE_MARKED_TILE_TO_FILL_LATE;
                }

                if (score > objectiveScore) {
                    objectiveScore = score;
                    objectiveType = ACTION_SOLDIER_ATTACK;
                    objectiveLocation = tileLocation;
                    objectiveUseSecondaryColor = useSecondary;
                    objectiveTowerType = null;
                }
            }

            // markable ruin
            if (!occupiedByTower && tile.hasRuin() && tile.getMark() == PaintType.EMPTY && targetTowerType != null && rc.canMarkTowerPattern(targetTowerType, tileLocation) && rc.getNumberTowers() < 25) {
                int score;
                if (isEarly) {
                    score = SOLDIER_SCORE_MARKABLE_RUIN_EARLY;
                }
                else if (isMid) {
                    score = SOLDIER_SCORE_MARKABLE_RUIN_MID;
                }
                else {
                    score = SOLDIER_SCORE_MARKABLE_RUIN_LATE;
                }

                if (score > objectiveScore) {
                    objectiveScore = score;
                    objectiveType = ACTION_SOLDIER_MARK_TOWER;
                    objectiveLocation = tileLocation;
                    objectiveUseSecondaryColor = false;
                    objectiveTowerType = targetTowerType;
                }
            }

            // visible ruin
            if (!occupiedByTower && tile.hasRuin()) {
                int score;
                if (isEarly) {
                    score = SOLDIER_SCORE_VISIBLE_RUIN_EARLY;
                }
                else if (isMid) {
                    score = SOLDIER_SCORE_VISIBLE_RUIN_MID;
                }
                else {
                    score = SOLDIER_SCORE_VISIBLE_RUIN_LATE;
                }

                if (score > objectiveScore) {
                    objectiveScore = score;
                    objectiveType = ACTION_NONE;
                    objectiveLocation = tileLocation;
                    objectiveUseSecondaryColor = false;
                    objectiveTowerType = null;
                }
            }
        }

        if (objectiveType == ACTION_SOLDIER_COMPLETE_TOWER && objectiveLocation != null) {
            return new BotPlans(Direction.CENTER, objectiveType, objectiveLocation, objectiveUseSecondaryColor, objectiveScore, objectiveTowerType);
        }

        int objectiveDistBefore = -1;
        if (objectiveLocation != null) {
            objectiveDistBefore = currentLocation.distanceSquaredTo(objectiveLocation);
        }

        // find best movement for objective, priority : 
        // generally, ewww to enemy tiles, meh to empty tiles, awww to ally tiles, penalty for recently stepped, edge, and crowding
        // if there is objective, then more scoring for getting closer to objective
        // if no objective, then explore unseen tiles
        int explorationTargetX = -1;
        int explorationTargetY = -1;

        if (objectiveLocation == null) {
            // explore part
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
                            score = SOLDIER_SCORE_EXPLORE_UNSEEN_TILE_EARLY;
                        }
                        else if (isMid) {
                            score = SOLDIER_SCORE_EXPLORE_UNSEEN_TILE_MID;
                        }
                        else {
                            score = SOLDIER_SCORE_EXPLORE_UNSEEN_TILE_LATE;
                        }
                    }
                    else {
                        int stale = round - tileLastSeenRound[scanIndex];
                        if (stale > 60) {
                            if (isEarly) {
                                score = SOLDIER_SCORE_EXPLORE_STALE_TILE_60_EARLY;
                            }
                            else if (isMid) {
                                score = SOLDIER_SCORE_EXPLORE_STALE_TILE_60_MID;
                            }
                            else {
                                score = SOLDIER_SCORE_EXPLORE_STALE_TILE_60_LATE;
                            }
                        }
                        else if (stale > 25) {
                            if (isEarly) {
                                score = SOLDIER_SCORE_EXPLORE_STALE_TILE_25_EARLY;
                            }
                            else if (isMid) {
                                score = SOLDIER_SCORE_EXPLORE_STALE_TILE_25_MID;
                            }
                            else {
                                score = SOLDIER_SCORE_EXPLORE_STALE_TILE_25_LATE;
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
                                score -= SOLDIER_SCORE_EXPLORE_RECENT_VISIT_PENALTY_6_EARLY;
                            }
                            else if (isMid) {
                                score -= SOLDIER_SCORE_EXPLORE_RECENT_VISIT_PENALTY_6_MID;
                            }
                            else {
                                score -= SOLDIER_SCORE_EXPLORE_RECENT_VISIT_PENALTY_6_LATE;
                            }
                        }
                        else if (age <= 12) {
                            if (isEarly) {
                                score -= SOLDIER_SCORE_EXPLORE_RECENT_VISIT_PENALTY_12_EARLY;
                            }
                            else if (isMid) {
                                score -= SOLDIER_SCORE_EXPLORE_RECENT_VISIT_PENALTY_12_MID;
                            }
                            else {
                                score -= SOLDIER_SCORE_EXPLORE_RECENT_VISIT_PENALTY_12_LATE;
                            }
                        }
                        else if (age <= 25) {
                            if (isEarly) {
                                score -= SOLDIER_SCORE_EXPLORE_RECENT_VISIT_PENALTY_25_EARLY;
                            }
                            else if (isMid) {
                                score -= SOLDIER_SCORE_EXPLORE_RECENT_VISIT_PENALTY_25_MID;
                            }
                            else {
                                score -= SOLDIER_SCORE_EXPLORE_RECENT_VISIT_PENALTY_25_LATE;
                            }
                        }
                    }
                    else {
                        if (isEarly) {
                            score += SOLDIER_SCORE_EXPLORE_NEVER_VISITED_BONUS_EARLY;
                        }
                        else if (isMid) {
                            score += SOLDIER_SCORE_EXPLORE_NEVER_VISITED_BONUS_MID;
                        }
                        else {
                            score += SOLDIER_SCORE_EXPLORE_NEVER_VISITED_BONUS_LATE;
                        }
                    }

                    if (isEarly) {
                        score -= SOLDIER_SCORE_EXPLORE_DISTANCE_PENALTY_EARLY * dist2;
                    }
                    else if (isMid) {
                        score -= SOLDIER_SCORE_EXPLORE_DISTANCE_PENALTY_MID * dist2;
                    }
                    else {
                        score -= SOLDIER_SCORE_EXPLORE_DISTANCE_PENALTY_LATE * dist2;
                    }

                    if (scanX == 0 || scanX == CACHE_MAP_W - 1) {
                        if (isEarly) {
                            score -= SOLDIER_SCORE_EXPLORE_EDGE_PENALTY_EARLY;
                        }
                        else if (isMid) {
                            score -= SOLDIER_SCORE_EXPLORE_EDGE_PENALTY_MID;
                        }
                        else {
                            score -= SOLDIER_SCORE_EXPLORE_EDGE_PENALTY_LATE;
                        }
                    }
                    if (scanY == 0 || scanY == CACHE_MAP_H - 1) {
                        if (isEarly) {
                            score -= SOLDIER_SCORE_EXPLORE_EDGE_PENALTY_EARLY;
                        }
                        else if (isMid) {
                            score -= SOLDIER_SCORE_EXPLORE_EDGE_PENALTY_MID;
                        }
                        else {
                            score -= SOLDIER_SCORE_EXPLORE_EDGE_PENALTY_LATE;
                        }
                    }

                    if (score > bestExploreScore) {
                        bestExploreScore = score;
                        explorationTargetX = scanX;
                        explorationTargetY = scanY;
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

                // tile paint check part
                if (isEarly) {
                    if (paint == PaintType.EMPTY) {
                        moveScore += SOLDIER_SCORE_MOVE_EMPTY_TILE_EARLY;
                    }
                    else if (paint.isAlly()) {
                        moveScore += SOLDIER_SCORE_MOVE_ALLY_TILE_EARLY;
                    }
                    else {
                        moveScore -= SOLDIER_SCORE_MOVE_ENEMY_TILE_EARLY;
                    }
                }
                else if (isMid) {
                    if (paint == PaintType.EMPTY) {
                        moveScore += SOLDIER_SCORE_MOVE_EMPTY_TILE_MID;
                    }
                    else if (paint.isAlly()) {
                        moveScore += SOLDIER_SCORE_MOVE_ALLY_TILE_MID;
                    }
                    else {
                        moveScore -= SOLDIER_SCORE_MOVE_ENEMY_TILE_MID;
                    }
                }
                else {
                    if (paint == PaintType.EMPTY) {
                        moveScore += SOLDIER_SCORE_MOVE_EMPTY_TILE_LATE;
                    }
                    else if (paint.isAlly()) {
                        moveScore += SOLDIER_SCORE_MOVE_ALLY_TILE_LATE;
                    }
                    else {
                        moveScore -= SOLDIER_SCORE_MOVE_ENEMY_TILE_LATE;
                    }
                }
            }

            if (moveDirection == SOLDIER_DIRECTION) {
                if (isEarly) {
                    moveScore += SOLDIER_SCORE_MOVE_FORWARD_EARLY;
                }
                else if (isMid) {
                    moveScore += SOLDIER_SCORE_MOVE_FORWARD_MID;
                }
                else {
                    moveScore += SOLDIER_SCORE_MOVE_FORWARD_LATE;
                }
            }
            else if (moveDirection == soldierLeft || moveDirection == soldierRight) {
                if (isEarly) {
                    moveScore += SOLDIER_SCORE_MOVE_SIDE_EARLY;
                }
                else if (isMid) {
                    moveScore += SOLDIER_SCORE_MOVE_SIDE_MID;
                }
                else {
                    moveScore += SOLDIER_SCORE_MOVE_SIDE_LATE;
                }
            }
            else if (moveDirection != Direction.CENTER) {
                if (isEarly) {
                    moveScore -= SOLDIER_SCORE_MOVE_OTHER_DIRECTION_PENALTY_EARLY;
                }
                else if (isMid) {
                    moveScore -= SOLDIER_SCORE_MOVE_OTHER_DIRECTION_PENALTY_MID;
                }
                else {
                    moveScore -= SOLDIER_SCORE_MOVE_OTHER_DIRECTION_PENALTY_LATE;
                }
            }

            if (nearbyEnemies.length > 0) {
                if (isEarly) {
                    moveScore -= SOLDIER_SCORE_MOVE_ENEMY_VISIBLE_PENALTY_EARLY;
                }
                else if (isMid) {
                    moveScore -= SOLDIER_SCORE_MOVE_ENEMY_VISIBLE_PENALTY_MID;
                }
                else {
                    moveScore -= SOLDIER_SCORE_MOVE_ENEMY_VISIBLE_PENALTY_LATE;
                }
            }

            if (objectiveLocation != null) {
                // move to objective location
                int after = nextLocation.distanceSquaredTo(objectiveLocation);

                if (after < objectiveDistBefore) {
                    if (isEarly) {
                        moveScore += SOLDIER_SCORE_MOVE_OBJECTIVE_CLOSER_EARLY;
                    }
                    else if (isMid) {
                        moveScore += SOLDIER_SCORE_MOVE_OBJECTIVE_CLOSER_MID;
                    }
                    else {
                        moveScore += SOLDIER_SCORE_MOVE_OBJECTIVE_CLOSER_LATE;
                    }
                }
                else if (after > objectiveDistBefore) {
                    if (isEarly) {
                        moveScore -= SOLDIER_SCORE_MOVE_OBJECTIVE_FARTHER_EARLY;
                    }
                    else if (isMid) {
                        moveScore -= SOLDIER_SCORE_MOVE_OBJECTIVE_FARTHER_MID;
                    }
                    else {
                        moveScore -= SOLDIER_SCORE_MOVE_OBJECTIVE_FARTHER_LATE;
                    }
                }
            }
            else if (explorationTargetX != -1) {
                // move to exploration location
                int before = (currentX - explorationTargetX) * (currentX - explorationTargetX) + (currentY - explorationTargetY) * (currentY - explorationTargetY);
                int after = (nextX - explorationTargetX) * (nextX - explorationTargetX) + (nextY - explorationTargetY) * (nextY - explorationTargetY);

                if (after < before) {
                    if (isEarly) {
                        moveScore += SOLDIER_SCORE_MOVE_EXPLORE_TARGET_CLOSER_EARLY;
                    }
                    else if (isMid) {
                        moveScore += SOLDIER_SCORE_MOVE_EXPLORE_TARGET_CLOSER_MID;
                    }
                    else {
                        moveScore += SOLDIER_SCORE_MOVE_EXPLORE_TARGET_CLOSER_LATE;
                    }
                }
                else if (after == before) {
                    if (isEarly) {
                        moveScore += SOLDIER_SCORE_MOVE_EXPLORE_TARGET_SAME_DISTANCE_EARLY;
                    }
                    else if (isMid) {
                        moveScore += SOLDIER_SCORE_MOVE_EXPLORE_TARGET_SAME_DISTANCE_MID;
                    }
                    else {
                        moveScore += SOLDIER_SCORE_MOVE_EXPLORE_TARGET_SAME_DISTANCE_LATE;
                    }
                }
                else {
                    if (isEarly) {
                        moveScore -= SOLDIER_SCORE_MOVE_EXPLORE_TARGET_FARTHER_EARLY;
                    }
                    else if (isMid) {
                        moveScore -= SOLDIER_SCORE_MOVE_EXPLORE_TARGET_FARTHER_MID;
                    }
                    else {
                        moveScore -= SOLDIER_SCORE_MOVE_EXPLORE_TARGET_FARTHER_LATE;
                    }
                }

                if (isEarly) {
                    moveScore -= SOLDIER_SCORE_MOVE_EXPLORE_TARGET_DISTANCE_PENALTY_EARLY * after;
                }
                else if (isMid) {
                    moveScore -= SOLDIER_SCORE_MOVE_EXPLORE_TARGET_DISTANCE_PENALTY_MID * after;
                }
                else {
                    moveScore -= SOLDIER_SCORE_MOVE_EXPLORE_TARGET_DISTANCE_PENALTY_LATE * after;
                }

                int nextIndex = nextX * CACHE_MAP_H + nextY;
                int lastVisited = tileLastVisitedRound[nextIndex];
                if (lastVisited > 0) {
                    int age = round - lastVisited;
                    if (age <= 6) {
                        if (isEarly) {
                            moveScore -= SOLDIER_SCORE_MOVE_EXPLORE_TARGET_RECENT_VISIT_PENALTY_6_EARLY;
                        }
                        else if (isMid) {
                            moveScore -= SOLDIER_SCORE_MOVE_EXPLORE_TARGET_RECENT_VISIT_PENALTY_6_MID;
                        }
                        else {
                            moveScore -= SOLDIER_SCORE_MOVE_EXPLORE_TARGET_RECENT_VISIT_PENALTY_6_LATE;
                        }
                    }
                    else if (age <= 12) {
                        if (isEarly) {
                            moveScore -= SOLDIER_SCORE_MOVE_EXPLORE_TARGET_RECENT_VISIT_PENALTY_12_EARLY;
                        }
                        else if (isMid) {
                            moveScore -= SOLDIER_SCORE_MOVE_EXPLORE_TARGET_RECENT_VISIT_PENALTY_12_MID;
                        }
                        else {
                            moveScore -= SOLDIER_SCORE_MOVE_EXPLORE_TARGET_RECENT_VISIT_PENALTY_12_LATE;
                        }
                    }
                }
                else {
                    if (isEarly) {
                        moveScore += SOLDIER_SCORE_MOVE_EXPLORE_TARGET_NEVER_VISITED_BONUS_EARLY;
                    }
                    else if (isMid) {
                        moveScore += SOLDIER_SCORE_MOVE_EXPLORE_TARGET_NEVER_VISITED_BONUS_MID;
                    }
                    else {
                        moveScore += SOLDIER_SCORE_MOVE_EXPLORE_TARGET_NEVER_VISITED_BONUS_LATE;
                    }
                }

                if (nextX == 0 || nextX == CACHE_MAP_W - 1) {
                    if (isEarly) {
                        moveScore -= SOLDIER_SCORE_MOVE_EXPLORE_TARGET_EDGE_PENALTY_EARLY;
                    }
                    else if (isMid) {
                        moveScore -= SOLDIER_SCORE_MOVE_EXPLORE_TARGET_EDGE_PENALTY_MID;
                    }
                    else {
                        moveScore -= SOLDIER_SCORE_MOVE_EXPLORE_TARGET_EDGE_PENALTY_LATE;
                    }
                }
                if (nextY == 0 || nextY == CACHE_MAP_H - 1) {
                    if (isEarly) {
                        moveScore -= SOLDIER_SCORE_MOVE_EXPLORE_TARGET_EDGE_PENALTY_EARLY;
                    }
                    else if (isMid) {
                        moveScore -= SOLDIER_SCORE_MOVE_EXPLORE_TARGET_EDGE_PENALTY_MID;
                    }
                    else {
                        moveScore -= SOLDIER_SCORE_MOVE_EXPLORE_TARGET_EDGE_PENALTY_LATE;
                    }
                }

                if (moveDirection == SOLDIER_DIRECTION) {
                    if (isEarly) {
                        moveScore += SOLDIER_SCORE_MOVE_EXPLORE_TARGET_FORWARD_EARLY;
                    }
                    else if (isMid) {
                        moveScore += SOLDIER_SCORE_MOVE_EXPLORE_TARGET_FORWARD_MID;
                    }
                    else {
                        moveScore += SOLDIER_SCORE_MOVE_EXPLORE_TARGET_FORWARD_LATE;
                    }
                }
                else if (moveDirection == soldierLeft || moveDirection == soldierRight) {
                    if (isEarly) {
                        moveScore += SOLDIER_SCORE_MOVE_EXPLORE_TARGET_SIDE_EARLY;
                    }
                    else if (isMid) {
                        moveScore += SOLDIER_SCORE_MOVE_EXPLORE_TARGET_SIDE_MID;
                    }
                    else {
                        moveScore += SOLDIER_SCORE_MOVE_EXPLORE_TARGET_SIDE_LATE;
                    }
                }

                if (nearbyAllies.length >= 3 && moveDirection == Direction.CENTER) {
                    if (isEarly) {
                        moveScore -= SOLDIER_SCORE_MOVE_EXPLORE_TARGET_CENTER_CROWD_PENALTY_EARLY;
                    }
                    else if (isMid) {
                        moveScore -= SOLDIER_SCORE_MOVE_EXPLORE_TARGET_CENTER_CROWD_PENALTY_MID;
                    }
                    else {
                        moveScore -= SOLDIER_SCORE_MOVE_EXPLORE_TARGET_CENTER_CROWD_PENALTY_LATE;
                    }
                }
            }
            else {
                // exploration to random place, go to unseen and such
                int nextIndex = nextX * CACHE_MAP_H + nextY;

                if ((tileFlags[nextIndex] & TILE_SEEN) == 0) {
                    if (isEarly) {
                        moveScore += SOLDIER_SCORE_MOVE_UNSEEN_TILE_EARLY;
                    }
                    else if (isMid) {
                        moveScore += SOLDIER_SCORE_MOVE_UNSEEN_TILE_MID;
                    }
                    else {
                        moveScore += SOLDIER_SCORE_MOVE_UNSEEN_TILE_LATE;
                    }
                }
                else {
                    int stale = round - tileLastSeenRound[nextIndex];
                    if (stale > 40) {
                        if (isEarly) {
                            moveScore += SOLDIER_SCORE_MOVE_STALE_TILE_40_EARLY;
                        }
                        else if (isMid) {
                            moveScore += SOLDIER_SCORE_MOVE_STALE_TILE_40_MID;
                        }
                        else {
                            moveScore += SOLDIER_SCORE_MOVE_STALE_TILE_40_LATE;
                        }
                    }
                    else if (stale > 20) {
                        if (isEarly) {
                            moveScore += SOLDIER_SCORE_MOVE_STALE_TILE_20_EARLY;
                        }
                        else if (isMid) {
                            moveScore += SOLDIER_SCORE_MOVE_STALE_TILE_20_MID;
                        }
                        else {
                            moveScore += SOLDIER_SCORE_MOVE_STALE_TILE_20_LATE;
                        }
                    }
                }

                int lastVisited = tileLastVisitedRound[nextIndex];
                if (lastVisited > 0) {
                    int age = round - lastVisited;
                    if (age <= 6) {
                        if (isEarly) {
                            moveScore -= SOLDIER_SCORE_MOVE_RECENT_VISIT_PENALTY_6_EARLY;
                        }
                        else if (isMid) {
                            moveScore -= SOLDIER_SCORE_MOVE_RECENT_VISIT_PENALTY_6_MID;
                        }
                        else {
                            moveScore -= SOLDIER_SCORE_MOVE_RECENT_VISIT_PENALTY_6_LATE;
                        }
                    }
                    else if (age <= 12) {
                        if (isEarly) {
                            moveScore -= SOLDIER_SCORE_MOVE_RECENT_VISIT_PENALTY_12_EARLY;
                        }
                        else if (isMid) {
                            moveScore -= SOLDIER_SCORE_MOVE_RECENT_VISIT_PENALTY_12_MID;
                        }
                        else {
                            moveScore -= SOLDIER_SCORE_MOVE_RECENT_VISIT_PENALTY_12_LATE;
                        }
                    }
                    else if (age <= 25) {
                        if (isEarly) {
                            moveScore -= SOLDIER_SCORE_MOVE_RECENT_VISIT_PENALTY_25_EARLY;
                        }
                        else if (isMid) {
                            moveScore -= SOLDIER_SCORE_MOVE_RECENT_VISIT_PENALTY_25_MID;
                        }
                        else {
                            moveScore -= SOLDIER_SCORE_MOVE_RECENT_VISIT_PENALTY_25_LATE;
                        }
                    }
                }
                else {
                    if (isEarly) {
                        moveScore += SOLDIER_SCORE_MOVE_NEVER_VISITED_BONUS_EARLY;
                    }
                    else if (isMid) {
                        moveScore += SOLDIER_SCORE_MOVE_NEVER_VISITED_BONUS_MID;
                    }
                    else {
                        moveScore += SOLDIER_SCORE_MOVE_NEVER_VISITED_BONUS_LATE;
                    }
                }

                if (moveDirection == SOLDIER_DIRECTION) {
                    if (isEarly) {
                        moveScore += SOLDIER_SCORE_MOVE_FORWARD_EXPLORE_EARLY;
                    }
                    else if (isMid) {
                        moveScore += SOLDIER_SCORE_MOVE_FORWARD_EXPLORE_MID;
                    }
                    else {
                        moveScore += SOLDIER_SCORE_MOVE_FORWARD_EXPLORE_LATE;
                    }
                }
                else if (moveDirection == soldierLeft || moveDirection == soldierRight) {
                    if (isEarly) {
                        moveScore += SOLDIER_SCORE_MOVE_SIDE_EXPLORE_EARLY;
                    }
                    else if (isMid) {
                        moveScore += SOLDIER_SCORE_MOVE_SIDE_EXPLORE_MID;
                    }
                    else {
                        moveScore += SOLDIER_SCORE_MOVE_SIDE_EXPLORE_LATE;
                    }
                }

                if (nearbyAllies.length >= 3 && moveDirection == Direction.CENTER) {
                    if (isEarly) {
                        moveScore -= SOLDIER_SCORE_MOVE_CENTER_CROWD_PENALTY_EARLY;
                    }
                    else if (isMid) {
                        moveScore -= SOLDIER_SCORE_MOVE_CENTER_CROWD_PENALTY_MID;
                    }
                    else {
                        moveScore -= SOLDIER_SCORE_MOVE_CENTER_CROWD_PENALTY_LATE;
                    }
                }

                if (nextX == 0 || nextX == CACHE_MAP_W - 1) {
                    if (isEarly) {
                        moveScore -= SOLDIER_SCORE_MOVE_EDGE_PENALTY_EARLY;
                    }
                    else if (isMid) {
                        moveScore -= SOLDIER_SCORE_MOVE_EDGE_PENALTY_MID;
                    }
                    else {
                        moveScore -= SOLDIER_SCORE_MOVE_EDGE_PENALTY_LATE;
                    }
                }
                if (nextY == 0 || nextY == CACHE_MAP_H - 1) {
                    if (isEarly) {
                        moveScore -= SOLDIER_SCORE_MOVE_EDGE_PENALTY_EARLY;
                    }
                    else if (isMid) {
                        moveScore -= SOLDIER_SCORE_MOVE_EDGE_PENALTY_MID;
                    }
                    else {
                        moveScore -= SOLDIER_SCORE_MOVE_EDGE_PENALTY_LATE;
                    }
                }
            }

            int totalScore = moveScore + Math.max(0, objectiveScore);

            if (totalScore > bestPlan.score) {
                bestPlan = new BotPlans(moveDirection, objectiveType, objectiveLocation, objectiveUseSecondaryColor, totalScore, objectiveTowerType);
            }
        }

        return bestPlan;
    }

    public static void executePlan(RobotController rc, BotPlans botPlans) throws GameActionException {
        if (botPlans == null) {
            return;
        }

        if (botPlans.moveDirection != null && botPlans.moveDirection != Direction.CENTER && rc.canMove(botPlans.moveDirection)) {
            rc.move(botPlans.moveDirection);
            SOLDIER_DIRECTION = botPlans.moveDirection;

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

        if (botPlans.actionType == ACTION_SOLDIER_ATTACK) {
            if (botPlans.actionLocation != null && rc.canAttack(botPlans.actionLocation)) {
                rc.attack(botPlans.actionLocation, botPlans.useSecondaryColor);
            }
            return;
        }

        if (botPlans.actionType == ACTION_SOLDIER_MARK_TOWER) {
            if (botPlans.actionLocation != null && botPlans.towerType != null && rc.canMarkTowerPattern(botPlans.towerType, botPlans.actionLocation)) {
                rc.markTowerPattern(botPlans.towerType, botPlans.actionLocation);
            }
            return;
        }

        if (botPlans.actionType == ACTION_SOLDIER_COMPLETE_TOWER) {
            if (botPlans.actionLocation != null && botPlans.towerType != null && rc.canCompleteTowerPattern(botPlans.towerType, botPlans.actionLocation)) {
                rc.completeTowerPattern(botPlans.towerType, botPlans.actionLocation);
            }
            return;
        }
    }
}