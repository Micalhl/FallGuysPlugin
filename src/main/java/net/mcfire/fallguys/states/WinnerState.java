package net.mcfire.fallguys.states;

import de.myzelyam.api.vanish.VanishAPI;
import net.mcfire.fallguys.FallGuys;
import net.mcfire.fallguys.GameState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.UUID;

public class WinnerState implements GameState {

    private final UUID uuid;
    private final OfflinePlayer player;

    private BossBar bar = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID);

    public WinnerState(UUID uuid) {
        this.uuid = uuid;
        player = Bukkit.getOfflinePlayer(uuid);
        if(player.isOnline()) {
            Player winner = (Player) player;
            winner.sendTitle("\u00a7e恭喜夺冠! ", "\u00a76Winner winner chicken dinner! ", 10, 20, 10);
        }
    }

    private int countdown = 15;

    @Override
    public boolean onPlayerJoin(Player player) {
        return false;
    }

    @Override
    public void run() {
        if(countdown <= 0) {
            FallGuys.getInstance().enterState(new ResetState(true));
            return;
        }
        countdown --;
        bar.setTitle(String.format("\u00a7b距离下一局开始 \u00a7e%d \u00a7b秒", countdown));
    }

    @Override
    public void onEnterState() {
        final Location loc = FallGuys.getInstance().readConfigLocation("award.location");
        Bukkit.getOnlinePlayers().forEach(p -> {
            VanishAPI.hidePlayer(p);
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(loc);
            bar.addPlayer(p);
        });

        // 播放声音
        FallGuys.playSoundForAll("fallguys.win.bgm");
        Bukkit.getScheduler().runTaskLater(FallGuys.getInstance(), () -> FallGuys.playSoundForAll("fallguys.win.sound"), 20L);

        Bukkit.getScheduler().runTaskLater(FallGuys.getInstance(), () -> {
            Bukkit.getOnlinePlayers().forEach(p -> {
                if(!uuid.equals(p.getUniqueId())) {
                    p.sendTitle("\u00a7d\u00a7l下次加油! ", " ", 10, 40, 10);
                }
            });
        }, 40L);
    }

    @Override
    public void onLeaveState() {
        bar.removeAll();
        bar = null;
    }
}
