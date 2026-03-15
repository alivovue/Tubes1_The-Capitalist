package alternative-bots-1.includes;

import battlecode.common.MapLocation;
import battlecode.common.UnitType;

public class TowerPlans {
    public int actionType;
    public UnitType spawnType;
    public MapLocation spawnLocation;
    public int score;

    public TowerPlans(int actionType, UnitType spawnType, MapLocation spawnLocation, int score) {
        this.actionType = actionType;
        this.spawnType = spawnType;
        this.spawnLocation = spawnLocation;
        this.score = score;
    }
}
