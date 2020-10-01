package net.mcfire.fallguys.maps;

import io.github.definitlyevil.bukkitces.CustomEntityRegister;
import net.mcfire.fallguys.FallGuys;
import net.mcfire.fallguys.cef.FootballEntity;
import net.mcfire.fallguys.states.MatchState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * 基♂情足球
  */
public class FootbalMap extends TeamedMap {
    public FootbalMap(MatchState state) {
        super(state);
    }

    private int[] scores = {0, 0};

    private Location footballSpawn;

    private BoundingBox[] gates = {null, null};

    private boolean footballSpawned = false;
    private FootballEntity football = null;

    private int countdown = 90;

    @Override
    protected void _internal_onMapLoad() {
        Location loc = readConfigLocation("spawn.overview");
        footballSpawn = readConfigLocation("spawn.football");
        gates[0] = readBoundingBox("gates.team-1");
        gates[1] = readBoundingBox("gates.team-2");
        Bukkit.getOnlinePlayers().forEach(p -> {
            p.teleport(loc);
        });
        updateBossBar();
    }

    @Override
    protected void _internal_tick() {

    }

    /**
     * 足球是两个队伍
     * @return
     */
    @Override
    protected int internal_getTeamCount() {
        return 2;
    }

    @Override
    public String getDisplayName() {
        return "激情足球";
    }

    @Override
    public void onGameStart() {
        Location spawn1 = readConfigLocation("spawn.team-1");
        Location spawn2 = readConfigLocation("spawn.team-2");
        FallGuys.getInstance().getPlayingPlayers().forEach(p -> {
            p.teleport(
                getPlayerTeam(p.getUniqueId()) == 0 ? spawn1 : spawn2
            );
        });
    }

    @Override
    public boolean isGameFinished() {
        if(countdown <= 0) {
            changeTeamState(0, scores[0] < scores[1]);
            changeTeamState(1, scores[1] < scores[0]);
            return true;
        }
        countdown --;
        return false;
    }

    @Override
    public void update() {
        if(!footballSpawned) {
            footballSpawned = true;
            CustomEntityRegister.getInstance().spawn(
                FootballEntity.TYPE,
                footballSpawn,
                (_e) -> this.football = (FootballEntity) _e
            );
        } else {
            if(football == null) {
                Bukkit.broadcastMessage("football not spawned yet");
                return;
            }
            Vector floc = football.getLocation().toVector();
            if(gates[0].contains(floc)) {
                scores[1] ++;
                footballSpawned = false;
                football.getBukkitEntity().remove();
                FallGuys.playSoundForAll("fallguys.course.start");
            } else if (gates[1].contains(floc)) {
                scores[0] ++;
                footballSpawned = false;
                football.getBukkitEntity().remove();
                FallGuys.playSoundForAll("fallguys.course.start");
            }
            updateBossBar();
        }
    }

    private void updateBossBar() {
        TeamColor color0 = teamColor.get(0);
        TeamColor color1 = teamColor.get(1);
        state.getBar().setTitle(
            String.format(
                "%s\u00a77(\u00a7b%d\u00a77) \u00a77| %s\u00a77(\u00a7b%d\u00a77) \u00a77| \u00a79倒计时 \u00a7a%ds",
                color0.display, scores[0],
                color1.display, scores[1],
                countdown
            )
        );
    }

    @Override
    public void cleanUp() {
        if(football != null) {
            football.getBukkitEntity().remove();
        }
    }

    @Override
    protected String getConfigurationName() {
        return "team-football";
    }
}
