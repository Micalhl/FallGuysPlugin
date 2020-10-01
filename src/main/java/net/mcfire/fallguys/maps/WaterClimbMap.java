package net.mcfire.fallguys.maps;

import de.myzelyam.api.vanish.VanishAPI;
import de.tr7zw.nbtinjector.NBTInjector;
import net.mcfire.fallguys.FallGuys;
import net.mcfire.fallguys.states.MatchState;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedList;
import java.util.List;

/**
 * catch-tails:
 *   spawn:
 *     overview: game,-247,217,-191,320,68
 *     game: game,-233,192,-138
 *   water:
 *     pos1: game,68,147,-203
 *     pos2: game,127,147,-90
 *     height: 50
 *     ticks-per-rise: 20
 */
public class WaterClimbMap extends BaseMap {
    private static final ItemStack WATER_SURFACE = new ItemStack(Material.GOLDEN_HOE, 1);
    static {
        ItemMeta m = WATER_SURFACE.getItemMeta();
        m.setCustomModelData(2);
        WATER_SURFACE.setItemMeta(m);
    }

    public WaterClimbMap(MatchState state) {
        super(state);
    }

    private Location baseWaterLocation1;
    private Location baseWaterLocation2;

    private double waterHeight;
    private double maxWaterHeight;

    private double riseDistance;

    @Override
    public String getDisplayName() {
        return "水涨趴高";
    }

    private List<ArmorStand> listWaterSurface = new LinkedList<>();

    @Override
    public void onMapLoad() {
        final Location overview = readConfigLocation("spawn.overview");
        Bukkit.getOnlinePlayers().forEach(p -> {
            VanishAPI.hidePlayer(p);
            p.setGameMode(GameMode.SPECTATOR);
            p.setAllowFlight(true);
            p.setFlying(true);
            p.teleport(overview);
        });

        baseWaterLocation1 = readConfigLocation("water.pos1");
        baseWaterLocation2 = readConfigLocation("water.pos2");
        maxWaterHeight = configMap.getDouble("water.height");
        riseDistance = configMap.getDouble("water.rise-distance");

        World w = baseWaterLocation1.getWorld();
        double y = baseWaterLocation1.getY();
        for(double x = Math.min(baseWaterLocation1.getX(), baseWaterLocation2.getX()); x < Math.max(baseWaterLocation1.getX(), baseWaterLocation2.getX()); x += 7.0d) {
            for(double z = Math.min(baseWaterLocation1.getZ(), baseWaterLocation2.getZ()); z < Math.max(baseWaterLocation1.getZ(), baseWaterLocation2.getZ()); z += 7.0d) {
                final ArmorStand spawned = (ArmorStand) w.spawnEntity(new Location(w, x, y, z), EntityType.ARMOR_STAND);
                Bukkit.getScheduler().runTaskLater(FallGuys.getInstance(), () -> {
                    final ArmorStand patched = (ArmorStand) NBTInjector.patchEntity(spawned);
                    patched.setVisible(false);
                    patched.setBasePlate(false);
                    patched.setGravity(false);
                    patched.getEquipment().setHelmet(WATER_SURFACE);
                    listWaterSurface.add(patched);
                }, 1L);
            }
        }
        Bukkit.broadcastMessage(String.format("water surface amount: %d", listWaterSurface.size()));
    }

    @Override
    public void onGameStart() {
        final Location loc = readConfigLocation("spawn.game");
        FallGuys.getInstance().getPlayingPlayers().forEach(p -> {
            p.teleport(loc);

            p.setGameMode(GameMode.ADVENTURE);
            p.setFlying(false);
            p.setAllowFlight(false);
            VanishAPI.showPlayer(p);
        });
    }

    private int getRequiredFinishAmount() {
        final int online = FallGuys.getInstance().getPlayingPlayers().size();
        if(online > 10) {
            return online / 5 * 4;
        } else if (online >= 5) {
            return 4;
        } else if (online >= 3) {
            return 2;
        } else {
            return 1;
        }
    }

    @Override
    public boolean isGameFinished() {
        if(state.getFinishedCount() >= getRequiredFinishAmount()) return true;
        List<Player> playing = FallGuys.getInstance().getPlayingPlayers();
        return playing.isEmpty() || playing.stream().allMatch(p ->
            state.isPlayerEliminated(p) || state.isPlayerFinished(p) || !p.isOnline()
        );
    }

    @Override
    public void tick() {
        waterUpdate();
    }

    /**
     * 提高水
     */
    private void waterUpdate() {
        if(waterHeight >= maxWaterHeight) return;
        double baseY = baseWaterLocation1.getY();
        waterHeight += riseDistance;
        listWaterSurface.forEach(e -> {
            Location l = e.getLocation();
            l.setY(baseY + waterHeight);
            e.teleport(l);
        });
    }

    @Override
    public void update() {
    }

    @Override
    public void cleanUp() {
        listWaterSurface.forEach(Entity::remove);
        listWaterSurface.clear();
        listWaterSurface = null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        if(!state.isStarted()) {
            event.setCancelled(true);
            return;
        }
        if(state.isPlayerEliminated(event.getPlayer())) return;
        if(state.isEndedForPlayer(event.getPlayer())) return;

        Location location = event.getPlayer().getLocation();
        if(location.getY() <= baseWaterLocation1.getY() + waterHeight){
            // 玩家掉进水里，淘汰掉ta
            state.eliminatePlayer(event.getPlayer());
        }

        if(location.getBlock().getRelative(BlockFace.DOWN).getType() == Material.PURPUR_BLOCK) {
            // 玩家完成了地图！
            state.playerFinish(event.getPlayer());
        }
    }

    @Override
    protected String getConfigurationName() {
        return "water-climb";
    }

}
