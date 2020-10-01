package net.mcfire.fallguys.maps;

import de.myzelyam.api.vanish.VanishAPI;
import io.github.definitlyevil.bukkitces.CustomEntityRegister;
import net.mcfire.fallguys.FallGuys;
import net.mcfire.fallguys.cef.PinkBarrier;
import net.mcfire.fallguys.states.MatchState;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;

public class CrownMap extends BaseMap {
    public CrownMap(MatchState state) {
        super(state);
    }

    private Location crownLocation;
    private double crownRadiusSqr;

    private UUID finished = null;

    @Override
    public String getDisplayName() {
        return "登山比拼";
    }

    @Override
    public void onMapLoad() {
        crownLocation =readConfigLocation("crown.location");
        crownRadiusSqr = configMap.getDouble("crown.radius");
        crownRadiusSqr *= crownRadiusSqr; // radius squared!!!
        Location loc = readConfigLocation("spawn.overview");
        spawnCustomEntitiesFromConfig("barriers", PinkBarrier.TYPE);
        Bukkit.getOnlinePlayers().forEach(p -> {
            p.teleport(loc);
        });
    }

    @Override
    public void onGameStart() {
        Location loc = readConfigLocation("spawn.game");
        Bukkit.getOnlinePlayers().forEach(p -> {
            p.teleport(loc);
        });
    }

    @Override
    public boolean isGameFinished() {
        return finished != null;
    }

    @Override
    public void update() {

    }

    @Override
    public void cleanUp() {

    }

    @Override
    public void onPressQ(Player p) {
        p.sendMessage(String.format("started = %s", state.isStarted() ? "YES" : "NO"));
        p.sendMessage(String.format("gaming = %s", FallGuys.getInstance().isPlayerPlaying(p) ? "YES" : "NO"));
        p.sendMessage(String.format("eliminated = %s, finished = %s", state.isPlayerEliminated(p) ? "YES" : "NO", state.isPlayerFinished(p) ? "YES" : "NO"));

        if(!state.isStarted() || finished != null) return;
        if(state.isEndedForPlayer(p)) return;
        double dist = p.getEyeLocation().distanceSquared(crownLocation);
        p.sendMessage(String.format("Crown Dst: %.2f / %.2f", Math.sqrt(dist), Math.sqrt(crownRadiusSqr)));
        if(dist <= crownRadiusSqr) {
            finished = p.getUniqueId();
            state.playerFinish(p);
            Bukkit.broadcastMessage(String.format("\u00a7e%s 抢到了皇冠! ", p.getName()));
            FallGuys.getInstance().getPlayingPlayers().forEach(p2 -> {
                if(p2 != p) state.eliminatePlayer(p2);
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if(!state.isStarted() || finished != null) return;
        if(state.isEndedForPlayer(event.getPlayer())) return;
        final Location to = event.getTo();
        if(to == null) return;
        if(to.distanceSquared(crownLocation) <= crownRadiusSqr) {
            event.getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText("\u00a7e按 Q 抢夺皇冠! "));
        }
    }

    @Override
    protected String getConfigurationName() {
        return "crown-map";
    }

}
