package net.mcfire.fallguys.maps;

import io.github.definitlyevil.bukkitces.CustomEntityRegister;
import net.mcfire.fallguys.FallGuys;
import net.mcfire.fallguys.cef.PinkBarrier;
import net.mcfire.fallguys.states.MatchState;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseMap implements FallGuysMap {

    protected final MatchState state;

    protected ConfigurationSection configMap;

    public BaseMap(MatchState state) {
        this.state = state;
        configMap = FallGuys.getInstance().getMapConfiguration(getConfigurationName()); // 加载地图相应的配置文件
    }

    protected abstract String getConfigurationName();

    public Location readConfigLocation(String key) {
        return FallGuys.readLocationFromString(configMap.getString(key));
    }

    public Vector readConfigVector(String key) {
        return FallGuys.readVectorFromString(configMap.getString(key));
    }

    public Location readConfigYawPitch(String key) {
        return FallGuys.readYawPitchFromString(configMap.getString(key));
    }

    public List<Location> readConfigLocationList(String key) {
        List<String> str = configMap.getStringList(key);
        List<Location> locations = new ArrayList<>(str.size());
        str.forEach(s -> {
            Location l = FallGuys.readLocationFromString(s);
            if(l != null) locations.add(l);
        });
        return locations;
    }

    public BoundingBox readBoundingBox(String key) {
        List<String> str = configMap.getStringList(key);
        if(str.size() != 2) return null;
        Vector v1 = FallGuys.readVectorFromString(str.get(0));
        Vector v2 = FallGuys.readVectorFromString(str.get(1));
        return new BoundingBox(v1.getX(), v1.getY(), v1.getZ(), v2.getX(), v2.getY(), v2.getZ());
    }

    public void spawnCustomEntitiesFromConfig(String locationListKey, String type) {
        readConfigLocationList(locationListKey).forEach(spawnAt -> {
            CustomEntityRegister.getInstance()
                .spawn(type, spawnAt, null);
        });
    }

}
