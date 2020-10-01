package net.mcfire.fallguys.states;

import com.comphenix.protocol.ProtocolLibrary;
import de.myzelyam.api.vanish.VanishAPI;
import net.mcfire.fallguys.FallGuys;
import net.mcfire.fallguys.GameState;
import net.mcfire.fallguys.MapSelector;
import net.mcfire.fallguys.maps.CrownMap;
import net.mcfire.fallguys.maps.DoorsMap;
import net.mcfire.fallguys.maps.FallGuysMap;
import net.mcfire.fallguys.maps.FootbalMap;
import net.mcfire.fallguys.utils.BGM;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.BlockFace;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

public class MatchState implements GameState {
    private static final String META_LAST_ZHAZONG = "FallGuys::ZhaZong";
    private static final long ZHAZONG_COOLDOWN = 500L;

    private FallGuysMap map = null;

    private BossBar bar = Bukkit.createBossBar(" ", BarColor.WHITE, BarStyle.SOLID);

    private boolean started = false;
    private boolean ended = false;
    private int countdown = 10;

    private Set<UUID> finished = new HashSet<>();
    private Set<UUID> eliminated = new HashSet<>();

    private int initialPlayerCount;

    private BukkitTask ticker = null;

    private BukkitTask taskAmbientLoop; // 背景噪音的循环

    /**
     * 是否淘汰没有完成的玩家？
     */
    private boolean eliminateUnfinished = true;

    @Override
    public void onEnterState() {
        Bukkit.broadcastMessage("\u00a7a正在随机选择地图... ");

        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        initialPlayerCount = onlinePlayers.size();
        onlinePlayers.forEach(p -> {
            p.setGameMode(GameMode.SPECTATOR);
            p.setAllowFlight(true);
            p.setFlying(true);
            p.setNoDamageTicks(100);
            p.setHealth(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            p.getActivePotionEffects().forEach(pot -> p.removePotionEffect(pot.getType()));
            bar.addPlayer(p);
            p.setCollidable(true);
            p.getInventory().clear();
            p.getInventory().setHeldItemSlot(0);
            p.getInventory().setItem(0, new ItemStack(Material.REDSTONE));
            p.getInventory().setItem(1, new ItemStack(Material.ARROW));
            VanishAPI.hidePlayer(p);
            if(!FallGuys.getInstance().isPlayerPlaying(p)) {
                p.sendMessage("\u00a7e\u00a7l你处于观战模式! ");
            }
        });
        taskAmbientLoop = Bukkit.getScheduler().runTaskTimer(FallGuys.getInstance(), () -> {
            Bukkit.getOnlinePlayers().forEach(p -> {
                p.playSound(p.getLocation(), "fallguys.ambient", SoundCategory.MASTER, 1.0f, 1.0f);
            });
        }, 20, 54 * 20);

        // map = new DoorsMap(this);
        // map = new JumpNFallMap(this);
        // map = new BarrierGameMap(this);
        // map = new CatchTailsMap(this);
        // map = new WaterClimbMap(this);
        // map = new FootbalMap(this);
        selectMap();
        map.onMapLoad();

        FallGuys.playSoundForAll("fallguys.intro", SoundCategory.MASTER);

        Bukkit.getPluginManager().registerEvents(
            map, FallGuys.getInstance()
        );

        FallGuys.getInstance().getLogger().info("Registering packet listener... ");
        ProtocolLibrary.getProtocolManager().addPacketListener(map);
    }

    private void selectMap() {
        if(FallGuys.getInstance().getPlayingPlayers().size() > 3) {
            try {
                map = MapSelector.randomMap(this);
            } catch (Exception ex) {
                ex.printStackTrace();
                Bukkit.broadcastMessage("地图选择失败! ");
                FallGuys.getInstance().enterState(new WaitState());
                return;
            }
        } else {
            map = new CrownMap(this);
        }
    }

    public int getInitialPlayerCount() {
        return initialPlayerCount;
    }

    public boolean isStarted() {
        return started;
    }

    @Override
    public void onLeaveState() {
        HandlerList.unregisterAll(map);
        map.cleanUp();
        ProtocolLibrary.getProtocolManager().removePacketListener(map);
        map = null;

        bar.removeAll();
        bar = null;

        taskAmbientLoop.cancel();
        Bukkit.getOnlinePlayers().forEach(p -> p.stopSound("fallguys.ambient"));

        FallGuys.getInstance().getLogger().info("Unregistering packet listener... ");
    }

    public BossBar getBar() {
        return bar;
    }

    @Override
    public void run() {
        if(map == null) return;
        finished.removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        if(!started) {
            if(countdown <= 0) {
                started = true;
                map.onGameStart();
                ticker = Bukkit.getScheduler().runTaskTimer(FallGuys.getInstance(), this::tick, 0L, 1L);
                Bukkit.getOnlinePlayers().forEach(p -> {
                    if(FallGuys.getInstance().isPlayerPlaying(p)) {
                        p.setGameMode(GameMode.ADVENTURE);
                        p.setFlying(false);
                        p.setAllowFlight(false);
                        VanishAPI.showPlayer(p);
                        p.removePotionEffect(PotionEffectType.INVISIBILITY);
                    } else {
                        p.sendMessage("\u00a77你处于观战模式! ");
                        p.setGameMode(GameMode.SPECTATOR);
                        p.setAllowFlight(true);
                        p.setFlying(true);
                    }
                    p.playSound(p.getLocation(), BGM.COURSE[new Random(System.currentTimeMillis() / FallGuys.getInstance().getPlayingPlayers().size()).nextInt(BGM.COURSE.length)], SoundCategory.MASTER, .5f, 1.0f);
                    p.playSound(p.getLocation(), "fallguys.course.start", SoundCategory.MASTER, 1.0f, 1.0f);
                });
            } else {
                Bukkit.getOnlinePlayers().forEach(p -> {
                    p.sendTitle(map.getDisplayName(), String.format("\u00a7f\u00a7l%d", countdown), 5, 20, 5);
                    p.playSound(p.getLocation(), "fallguys.course.countdown", SoundCategory.MASTER, 1.0f, 1.0f);
                });
                countdown--;
            }
        } else {
            if(!ended) {
                map.update();
                eliminated.removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
                if (map.isGameFinished()) {
                    // game is finished!
                    ended = true;
                    countdown = 3;
                    ticker.cancel();
                    ticker = null;
                    Bukkit.getOnlinePlayers().forEach(p -> {
                        p.setAllowFlight(true);
                        p.setGameMode(GameMode.SPECTATOR);
                        p.sendTitle("\u00a7a本回合结束", "", 20, 40, 20);
                        for(String n : BGM.COURSE) p.stopSound(n);
                        p.playSound(p.getLocation(), "fallguys.course.ended", SoundCategory.MASTER, 1.0f, 1.0f);
                    });
                }
            } else {
                countdown --;
                if(eliminateUnfinished) {
                    FallGuys.getInstance().getPlayingPlayers().forEach(p -> {
                        UUID uuid = p.getUniqueId();
                        if (!eliminated.contains(uuid) && !finished.contains(uuid)) {
                            eliminatePlayer(p);
                        }
                    });
                } else {
                    FallGuys.getInstance().getPlayingPlayers().forEach(p -> {
                        UUID uuid = p.getUniqueId();
                        if (!eliminated.contains(uuid) && !finished.contains(uuid)) {
                            playerFinish(p);
                        }
                    });
                }
                if(finished.size() == 1) {
                    FallGuys.getInstance().enterState(new WinnerState(finished.toArray(new UUID[1])[0]));
                } else {
                    FallGuys.getInstance().enterState(new EliminateState(eliminated));
                }
            }
        }
    }

    public void setEliminateUnfinished(boolean eliminateUnfinished) {
        this.eliminateUnfinished = eliminateUnfinished;
    }

    public void playerFinish(Player p) {
        if(finished.add(p.getUniqueId())) {
            p.setGameMode(GameMode.SPECTATOR);
            p.sendTitle("\u00a7b\u00a7l达标啦！", "\u00a7bQualified! ", 10, 40, 10);
            p.playSound(p.getLocation(), "fallguys.course.qualified", SoundCategory.MASTER, 1.0f, 1.0f);
            p.playSound(p.getLocation(), "fallguys.course.win", SoundCategory.MASTER, 1.0f, 1.0f);
            p.getInventory().clear();
        }
    }

    public boolean isPlayerFinished(Player p) {
        return finished.contains(p.getUniqueId());
    }

    public int getFinishedCount() { return finished.size(); }

    public int getEliminatedCount() {
        return eliminated.size();
    }

    public void eliminatePlayer(Player p) {
        if(eliminated.add(p.getUniqueId())) {
            p.sendTitle("\u00a7c\u00a7l出局啦! ", "Eliminated! ", 20, 40, 20);
            p.playSound(p.getLocation(), "fallguys.course.eliminated", SoundCategory.MASTER, 1.0f, 1.0f);
            p.playSound(p.getLocation(), "fallguys.course.lose", SoundCategory.MASTER, 1.0f, 1.0f);
            p.getInventory().clear();
            p.setGameMode(GameMode.SPECTATOR);
            p.setAllowFlight(true);
            p.setFlying(true);
        }
    }

    public boolean isPlayerEliminated(Player p) {
        return eliminated.contains(p.getUniqueId());
    }

    public boolean isEndedForPlayer(Player p) {
        return !FallGuys.getInstance().isPlayerPlaying(p) || isPlayerFinished(p) || isPlayerEliminated(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDropItem(PlayerDropItemEvent event) {
        event.setCancelled(true);
        map.onPressQ(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemSlotChange(PlayerItemHeldEvent event) {
        event.setCancelled(true);
        if(event.getNewSlot() == 1) {
            // 飞扑
            processSwoop(event.getPlayer());
        }
    }

    /**
     * 每个tick执行一次!
     * 1. 闸总游戏逻辑
     * 2. 触发map里边的tick
     */
    private void tick() {
        Bukkit.getOnlinePlayers().forEach(this::processZhaZong);

        map.tick();
    }

    private static final double DEG90_TO_RAD = 1.57079637d;
    /**
     * 当玩家按下了 F 键，开始扒拉人
     * @param event
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void doZhaZong(PlayerSwapHandItemsEvent event) {
        if(isEndedForPlayer(event.getPlayer())) return;
        Player p = event.getPlayer();
        if(isZhaZongCoolingDown(p)) {
            p.sendMessage("\u00a7c闸总功能冷却中... ");
            return;
        }

        Player hit = rayTraceTargetPlayer(p);
        if(hit == null) return;

        // 保存闸总冷却记录
        p.setMetadata(META_LAST_ZHAZONG, new FixedMetadataValue(FallGuys.getInstance(), System.currentTimeMillis()));

        // 获取扒拉人的方向
        Random rnd = new Random();
        Vector dir = p.getLocation().getDirection();
        dir.rotateAroundY((rnd.nextBoolean() ? 1d:-1d) * DEG90_TO_RAD);

        dir.normalize().multiply(.8);
        dir.setY(.4d);

        hit.setVelocity(dir);
        hit.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("\u00a76你被 \u00a7c%s \u00a76扒拉了一下"));
    }

    private static boolean isZhaZongCoolingDown(Player p) {
        // 检测冷却
        if(p.hasMetadata(META_LAST_ZHAZONG)) {
            long last = (long) p.getMetadata(META_LAST_ZHAZONG).get(0).value();
            long diff = System.currentTimeMillis() - last;
            if(diff < ZHAZONG_COOLDOWN) {
                return true;
            }
        }
        return false;
    }

    private void processZhaZong(Player p) {
        if(isZhaZongCoolingDown(p)) return;
        Player hit = rayTraceTargetPlayer(p);
        if(hit == null) return;
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(String.format("\u00a7d按 \"\u00a7eF\u00a7d\" 键扒拉 \u00a7c%s", hit)));
    }

    private static Player rayTraceTargetPlayer(Player from) {
        Location loc = from.getEyeLocation();
        RayTraceResult result = from.getWorld().rayTraceEntities(loc, loc.getDirection(), 1.6d, (e) -> e!=from && e.getType() == EntityType.PLAYER && !e.hasMetadata("npc"));
        if(result == null || result.getHitEntity() == null) return null;
        return (Player) result.getHitEntity();
    }

    /**
     * 尝试使用飞扑
     * @param p
     */
    private static final String META_SWOOP_PREPARE = "FallGuys::Swoop::Prepare";
    private static final String META_SWOOP_IN_AIR = "FallGuys::Swoop::InAir";
    private static final String META_SWOOP_LOCKED = "FallGuys::Swoop::Locked";
    private void processSwoop(Player p) {
        if (!FallGuys.getInstance().isPlayerPlaying(p)) return;
        if (p.hasMetadata(META_SWOOP_PREPARE) || p.hasMetadata(META_SWOOP_IN_AIR)) return;
        Location at = p.getLocation();
        Vector dir = at.getDirection();
        dir.multiply(.6d);
        dir.setY(0.2d);
        p.setVelocity(dir);

        p.setMetadata(META_SWOOP_PREPARE, new FixedMetadataValue(FallGuys.getInstance(), true));
        Bukkit.getScheduler().runTaskLater(FallGuys.getInstance(), () -> {
            p.setMetadata(META_SWOOP_IN_AIR, new FixedMetadataValue(FallGuys.getInstance(), true));
        }, 10L);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void swoopOnPlayerMove(PlayerMoveEvent event) {
        if(event.getTo() == null) return;
        Player p = event.getPlayer();
        if(!p.hasMetadata(META_SWOOP_IN_AIR)) return;
        if((!event.getTo().getBlock().isPassable()) || (!event.getTo().getBlock().getRelative(BlockFace.DOWN).isPassable() && event.getTo().getY() - event.getTo().getBlockY() < 0.01d)) {
            p.sendMessage("飞扑: 已经落地，你摔到了");
            p.setMetadata(META_SWOOP_LOCKED, new FixedMetadataValue(FallGuys.getInstance(), true));
            Bukkit.getScheduler().runTaskLater(FallGuys.getInstance(), () -> {
                if(p.isOnline()) p.sendMessage("飞扑: 你站起来了");
                clearSwoop(p);
            }, 20);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void swoopOnPlayerMoveLock(PlayerMoveEvent event) {
        if(event.getPlayer().hasMetadata(META_SWOOP_LOCKED)) event.setCancelled(true);
    }
    @EventHandler
    public void swoopOnPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        clearSwoop(p);
    }
    public void clearSwoop(Player p) {
        p.removeMetadata(META_SWOOP_PREPARE, FallGuys.getInstance());
        p.removeMetadata(META_SWOOP_IN_AIR, FallGuys.getInstance());
        p.removeMetadata(META_SWOOP_LOCKED, FallGuys.getInstance());
    }

}
