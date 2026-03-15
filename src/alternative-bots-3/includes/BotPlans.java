package alternative_bots_3.includes;

import battlecode.common.Direction;
import battlecode.common.MapLocation;
import battlecode.common.UnitType;

public class BotPlans {
    public Direction moveDirection;
    public int actionType;
    public MapLocation actionLocation;
    public boolean useSecondaryColor;
    public int score;
    public UnitType towerType;

    public BotPlans(Direction moveDirection, int actionType, MapLocation actionLocation, boolean useSecondaryColor, int score, UnitType towerType) {
        this.moveDirection = moveDirection;
        this.actionType = actionType;
        this.actionLocation = actionLocation;
        this.useSecondaryColor = useSecondaryColor;
        this.score = score;
        this.towerType = towerType;
    }
}
