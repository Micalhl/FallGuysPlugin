package net.mcfire.fallguys;

import net.mcfire.fallguys.maps.*;
import net.mcfire.fallguys.states.MatchState;
import org.bukkit.Bukkit;

import java.util.*;

public final class MapSelector {

    private static final List<String> mapNames = new ArrayList<>();
    private static final Map<String, Class<? extends FallGuysMap>> register = new HashMap<>();

    private static void register(Class<? extends FallGuysMap> clazz) {
        mapNames.add(clazz.getSimpleName());
        register.put(clazz.getSimpleName(), clazz);
    }

    static {
        register(JumpNFallMap.class);
        register(DoorsMap.class);
        register(BarrierGameMap.class);
        register(FootbalMap.class);
        register(CatchTailsMap.class);
        register(WaterClimbMap.class);
        // TODO: developing!
    }

    private static List<String> pool = new ArrayList<>();

    public static FallGuysMap randomMap(MatchState state) throws Exception {
        if(pool.size() <= 0) {
            pool.addAll(mapNames);
        }
        Random rnd = new Random(System.currentTimeMillis() - (Bukkit.getOnlinePlayers().size() * FallGuys.getInstance().getPlayingPlayers().size()));
        int index = rnd.nextInt(pool.size());
        String mapName = pool.remove(index);
        Class<? extends FallGuysMap> mapClazz = register.get(mapName);
        return mapClazz.getDeclaredConstructor(MatchState.class).newInstance(state);
    }

}
