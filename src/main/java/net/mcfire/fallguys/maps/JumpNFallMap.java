package net.mcfire.fallguys.maps;

import de.myzelyam.api.vanish.VanishAPI;
import net.mcfire.fallguys.FallGuys;
import net.mcfire.fallguys.states.MatchState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class JumpNFallMap extends BaseMap {

    private static final String CONFIG_KEY_SPAWN_LIST = "spawn-points";

    public JumpNFallMap(MatchState state) {
        super(state);
    }

    @Override
    public String getDisplayName() {
        return "\u00a7e\u00a7l方块迷图";
    }

    private BukkitTask blockRemoverTask = null;
    private Map<Vector, BukkitTask> pendingRemovals = new HashMap<>();

    @Override
    public void onMapLoad() {
        List<Location> spawnPoints = readConfigLocationList(CONFIG_KEY_SPAWN_LIST);
        Location firstSpawn = spawnPoints.get(0);
        Bukkit.getOnlinePlayers().forEach(p ->{
            // 处理观战人员
            if(!FallGuys.getInstance().isPlayerPlaying(p)) {
                p.teleport(firstSpawn);
                p.setGameMode(GameMode.SPECTATOR);
                p.sendTitle("\u00a77你处于观战模式! ", "You're spectating. ", 10, 20, 10);
            }
        });

        Random rnd = new Random(System.currentTimeMillis() - Bukkit.getOnlinePlayers().size());
        for(Player player : FallGuys.getInstance().getPlayingPlayers()) {
            if(spawnPoints.size() == 0) spawnPoints = readConfigLocationList(CONFIG_KEY_SPAWN_LIST);

            int index = rnd.nextInt(spawnPoints.size());
            Location loc = spawnPoints.remove(index);

            player.setGameMode(GameMode.ADVENTURE);
            player.setFlying(false);
            player.setAllowFlight(false);
            player.teleport(loc);

            VanishAPI.showPlayer(player);
        }

        state.setEliminateUnfinished(false);

        state.getBar().setTitle("\u00a7e\u00a7l方 块 迷 图");
    }

    private int getTargetEliminateCount() {
        final int online = FallGuys.getInstance().getPlayingPlayers().size();
        if(online > 10) {
            return online / 4;
        } else if (online > 3) {
            return online / 2;
        } else {
            return 1;
        }
    }

    @Override
    public void onGameStart() {
        blockRemoverTask = Bukkit.getScheduler().runTaskTimer(FallGuys.getInstance(), () -> {
            Bukkit.getOnlinePlayers().forEach(p -> {
                if(!FallGuys.getInstance().isPlayerPlaying(p)) return;
                Location loc = p.getLocation();
                Vector target = loc.toVector();
                if(pendingRemovals.containsKey(target)) return;
                if(Math.floor(target.getY()) - target.getY() < 0.2d) {
                    // Bukkit.broadcastMessage("removing " + target.toString());
                    pendingRemovals.put(target,
                        Bukkit.getScheduler().runTaskLater(FallGuys.getInstance(), () -> {
                            loc.setY(loc.getY() - 1);
                            for(int x = target.getBlockX() - 1; x <= target.getBlockX() + 1; x ++) {
                                for(int z = target.getBlockZ() - 1; z <= target.getBlockZ() + 1; z ++) {
                                    loc.setX(x);
                                    loc.setZ(z);
                                    Block b = loc.getBlock();
                                    if(!b.isEmpty()) {
                                        loc.getBlock().setType(Material.AIR);
                                    }
                                }
                            }
                        }, 20L)
                    );
                }
            });
        }, 10L, 5L);
    }

    @Override
    public boolean isGameFinished() {
        return state.getEliminatedCount() >= getTargetEliminateCount();
    }

    @Override
    public void cleanUp() {
        if(blockRemoverTask != null) {
            blockRemoverTask.cancel();
            blockRemoverTask = null;
        }
    }

    @Override
    public void update() {
        if(state.isStarted()) pendingRemovals.entrySet().removeIf(entry -> !Bukkit.getScheduler().isQueued(entry.getValue().getTaskId()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        if(!FallGuys.getInstance().isPlayerPlaying(event.getPlayer())) return;
        if(!state.isStarted()) event.setCancelled(true);
        if(event.getTo() != null && event.getTo().getY() < -10d) {
            if(!state.isPlayerEliminated(event.getPlayer())) {
                state.eliminatePlayer(event.getPlayer());
            }
            event.setCancelled(true);
        }
    }

    @Override
    protected String getConfigurationName() {
        return "jump-n-fall";
    }

}
