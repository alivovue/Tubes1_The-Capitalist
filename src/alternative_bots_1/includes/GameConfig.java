package alternative_bots_1.includes;

import battlecode.common.Direction;

public class GameConfig {
    // game stages -- batas akhir
    public static final int STAGE_EARLY_ROUND = 500;
    public static final int STAGE_MID_ROUND = 900;
    public static final int STAGE_LATE_ROUND = 2000;

    // init config
    public static final int TOWER_INIT_SOLDIER = 2;

    public static final int SOLDIER_PAINT_RETREAT_THRESHOLD = 50;
    public static final int MOPPER_PAINT_RETREAT_THRESHOLD = 50;
    public static final int SPLASHER_PAINT_RETREAT_THRESHOLD = 50;

    // actions
    // no action
    public static final int ACTION_NONE = 0;

    // soldier actions
    public static final int ACTION_SOLDIER_ATTACK = 1;
    public static final int ACTION_SOLDIER_MARK_TOWER = 2;
    public static final int ACTION_SOLDIER_COMPLETE_TOWER = 3;
    public static final int ACTION_SOLDIER_MARK_SRP = 4;
    public static final int ACTION_SOLDIER_COMPLETE_SRP = 5;
    public static final int ACTION_SOLDIER_UPGRADE_TOWER = 6;

    // mopper actions
    public static final int ACTION_MOPPER_ATTACK = 7;
    public static final int ACTION_MOPPER_SWING = 8;
    public static final int ACTION_MOPPER_UPGRADE_TOWER = 9;

    // splasher actions
    public static final int ACTION_SPLASHER_ATTACK = 10;
    public static final int ACTION_SPLASHER_UPGRADE_TOWER = 11;

    // tower actions
    public static final int ACTION_TOWER_BUILD_SOLDIER = 12;
    public static final int ACTION_TOWER_BUILD_MOPPER = 13;
    public static final int ACTION_TOWER_BUILD_SPLASHER = 14;
    public static final int ACTION_TOWER_ATTACK = 15;
    public static final int ACTION_TOWER_AOE_ATTACK = 16;

    // comms actions
    public static final int ACTION_SEND_MESSAGE = 17;
    public static final int ACTION_SOLDIER_REQUEST_PAINT = 18;

    // direction
    public static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };
}
