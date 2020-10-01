package net.mcfire.fallguys;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.comphenix.protocol.ProtocolLibrary;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import io.github.definitlyevil.bukkitces.CustomEntityFramework;
import io.github.definitlyevil.bukkitces.CustomEntityRegister;
import me.clip.placeholderapi.PlaceholderAPI;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.npc.MemoryNPCDataStore;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.mcfire.fallguys.cef.BigBall;
import net.mcfire.fallguys.cef.FootballEntity;
import net.mcfire.fallguys.cef.MoveableBlock;
import net.mcfire.fallguys.cef.PinkBarrier;
import net.mcfire.fallguys.commands.ForceStartCommand;
import net.mcfire.fallguys.states.ResetState;
import net.mcfire.fallguys.states.WaitState;
import net.mcfire.roomrpc.HTTP;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.util.*;

public final class FallGuys extends JavaPlugin implements Listener
{

    public static boolean maintenance = false;

    private static FallGuys instance = null;

    public static FallGuys getInstance() {
        return instance;
    }

    private NPCRegistry npcRegistry = null;

    /**
     * 当前游戏状态
     */
    private GameState state = null;

    private Set<UUID> playing = new HashSet<>();

    private BukkitTask stateUpdaterTask = null;

    private File directoryMapConfigurations;

    @Override
    public void onLoad() {
        instance = this;

        CustomEntityFramework.debug = false;
    }

    /**
     * 获得相应地图的配置文件
     * @param mapName
     * @return
     */
    public ConfigurationSection getMapConfiguration(String mapName) {
        File f = new File(directoryMapConfigurations, mapName + ".yml");
        if(!f.exists()) {
            try {
                byte[] data = ByteStreams.toByteArray(FallGuys.class.getResourceAsStream("/maps/" + mapName + ".yml"));
                Files.write(data, f);
                return YamlConfiguration.loadConfiguration(new InputStreamReader(new ByteArrayInputStream(data)));
            } catch (Exception ex) {
                ex.printStackTrace();
                return null;
            }
        } else {
            return YamlConfiguration.loadConfiguration(f);
        }
    }

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        directoryMapConfigurations = new File(getDataFolder(), "maps");
        directoryMapConfigurations.mkdirs();

        saveDefaultConfig();
        reloadConfig();
        maintenance = getConfig().getBoolean("maintenance-mode", false);

        getLogger().info("Loading RoomRPC info... ");
        saveResource("api.yml", false);
        ConfigurationSection configAPI = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "api.yml"));
        HTTP.api_endpoint = configAPI.getString("api.endpoint");
        HTTP.api_key = configAPI.getString("api.key");

        CustomEntityRegister.getInstance().register(BigBall.class);
        CustomEntityRegister.getInstance().register(PinkBarrier.class);
        CustomEntityRegister.getInstance().register(FootballEntity.class);
        CustomEntityRegister.getInstance().register(MoveableBlock.class);
        PinkBarrier.PinkBarrierPushListener.register(this); // listener
        FootballEntity.FootballPhysicsListener.register(this); // football
        MoveableBlock.MoveableBlockPhysicsListener.register(this); // moveable block

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new WorldProtectListener(), this);
        getServer().getScheduler().runTaskTimer(this, new ConveyerTask(), 20L, 10L);
        getServer().getPluginManager().registerEvents(new JumpSoundListener(), this);

        getCommand("fstart").setExecutor(new ForceStartCommand());
    }

    public void enterState(GameState newState) {
        if(this.state != null) {
            getLogger().info(String.format("Leaving state: %s", this.state.getClass().getSimpleName()));
            this.state.onLeaveState();
            HandlerList.unregisterAll(this.state);
            this.state = null;
        }
        if(stateUpdaterTask != null) {
            stateUpdaterTask.cancel();
            stateUpdaterTask = null;
        }
        getLogger().info(String.format("Entering state: %s", newState.getClass().getSimpleName()));
        this.state = newState;
        this.state.onEnterState();
        getServer().getPluginManager().registerEvents(this.state, this);
        stateUpdaterTask = getServer().getScheduler().runTaskTimer(this, this.state, 0L, 20L);
    }

    public File getDirectoryMapConfigurations() {
        return directoryMapConfigurations;
    }

    public GameState getState() {
        return state;
    }

    /**
     * 检测玩家连接，如果游戏已经开始则踢出。
     * @param event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(AsyncPlayerPreLoginEvent event) {
        if(maintenance) return;
        if(this.state == null || ResetState.class.isAssignableFrom(state.getClass()) || !WaitState.class.isAssignableFrom(state.getClass()) || ((WaitState) state).isStarted()) {
            getLogger().info(String.format("Kicked player <%s> because game is already started! ", event.getName()));
            event.setKickMessage("\u00a7c游戏已经开始");
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerLoaded(ServerLoadEvent event) {
        if(event.getType() != ServerLoadEvent.LoadType.STARTUP) return;

        PlaceholderAPI.registerPlaceholderHook("fallguys", new PAPIIntegration());
        ProtocolLibrary.getProtocolManager().addPacketListener(new SkinPacketListener(this));

        npcRegistry = CitizensAPI.createNamedNPCRegistry(String.format("npc-%d", System.currentTimeMillis()), new MemoryNPCDataStore());

        if(maintenance) return; // MAINTENANCE

        getServer().getWorlds().forEach(w -> {
            w.setSpawnLocation(0, 250, 0);
            w.setSpawnFlags(false, false);
            w.setAutoSave(false);
            w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            w.setTime(2000L);
        });

        enterState(new WaitState());
    }

    public NPCRegistry getNpcRegistry() {
        return npcRegistry;
    }

    /**
     * 检测玩家进入世界。
     * @param event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if(maintenance) return;
        event.getPlayer().setFoodLevel(20);
        if(state != null) {
            if(!state.onPlayerJoin(event.getPlayer())) {
                event.getPlayer().kickPlayer("Kicked by game state! ");
                return;
            }
        } else event.getPlayer().kickPlayer("state == null");
    }

    private final Map<UUID, String> skinMap = Collections.synchronizedMap(new HashMap<>());

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoinLoadSkin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        player.sendMessage("正在读取皮肤... ");
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            if(!player.isOnline()) return;
            try {
                JSONObject p = new JSONObject();
                p.put("u", player.getName());
                p.put("f", new JSONArray(Arrays.asList("FALLGUYS_SKIN")));
                JSONArray skin = HTTP.api("/storage/equipped", p).getJSONObject("data").getJSONArray("equipped");
                String skin_name;
                if(skin.size() <= 0) {
                    player.sendMessage("\u00a76当前皮肤: \u00a7d默认皮肤 - 粉色糖豆人");
                    skin_name = "fallguys_pink";
                } else {
                    JSONObject skinInfo = skin.getJSONObject(0);
                    skin_name = skinInfo.getString("item_tag");
                    player.sendMessage(String.format("\u00a76当前皮肤: \u00a7a%s \u00a77(剩余 %d 天)",
                        skin_name,
                        (skinInfo.getLongValue("expire_time")-(System.currentTimeMillis()/1000L))/(24*60*60)
                    ));
                }
                skinMap.put(player.getUniqueId(), skin_name);
                player.sendMessage("SKIN=" + skin_name);
                Bukkit.getOnlinePlayers().forEach(_p -> {
                    _p.sendMessage(String.format("PSKIN=%s;%s", player.getUniqueId().toString(), skin_name));
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                event.getPlayer().sendMessage("\u00a7c皮肤获取失败! ");
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNPCSpawn(NPCSpawnEvent event) {
        Bukkit.broadcastMessage(String.format("PSKIN=%s;%s", event.getNPC().getEntity().getUniqueId().toString(), "fallguys_pink"));
    }

    public String getSkinFor(UUID uuid) {
        return skinMap.get(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if(maintenance) return;
        playing.remove(event.getPlayer().getUniqueId());
        skinMap.remove(event.getPlayer().getUniqueId());
        if(state != null) {
            state.onPlayerQuit(event.getPlayer());
        }
    }

    public List<Location> readConfigLocationList(String key) {
        List<String> lst = getConfig().getStringList(key);
        List<Location> ret = new ArrayList<>(lst.size());
        for(String l : lst) {
            ret.add(readLocationFromString(l));
        }
        return ret;
    }

    public Location readConfigLocation(String key) {
        return readLocationFromString(getConfig().getString(key));
    }

    public static Location readLocationFromString(String locationString) {
        String[] parts = locationString.split(",");
        if(parts.length != 4 && parts.length != 6) throw new IllegalArgumentException("Invalid location string: " + locationString);
        World w = Bukkit.getWorld(parts[0]);
        if(w == null) throw new IllegalArgumentException("World not found: " + locationString);
        Location loc = new Location(
            w,
            Double.parseDouble(parts[1]),
            Double.parseDouble(parts[2]),
            Double.parseDouble(parts[3])
        );
        if(parts.length == 6) {
            loc.setYaw(Float.parseFloat(parts[4]));
            loc.setPitch(Float.parseFloat(parts[5]));
        }
        return loc;
    }

    public Location readConfigYawPitchAsLocation(String key) {
        return readYawPitchFromString(getConfig().getString(key));
    }

    public static Location readYawPitchFromString(String str) {
        if(str == null) return null;
        String[] parts = str.split(",");
        if(parts.length != 2) return null;
        return new Location(null, 0d, 0d, 0d, Float.parseFloat(parts[0]), Float.parseFloat(parts[1]));
    }

    public static Vector readVectorFromString(String vecStr) {
        if(vecStr == null) return null;
        String[] args = vecStr.split(",");
        if(args.length != 3) return null;
        return new Vector(Double.parseDouble(args[0]), Double.parseDouble(args[1]), Double.parseDouble(args[2]));
    }

    public World getConfigWorld(String key) {
        return Bukkit.getWorld(getConfig().getString(key));
    }


    private static ItemStack setLeatherArmorColor(ItemStack armor, Color color) {
        if(!armor.getType().name().startsWith("LEATHER_")) throw new IllegalArgumentException("not leather armor");
        LeatherArmorMeta m = (LeatherArmorMeta) armor.getItemMeta();
        m.setColor(color);
        armor.setItemMeta(m);
        return armor;
    }

    public List<Player> getPlayingPlayers() {
        List<Player> l = new ArrayList<>(playing.size());
        playing.removeIf(u -> {
            Player p = Bukkit.getPlayer(u);
            if(p == null || !p.isOnline()) {
                return true;
            } else {
                l.add(p);
                return false;
            }
        });
        return l;
    }

    public void resetPlaying() {
        playing.clear();
        Bukkit.getOnlinePlayers().forEach(p -> playing.add(p.getUniqueId()));
    }

    public boolean isPlayerPlaying(Player p) {
        return playing.contains(p.getUniqueId());
    }

    public boolean removePlaying(Player p) {
        return playing.remove(p.getUniqueId());
    }



    public static void playSoundForAll(String name, SoundCategory category) { playSoundForAll(name, 1.0f, category); }
    public static void playSoundForAll(String name) { playSoundForAll(name, 1.0f, SoundCategory.MASTER); }
    public static void playSoundForAll(String name, float pitch) { playSoundForAll(name, pitch, SoundCategory.MASTER); }
    public static void playSoundForAll(String name, float pitch, SoundCategory category) {
        Bukkit.getOnlinePlayers().forEach(p -> {
            p.playSound(p.getLocation(), name, category, 1.0f, pitch);
        });
    }

}

