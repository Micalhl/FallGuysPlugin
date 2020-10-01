package net.mcfire.fallguys.maps;

import de.myzelyam.api.vanish.VanishAPI;
import net.mcfire.fallguys.FallGuys;
import net.mcfire.fallguys.states.MatchState;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class CatchTailsMap extends BaseMap {
    private static final String TAIL_META = "FallGuys::CatchTails::Tail::Meta";
    private static final ItemStack TAIL_ITEM = new ItemStack(Material.GOLD_BLOCK);

    private static final ItemStack TAIL_MODEL = new ItemStack(Material.GOLDEN_HOE);
    static {
        ItemMeta m = TAIL_MODEL.getItemMeta();
        m.setCustomModelData(5);
        TAIL_MODEL.setItemMeta(m);
    }

    public CatchTailsMap(MatchState state) {
        super(state);
    }

    private int timer = 90;

    private Set<UUID> tails = new HashSet<>();

    @Override
    public String getDisplayName() {
        return "追“尾”游戏";
    }

    @Override
    public void onMapLoad() {
        final Location overview = readConfigLocation("spawn.overview");

        Bukkit.getOnlinePlayers().forEach(p -> {
            VanishAPI.hidePlayer(p);
            p.teleport(overview);
            p.setGameMode(GameMode.SPECTATOR);

            p.removeMetadata(TAIL_META, FallGuys.getInstance());
        });

        List<Player> pool = FallGuys.getInstance().getPlayingPlayers();
        final int tails = pool.size() >= 5 ? (pool.size() / 5 * 3) : 1;
        Random rnd = new Random(System.currentTimeMillis() - pool.size());
        for(int i = 0; i < tails; i++) {
            if(pool.size() <= 0) break;
            int index = rnd.nextInt(pool.size());
            Player target = pool.remove(index);
            target.sendMessage("\u00a7a你被选中为初始尾巴玩家! ");
            addTail(target);
        }

        pool.clear();

        state.setEliminateUnfinished(false); // VERY IMPORTANT
    }

    private void addTail(Player target) {
        if(target.hasMetadata(TAIL_META)) return;
        tails.add(target.getUniqueId());
        target.getInventory().setHelmet(TAIL_MODEL);
        target.getInventory().setItem(4, TAIL_MODEL);
    }

    @Override
    public void onGameStart() {
        final Location locTails = readConfigLocation("spawn.game.tails");
        final Location locTailless = readConfigLocation("spawn.game.tailless");
        Bukkit.getOnlinePlayers().forEach(p -> {
            if(FallGuys.getInstance().isPlayerPlaying(p)) {
                if (tails.contains(p.getUniqueId())) {
                    p.teleport(locTails);
                    p.sendTitle("\u00a7e你一开始就有尾巴了！", "\u00a7d不要被别人抢走哦~ ", 20, 80, 20);
                } else {
                    p.teleport(locTailless);
                    p.sendTitle("\u00a7b你一开始没有尾巴！", "\u00a7d快去抢别人的尾巴咯~ ", 20, 80, 20);
                }
                p.setFlying(false);
                p.setAllowFlight(false);
                p.setGameMode(GameMode.ADVENTURE);
                VanishAPI.showPlayer(p);
            } else {
                p.setGameMode(GameMode.SPECTATOR);
                p.teleport(locTails);
            }
        });
    }

    @Override
    public boolean isGameFinished() {
        if(timer <= 0) {
            // eliminate
            FallGuys.getInstance().getPlayingPlayers().forEach(p -> {
                if(!tails.contains(p.getUniqueId())) {
                    state.eliminatePlayer(p);
                }
            });
            return true;
        }
        return false;
    }

    @Override
    public void update() {
        timer --;
        state.getBar().setTitle(String.format("\u00a7e追“尾”游戏 \u00a7c倒计时 %02d:%02d", timer / 60, timer % 60));
    }

    @Override
    public void cleanUp() {
        tails.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if(tails.contains(p.getUniqueId())) {
            tails.remove(p.getUniqueId());
            // TODO: drop tail
            p.getWorld().dropItemNaturally(p.getLocation(), TAIL_MODEL);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityTouch(PlayerInteractAtEntityEvent event) {
        if(!FallGuys.getInstance().isPlayerPlaying(event.getPlayer())) return;
        if(!Player.class.isAssignableFrom(event.getRightClicked().getClass())) return;
        if(tails.contains(event.getPlayer().getUniqueId())) {
            event.getPlayer().sendMessage("\u00a7d你已经有尾巴了！");
            return;
        }

        Player target = (Player) event.getRightClicked();
        if(target.getLocation().distanceSquared(event.getPlayer().getLocation()) > 2*2) {
            event.getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("\u00a7e太远啦，两格以内才能抢到！"));
            return;
        }

        event.setCancelled(true);

        if(!tails.contains(target.getUniqueId())) return;
        target.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("\u00a7d你的尾巴被抢走啦！"));
        tails.remove(target.getUniqueId());
        target.getInventory().setItem(5, null);
        target.getInventory().setHelmet(null);

        addTail(event.getPlayer());

        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MASTER, 1.0f, 1.0f);
        Bukkit.broadcastMessage(String.format("%s 抢到了尾巴! ", event.getPlayer().getName()));

        event.getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("\u00a7a你抢到了尾巴!!! "));
    }

    @Override
    protected String getConfigurationName() {
        return "catch-tails";
    }

}
