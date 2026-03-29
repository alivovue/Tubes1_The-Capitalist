package alternative_bots_1.bot;

import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Cache {
    public static final byte TILE_SEEN = 1;
    public static final byte TILE_WALL = 2;
    public static final byte TILE_RUIN = 4;
    public static final byte TILE_ALLY_TOWER = 8;
    public static final byte TILE_ENEMY_TOWER = 16;
    public static final byte TILE_ALLY_PAINT = 32;
    public static final byte TILE_ENEMY_PAINT = 64;

    public static int CACHE_MAP_W = -1;
    public static int CACHE_MAP_H = -1;
    public static int CACHE_MAP_SIZE = -1;

    public static byte[] tileFlags;
    public static int[] tileLastSeenRound;
    public static int[] tileLastVisitedRound;

    public static int[] knownRuins;
    public static int knownRuinCount = 0;

    public static int[] knownAllyTowers;
    public static int knownAllyTowerCount = 0;
    public static UnitType[] knownAllyTowerTypes;

    public static int[] knownEnemyTowers;
    public static int knownEnemyTowerCount = 0;

    public static int[] towerSpawnedSoldiers;
    public static int[] towerSpawnedMoppers;
    public static int[] towerSpawnedSplashers;

    // cache per part
    static void initializeCache(RobotController rc) {
        CACHE_MAP_W = rc.getMapWidth();
        CACHE_MAP_H = rc.getMapHeight();
        CACHE_MAP_SIZE = CACHE_MAP_W * CACHE_MAP_H;

        tileFlags = new byte[CACHE_MAP_SIZE];
        tileLastSeenRound = new int[CACHE_MAP_SIZE];
        tileLastVisitedRound = new int[CACHE_MAP_SIZE];

        knownRuins = new int[CACHE_MAP_SIZE];
        knownAllyTowers = new int[CACHE_MAP_SIZE];
        knownAllyTowerTypes = new UnitType[CACHE_MAP_SIZE];
        knownEnemyTowers = new int[CACHE_MAP_SIZE];

        towerSpawnedSoldiers = new int[CACHE_MAP_SIZE];
        towerSpawnedMoppers = new int[CACHE_MAP_SIZE];
        towerSpawnedSplashers = new int[CACHE_MAP_SIZE];

        knownRuinCount = 0;
        knownAllyTowerCount = 0;
        knownEnemyTowerCount = 0;
    }

    static void cacheTiles(RobotController rc, int round, MapInfo[] nearbyTiles) {
        MapLocation currentLocation = rc.getLocation();
        int currentIndex = currentLocation.x * CACHE_MAP_H + currentLocation.y;
        tileFlags[currentIndex] |= TILE_SEEN;
        tileLastSeenRound[currentIndex] = round;
        tileLastVisitedRound[currentIndex] = round;

        for (MapInfo tile : nearbyTiles) {
            MapLocation loc = tile.getMapLocation();
            int idx = loc.x * CACHE_MAP_H + loc.y;

            byte flags = tileFlags[idx];
            flags |= TILE_SEEN;

            if (tile.isWall()) {
                flags |= TILE_WALL;
            }
            else {
                flags &= ~TILE_WALL;
            }

            if (tile.hasRuin()) {
                if ((flags & TILE_RUIN) == 0) {
                    boolean exists = false;
                    for (int i = 0 ; i < knownRuinCount ; i++) {
                        if (knownRuins[i] == idx) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        knownRuins[knownRuinCount++] = idx;
                    }
                }
                flags |= TILE_RUIN;
            }
            else {
                flags &= ~TILE_RUIN;
            }

            flags &= ~(TILE_ALLY_PAINT | TILE_ENEMY_PAINT);
            PaintType paint = tile.getPaint();
            if (paint.isAlly()) {
                flags |= TILE_ALLY_PAINT;
            }
            else if (paint != PaintType.EMPTY) {
                flags |= TILE_ENEMY_PAINT;
            }

            tileFlags[idx] = flags;
            tileLastSeenRound[idx] = round;
        }
    }

    static void cacheAlliedBots(RobotController rc, int round, RobotInfo[] nearbyAllies) {
        for (RobotInfo ally : nearbyAllies) {
            if (!ally.getType().isTowerType()) {
                continue;
            }

            MapLocation loc = ally.getLocation();
            int idx = loc.x * CACHE_MAP_H + loc.y;

            int foundIndex = -1;
            for (int i = 0 ; i < knownAllyTowerCount ; i++) {
                if (knownAllyTowers[i] == idx) {
                    foundIndex = i;
                    break;
                }
            }

            if (foundIndex == -1) {
                knownAllyTowers[knownAllyTowerCount] = idx;
                knownAllyTowerTypes[knownAllyTowerCount] = ally.getType();
                knownAllyTowerCount++;
            }
            else {
                knownAllyTowerTypes[foundIndex] = ally.getType();
            }

            tileFlags[idx] |= TILE_ALLY_TOWER;
            tileFlags[idx] &= ~TILE_ENEMY_TOWER;
            tileFlags[idx] &= ~TILE_RUIN;
            tileFlags[idx] |= TILE_SEEN;
            tileLastSeenRound[idx] = round;
        }
    }

    static void cacheEnemyBots(RobotController rc, int round, RobotInfo[] nearbyEnemies) {
        for (RobotInfo enemy : nearbyEnemies) {
            if (!enemy.getType().isTowerType()) {
                continue;
            }

            MapLocation loc = enemy.getLocation();
            int idx = loc.x * CACHE_MAP_H + loc.y;

            boolean exists = false;
            for (int i = 0 ; i < knownEnemyTowerCount ; i++) {
                if (knownEnemyTowers[i] == idx) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                knownEnemyTowers[knownEnemyTowerCount++] = idx;
            }

            tileFlags[idx] |= TILE_ENEMY_TOWER;
            tileFlags[idx] &= ~TILE_ALLY_TOWER;
            tileFlags[idx] &= ~TILE_RUIN;
            tileFlags[idx] |= TILE_SEEN;
            tileLastSeenRound[idx] = round;
        }
    }

    static void cacheAlliedTowers(RobotController rc) throws GameActionException {
        for (int i = 0 ; i < knownAllyTowerCount ; i++) {
            int idx = knownAllyTowers[i];
            if ((tileFlags[idx] & TILE_ALLY_TOWER) == 0) {
                continue;
            }

            int x = idx / CACHE_MAP_H;
            int y = idx % CACHE_MAP_H;
            MapLocation loc = new MapLocation(x, y);

            if (rc.canSenseLocation(loc)) {
                RobotInfo info = rc.senseRobotAtLocation(loc);
                if (info == null || !info.getType().isTowerType() || info.getTeam() != rc.getTeam()) {
                    tileFlags[idx] &= ~TILE_ALLY_TOWER;
                }
            }
        }
    }

    static void cacheEnemyTowers(RobotController rc) throws GameActionException {
        for (int i = 0 ; i < knownEnemyTowerCount ; i++) {
            int idx = knownEnemyTowers[i];
            if ((tileFlags[idx] & TILE_ENEMY_TOWER) == 0) {
                continue;
            }

            int x = idx / CACHE_MAP_H;
            int y = idx % CACHE_MAP_H;
            MapLocation loc = new MapLocation(x, y);

            if (rc.canSenseLocation(loc)) {
                RobotInfo info = rc.senseRobotAtLocation(loc);
                if (info == null || !info.getType().isTowerType() || info.getTeam() != rc.getTeam().opponent()) {
                    tileFlags[idx] &= ~TILE_ENEMY_TOWER;
                }
            }
        }
    }

    // main cache function, caches the whole map
    public static void cacheMap(RobotController rc, MapInfo[] nearbyTiles, RobotInfo[] nearbyAllies, RobotInfo[] nearbyEnemies) throws GameActionException {
        if (tileFlags == null || CACHE_MAP_W != rc.getMapWidth() || CACHE_MAP_H != rc.getMapHeight()) {
            initializeCache(rc);
        }

        int round = rc.getRoundNum();
        cacheTiles(rc, round, nearbyTiles);
        cacheAlliedBots(rc, round, nearbyAllies);
        cacheEnemyBots(rc, round, nearbyEnemies);
        cacheAlliedTowers(rc);
        cacheEnemyTowers(rc);
    }
}