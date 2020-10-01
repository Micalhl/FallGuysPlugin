package net.mcfire.fallguys.states;

import de.myzelyam.api.vanish.VanishAPI;
import net.mcfire.fallguys.FallGuys;
import net.mcfire.fallguys.GameState;
import net.mcfire.fallguys.utils.ItemTools;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WaitState implements GameState {

    public static final ItemStack BUTTON_READY = ItemTools.item(Material.LIME_CONCRETE, "\u00a7a准备", true);
    public static final ItemStack BUTTON_NOT_READY = ItemTools.item(Material.RED_CONCRETE, "\u00a7a准备", true);
    private static final int MIN_PLAYER_LIMIT = 20;

    private BossBar bar = Bukkit.createBossBar(
        "\u00a7a等待玩家准备... ",
        BarColor.WHITE,
        BarStyle.SOLID
    );

    private Set<UUID> readyPlayers = new HashSet<>();

    private boolean started = false;

    private BukkitTask bgmTask = null;

    public boolean isStarted() {
        return started;
    }

    @Override
    public boolean onPlayerJoin(Player player) {
        if(started) {
            return false;
        }
        bar.addPlayer(player);
        VanishAPI.hidePlayer(player);
        prepareInventory(player);
        player.sendMessage("\u00a7a按下 \u00a7e\u00a7lF\u00a7a 键切换准备状态，房间内所有人准备好之后即可开始！");
        player.setGameMode(GameMode.ADVENTURE);
        Bukkit.getScheduler().runTaskLater(FallGuys.getInstance(), () -> {
            player.teleport(
                FallGuys.getInstance().readConfigLocation("wait-location")
            );
            player.setGameMode(GameMode.ADVENTURE);
            player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            player.setFoodLevel(20);
            player.setFlying(false);
            player.setAllowFlight(false);
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            player.stopSound("fallguys.theme");
            player.playSound(player.getLocation(), "fallguys.theme", SoundCategory.MASTER, 1.0f, 1.0f);

            setPlayerReady(player, true);

            VanishAPI.showPlayer(player);
        }, 10L);
        return true;
    }

    @Override
    public void onPlayerQuit(Player player) {
        bar.removePlayer(player);
        readyPlayers.remove(player.getUniqueId());
        VanishAPI.showPlayer(player);
        player.getInventory().clear();
    }

    private void prepareInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setItem(4, isPlayerReady(player) ? BUTTON_READY : BUTTON_NOT_READY);
        player.getInventory().setHeldItemSlot(4);
    }

    @Override
    public void onEnterState() {
        // process existing players
        Bukkit.getOnlinePlayers().forEach(this::onPlayerJoin);
        bgmTask = Bukkit.getScheduler().runTaskTimer(FallGuys.getInstance(), () -> {
            Bukkit.getOnlinePlayers().forEach(p -> {
                p.stopSound("fallguys.theme");
                p.playSound(p.getLocation(), "fallguys.theme", SoundCategory.MASTER, 1.0f, 1.0f);
            });
        }, 3960, 3960);
    }

    @Override
    public void onLeaveState() {
        bar.removeAll();
        bar = null;
        bgmTask.cancel();
        bgmTask = null;
        Bukkit.getOnlinePlayers().forEach((player) -> {
            player.removeMetadata("LastReady", FallGuys.getInstance());
            player.stopSound("fallguys.theme");
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFPressed(PlayerSwapHandItemsEvent event) {
        event.setCancelled(true);
        Player p = event.getPlayer();
        if (!isPlayerReady(p)) {
            setPlayerReady(p, !isPlayerReady(p));
        } else {
            event.getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("\u00a7c无法取消准备! "));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQPressed(PlayerDropItemEvent event) {
        event.setCancelled(true);
    }

    public boolean isPlayerReady(Player player) {
        return readyPlayers.contains(player.getUniqueId());
    }

    public void setPlayerReady(Player player, boolean ready) {
        if(ready) {
            if (player.hasMetadata("LastReady")) {
                long last = (long) player.getMetadata("LastReady").get(0).value();
                if (System.currentTimeMillis() - last <= 5000) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("\u00a7c准备冷却中... "));
                    return;
                }
            }
            player.setMetadata("LastReady", new FixedMetadataValue(FallGuys.getInstance(), System.currentTimeMillis()));
        }
        if(ready) {
            if(started) return;
            readyPlayers.add(player.getUniqueId());
            int onlineCount = Bukkit.getOnlinePlayers().size();
            int readyCount = readyPlayers.size();
            if ((readyCount >= onlineCount && onlineCount >= MIN_PLAYER_LIMIT)) {
                if(started) return;
                doStartGame();
            } else {
                updateBossbar(player);
                prepareInventory(player);
                Bukkit.getOnlinePlayers().forEach(_p -> _p.playSound(_p.getLocation(), "fallguys.ready.random", SoundCategory.MASTER, 1.0f, 1.0f));
            }
        } else {
            Bukkit.broadcastMessage(String.format("\u00a7b%s \u00a7c取消了准备! ", player.getName()));
            readyPlayers.remove(player.getUniqueId());
        }

        prepareInventory(player);
    }

    private void updateBossbar(Player player) {
        int onlineCount = Bukkit.getOnlinePlayers().size();
        int readyCount = readyPlayers.size();
        String t = String.format("\u00a7b%s \u00a7e已经准备好，还差%d人准备即可开始比赛！",
            player == null ? "" : player.getName(),
            onlineCount > MIN_PLAYER_LIMIT ? (onlineCount - readyCount) : MIN_PLAYER_LIMIT-readyCount
        );
        if(bar != null) {
            bar.setTitle(t);
        }
        Bukkit.broadcastMessage(t);
    }

    public void doStartGame() {
        if(started) return;
        Bukkit.broadcastMessage("比赛开始! ");
        bar.setTitle("\u00a7b\u00a7l比赛开始");
        started = true;

        FallGuys.getInstance().resetPlaying();

        Bukkit.getScheduler().runTaskLater(FallGuys.getInstance(), () -> {
            Bukkit.getOnlinePlayers().forEach(p -> {
                p.getInventory().clear();
            });
            FallGuys.getInstance().enterState(new MatchState());
        }, 40L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSlotChange(PlayerItemHeldEvent event) {
        if(event.getNewSlot() != 4) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        event.getPlayer().removeMetadata("LastReady", FallGuys.getInstance());
        readyPlayers.remove(event.getPlayer().getUniqueId());
    }

    private String[] texts = {
        "等待所有玩家准备好... ",
        "按使用物品键抢尾巴（客户端默认左右键交换，所以是左键）",
        "游戏中按 2 键飞扑！"
    };
    private int textIndex = 0;

    @Override
    public void run() {
        Bukkit.getOnlinePlayers().forEach(p -> {
            p.sendTitle("正在拉人", texts[textIndex], 5, 25, 5);
        });
        textIndex ++;
        textIndex %= texts.length;
    }
}
