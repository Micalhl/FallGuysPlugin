package net.mcfire.fallguys.states;

import de.myzelyam.api.vanish.VanishAPI;
import net.mcfire.fallguys.FallGuys;
import net.mcfire.fallguys.GameState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;

import java.util.List;

public class ResetState implements GameState {

    private final boolean ended;

    public ResetState(boolean ended) {
        this.ended = ended;
    }

    @Override
    public boolean onPlayerJoin(Player player) {
        return false;
    }

    @Override
    public void onEnterState() {
        Bukkit.broadcastMessage("开始重置世界... ");
        Location tp = FallGuys.getInstance().readConfigLocation("reset.location");
        Bukkit.getOnlinePlayers().forEach(p -> {
            VanishAPI.hidePlayer(p);
            p.setGameMode(GameMode.SPECTATOR);
            // p.setFlying(true);
            // p.setAllowFlight(true);
            p.teleport(tp);
        });
        Bukkit.getScheduler().runTaskLater(FallGuys.getInstance(), () -> {
            List<String> worlds = FallGuys.getInstance().getConfig().getStringList("reset.worlds");
            worlds.forEach(w -> {
                if(Bukkit.unloadWorld(w, false)) {
                    FallGuys.getInstance().getLogger().info(String.format("World %s unloaded! ", w));
                } else {
                    FallGuys.getInstance().getLogger().severe(String.format("World %s unload FAILED! ", w));
                }
            });
            Bukkit.getScheduler().runTaskLater(FallGuys.getInstance(), () -> {
                worlds.forEach(w -> {
                    Bukkit.createWorld(new WorldCreator(w));
                    Bukkit.getScheduler().runTaskLater(FallGuys.getInstance(), () -> {
                        if(ended) {
                            FallGuys.getInstance().enterState(new WaitState());
                        } else {
                            FallGuys.getInstance().enterState(new MatchState());
                        }
                    }, 20L);
                });
            }, 20L);
        }, 20L);
    }

}
