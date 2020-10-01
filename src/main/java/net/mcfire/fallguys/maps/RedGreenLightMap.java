package net.mcfire.fallguys.maps;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListeningWhitelist;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.injector.GamePhase;
import com.comphenix.protocol.reflect.StructureModifier;
import de.myzelyam.api.vanish.VanishAPI;
import net.mcfire.fallguys.FallGuys;
import net.mcfire.fallguys.states.MatchState;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.Random;

public class RedGreenLightMap extends BaseMap {
    public RedGreenLightMap(MatchState state) {
        super(state);
    }

    private static final String META_LAST_CHECKPOINT = "FallGuys::RedGreenLight::LastCheckPoint";

    private Location locationStart = null;

    private LightColor currentColor = null;
    private int lightTime = 0;

    @Override
    public String getDisplayName() {
        return "红灯、绿灯";
    }

    @Override
    public void onMapLoad() {
        state.getBar().setTitle("\u00a7a红灯停，绿灯行！");
        Location overview = readConfigLocation("spawn.overview");
        Bukkit.getOnlinePlayers().forEach(p -> {
            VanishAPI.hidePlayer(p);
            p.removeMetadata(META_LAST_CHECKPOINT, FallGuys.getInstance());
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(overview);
        });
    }

    @Override
    public void onGameStart() {
        locationStart = readConfigLocation("spawn.game");
        Bukkit.getOnlinePlayers().forEach(p -> {
            p.teleport(locationStart);
            p.setGameMode(GameMode.ADVENTURE);
            VanishAPI.showPlayer(p);
        });
    }

    private int getRequiredFinished() {
        final int online = Bukkit.getOnlinePlayers().size();
        if(online > 10) {
            return online / 3;
        } else if (online > 5) {
            return 3;
        } else return 1;
    }

    @Override
    public boolean isGameFinished() {
        return state.getFinishedCount() >= getRequiredFinished() || Bukkit.getOnlinePlayers().stream().allMatch(state::isEndedForPlayer);
    }

    @Override
    public void update() {
        if(currentColor == null) {
            setCurrentLight(LightColor.GREEN);
            return;
        }
        if(lightTime > 0) {
            lightTime --;
        } else {
            if(currentColor == LightColor.GREEN) {
                setCurrentLight(LightColor.YELLOW);
            } else if (currentColor == LightColor.YELLOW) {
                setCurrentLight(LightColor.RED);
            } else if (currentColor == LightColor.RED) {
                setCurrentLight(LightColor.GREEN);
            }
        }
    }

    private void setCurrentLight(LightColor color) {
        state.getBar().setColor(color.barColor);
        state.getBar().setTitle(color.display);
        this.currentColor = color;
        lightTime = color.randomTime();
        Bukkit.getOnlinePlayers().forEach(p -> {
            p.sendMessage("\u00a76信号变了！ " + color.display);
        });
    }

    @Override
    public void cleanUp() {
    }

    private enum LightColor {
        RED("\u00a7c\u00a7l红灯", BarColor.RED, 3, 0),
        GREEN("\u00a7a\u00a7l绿色", BarColor.GREEN, 5, 5),
        YELLOW("\u00a7e\u00a7l黄色", BarColor.YELLOW, 1,2);

        private final String display;
        private final BarColor barColor;

        private final int baseTime;
        private final int randomTime;

        LightColor(String display, BarColor barColor, int baseTime, int randomTime) {
            this.display = display;
            this.barColor = barColor;
            this.baseTime = baseTime;
            this.randomTime = randomTime;
        }

        public String getDisplay() {
            return display;
        }

        public BarColor getBarColor() {
            return barColor;
        }

        private int randomTime() {
            if(randomTime <= 0) return baseTime;
            return baseTime + new Random().nextInt(randomTime);
        }

    }

    @Override
    public ListeningWhitelist getReceivingWhitelist() {
        return ListeningWhitelist.newBuilder()
            .gamePhase(GamePhase.PLAYING)
            .types(PacketType.Play.Client.POSITION, PacketType.Play.Client.POSITION_LOOK)
            .build();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMoveCheck(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        Location location = p.getLocation();
        Block step = location.getBlock().getRelative(BlockFace.DOWN);
        if(step.getType() == Material.GOLD_BLOCK) {
            // 记录点
            p.setMetadata(META_LAST_CHECKPOINT, new FixedMetadataValue(FallGuys.getInstance(), location));
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("\u00a7a记录点已更新! "));
        } else if (step.getType() == Material.LIME_CONCRETE) {
            // 完成点
            state.playerFinish(p);
        }
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        if(!state.isStarted()) return;
        Player p = event.getPlayer();
        if(state.isPlayerEliminated(p)) return;
        if(currentColor != LightColor.RED) return;
        StructureModifier<Double> dbl = event.getPacket().getDoubles();
        double x = dbl.read(0), y = dbl.read(1), z = dbl.read(2);
        double distSqr = new Vector(x, y, z).distanceSquared(p.getLocation().toVector());
        if(distSqr >= 0.5d) {
            event.setCancelled(true); // IGNORE THIS PACKET!!!
            // MOVED!
            p.sendMessage("\u00a7c你移动啦！");
            Bukkit.getScheduler().runTask(FallGuys.getInstance(), () -> {
                if(!p.isOnline()) return;
                if(p.hasMetadata(META_LAST_CHECKPOINT)) {
                    Location loc = (Location) p.getMetadata(META_LAST_CHECKPOINT).get(0).value();
                    p.teleport(loc);
                } else {
                    p.teleport(locationStart);
                }
                p.sendMessage("\u00a7d你被传送到了记录点! ");
            });
        }
    }

    @Override
    protected String getConfigurationName() {
        return "red-green-light";
    }

}
