package net.mcfire.fallguys.maps;

import de.myzelyam.api.vanish.VanishAPI;
import net.mcfire.fallguys.FallGuys;
import net.mcfire.fallguys.states.MatchState;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

/**
 * -- CONFIG --
 * doors:
 *   spawn:
 *     overview: world,-53,238,116,0,28
 *     game: world,-53,216,114
 *   rows: 6
 *   length-per-door: 20
 *   door:
 *     count: 6
 *     # 1 pillar + 2 frame + 5 door way
 *     width: 8
 *     height: 6
 *   # door goes in positive Z!!!
 *   start: world,-77,215,108
 */
public class DoorsMap extends BaseMap {

    private static final String META_DOOR_FALLING_BLOCK = "FallGuys::Match::DoorsMap::DoorFallingBlock";

    private Material materialDoor;

    private List<BoundingBoxWithWorld> doorBoxes = null;

    public DoorsMap(MatchState state) {
        super(state);
    }

    @Override
    public String getDisplayName() {
        return "闯门冲关";
    }

    @Override
    public void onMapLoad() {
        Location overviewLocation = readConfigLocation("spawn.overview");
        Bukkit.getOnlinePlayers().forEach(p -> {
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(overviewLocation);
        });

        List<Integer> rowOpenCount = configMap.getIntegerList("open-count");
        int totalOpenCount = rowOpenCount.stream().mapToInt(Integer::valueOf).sum();
        doorBoxes = new ArrayList<>(totalOpenCount);

        List<Integer> doorSize = configMap.getIntegerList("door-size");
        final int doorSizeX = doorSize.get(0);
        final int doorSizeY = doorSize.get(1);
        final int doorSizeZ = doorSize.get(2);

        ConfigurationSection doorsSection = configMap.getConfigurationSection("locations");
        final Set<String> doorKeys = doorsSection.getKeys(false);
        final int rows = doorKeys.size();
        // load doors
        materialDoor = Material.valueOf(configMap.getString("material")); // 门的材质
        final Random rnd = new Random(System.currentTimeMillis() - (Bukkit.getOnlinePlayers().size() * FallGuys.getInstance().getPlayingPlayers().size()));

        List<Location> locations = new ArrayList<>(8);
        for(int row = 0; row < rows; row++) {
            locations.clear(); // 清理一下缓存
            String key = String.format("row-%d", row);
            List<String> locationsString = doorsSection.getStringList(key); // 门的坐标列表
            final int openCount = rowOpenCount.get(row);
            locationsString.forEach(str -> locations.add(FallGuys.readLocationFromString(str)));
            for(int i = 0; i < openCount; i ++) { // 随即确定哪个门是打开的
                int index = rnd.nextInt(locations.size());
                Location doorStartLocation = locations.remove(index);
                BoundingBox bbox = new BoundingBox(
                    doorStartLocation.getX(), doorStartLocation.getY(), doorStartLocation.getZ(),
                    doorStartLocation.getX() + doorSizeX, doorStartLocation.getY() + doorSizeY, doorStartLocation.getZ() + doorSizeZ
                    );
                doorBoxes.add(new BoundingBoxWithWorld(doorStartLocation.getWorld(), bbox));
                Bukkit.broadcastMessage("bbox => " + bbox.toString());
                /*final World world = doorStartLocation.getWorld();
                final int startX = doorStartLocation.getBlockX();
                final int startY = doorStartLocation.getBlockY();
                final int startZ = doorStartLocation.getBlockZ();
                for(int x = startX; x < startX + doorSizeX; x ++) {
                    for(int y = startY; y < startY + doorSizeY; y ++) {
                        for(int z = startZ; z < startZ + doorSizeZ; z ++) {
                            Location blockLocation = new Location(world, x, y, z);
                            if(blockLocation.getBlock().getType() == materialDoor) {
                                FallingBlock fall = world.spawnFallingBlock(blockLocation.add(.5d, 0d, .5d), materialDoor.createBlockData());
                                fall.setGravity(false);
                                fall.setInvulnerable(true);
                                fall.setSilent(true);
                                fall.setDropItem(false);
                                fall.setHurtEntities(false);

                                fallingBlocks.add(fall);
                                blocksToRemove.add(blockLocation.getBlock());
                            }
                        }
                    }
                }*/
            }
        }
    }

    @Override
    public void onGameStart() {
        final Location loc = readConfigLocation("spawn.game");
        Bukkit.getOnlinePlayers().forEach(p -> {
            if(FallGuys.getInstance().isPlayerPlaying(p)) {
                p.setGameMode(GameMode.ADVENTURE);
                p.setFlying(false);
                p.setAllowFlight(false);
                p.setNoDamageTicks(100);
                VanishAPI.showPlayer(p);
                p.removePotionEffect(PotionEffectType.BLINDNESS);
                p.sendMessage("\u00a7a比赛开始, 先冲到终点的可以避免被淘汰哦~ ");
            }
            p.teleport(loc);
        });
    }

    private int getRequiredPlayersWon() {
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
        return state.getFinishedCount() >= getRequiredPlayersWon();
    }

    @Override
    public void update() {
        state.getBar().setTitle(String.format("\u00a7e\u00a7l五花八门 \u00a7a通关人数 %d / %d", state.getFinishedCount(), getRequiredPlayersWon()));
    }

    @Override
    public void cleanUp() {
        doorBoxes.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        if(!state.isStarted()) {
            event.setCancelled(true);
            return;
        }
        Player p = event.getPlayer();
        if(state.isEndedForPlayer(p)) return;

        // 检测玩家冲到了终点
        if(event.getTo() != null && event.getTo().getBlock().getRelative(BlockFace.DOWN).getType() == Material.LIME_CONCRETE) {
            state.playerFinish(p);
            return;
        }

        // 冲向打开的门
        Vector playerDirection = p.getEyeLocation().getDirection();
        Vector vec = event.getTo().clone().add(0,1.62d,0).add(playerDirection).toVector();
        doorBoxes.removeIf(door -> {
            if(door.boundingBox.contains(vec)) {
                // 冲♂到了门
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("\u00a7l\u00a7l你撞开了一扇门！"));
                removeDoor(playerDirection, door);
                Location loc = p.getEyeLocation();
                loc.getWorld().playSound(loc, "fallguys.football_kick", SoundCategory.AMBIENT, 1.0f, 1.0f);
                loc.getWorld().spawnParticle(
                    Particle.EXPLOSION_LARGE,
                    loc, 1
                );
                p.setVelocity(playerDirection.clone().multiply(-.2).setY(.2d));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 1, true, false));
                return true;
            }
            return false;
        });
    }

    private void removeDoor(Vector playerDirection, BoundingBoxWithWorld door) {
        final World w = door.world;
        final BoundingBox bb = door.boundingBox;
        Random random = new Random();
        for(int x = (int) bb.getMinX(); x < bb.getMaxX(); x++) {
            for(int y = (int) bb.getMinY(); y < bb.getMaxY(); y++) {
                for(int z = (int) bb.getMinZ(); z < bb.getMaxZ(); z++) {
                    Block b = w.getBlockAt(x, y, z);
                    if(b.getType() != materialDoor) continue;
                    b.setType(Material.AIR);

                    FallingBlock fall = w.spawnFallingBlock(b.getLocation().add(.5d, 0d, .5d), materialDoor.createBlockData());
                    fall.setGravity(true);
                    fall.setSilent(true);
                    fall.setDropItem(false);
                    fall.setHurtEntities(false);
                    fall.setMetadata(META_DOOR_FALLING_BLOCK, new FixedMetadataValue(FallGuys.getInstance(), true));

                    Vector direction = playerDirection.clone();
                    direction.setY(.3d);
                    direction.setX(direction.getX() + (random.nextDouble()*.2d));
                    direction.setY(direction.getY() + (random.nextDouble()*.1d));
                    direction.setZ(direction.getZ() + (random.nextDouble()*.2d));
                    fall.setVelocity(direction);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFallingBlockForm(EntityChangeBlockEvent event) {
        if(event.getEntity().hasMetadata(META_DOOR_FALLING_BLOCK) && event.getTo() == materialDoor) {
            // 三秒左右删掉这个方块
            Location blockLocation = event.getBlock().getLocation();
            Random rnd = new Random();
            long delay = 40 + (rnd.nextBoolean()?1:-1) * rnd.nextInt(10);
            Bukkit.getScheduler().runTaskLater(FallGuys.getInstance(), () -> {
                Block block = blockLocation.getBlock();
                if(block.getType() == materialDoor) {
                    block.setType(Material.AIR);
                }
            }, delay);
        }
    }

    @Override
    protected String getConfigurationName() {
        return "doors";
    }

    private static class BoundingBoxWithWorld {
        public final World world;
        public final BoundingBox boundingBox;

        public BoundingBoxWithWorld(World world, BoundingBox boundingBox) {
            this.world = world;
            this.boundingBox = boundingBox;
        }
    }
}
