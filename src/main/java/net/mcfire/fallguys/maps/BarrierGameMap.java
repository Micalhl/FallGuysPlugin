package net.mcfire.fallguys.maps;

import de.myzelyam.api.vanish.VanishAPI;
import io.github.definitlyevil.bukkitces.CustomEntityRegister;
import net.mcfire.fallguys.FallGuys;
import net.mcfire.fallguys.cef.PinkBarrier;
import net.mcfire.fallguys.states.MatchState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

public class BarrierGameMap extends BaseMap {

    private boolean started = false;

    public BarrierGameMap(MatchState state) {
        super(state);
    }

    @Override
    public String getDisplayName() {
        return "障碍狂欢";
    }

    @Override
    public void onMapLoad() {
        Location overview = readConfigLocation("spawn.overview");
        spawnCustomEntitiesFromConfig("barriers", PinkBarrier.TYPE);
        Bukkit.getOnlinePlayers().forEach(p -> {
            p.teleport(overview);
        });
    }

    @Override
    public void onGameStart() {
        started = true;
        Location spawn = readConfigLocation("spawn.game");
        Bukkit.getOnlinePlayers().forEach(p -> {
            if(FallGuys.getInstance().isPlayerPlaying(p)) {
                p.setNoDamageTicks(100);
                VanishAPI.showPlayer(p);
            } else {
                p.setGameMode(GameMode.SPECTATOR);
                p.sendTitle(" ", "\u00a77你处于观战模式", 10, 20, 10);
            }
            p.teleport(spawn);
        });
        FallGuys.getInstance().getPlayingPlayers().forEach(p -> {
            p.teleport(spawn);
        });
    }

    private int getRequiredPlayersWon() {
        final int online = FallGuys.getInstance().getPlayingPlayers().size();
        if(online > 10) {
            return online / 3;
        } else if (online >= 3) {
            return 3;
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

    }

    @Override
    public void cleanUp() {

    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        if(!FallGuys.getInstance().isPlayerPlaying(event.getPlayer())) return;
        if(!started) event.setCancelled(true);
        if(!state.isEndedForPlayer(event.getPlayer()) && event.getTo().getBlock().getRelative(BlockFace.DOWN).getType() == Material.LIME_CONCRETE) {
            Player p = event.getPlayer();
            state.playerFinish(p);
        }
    }

    @Override
    protected String getConfigurationName() {
        return "barrier-game";
    }

}
