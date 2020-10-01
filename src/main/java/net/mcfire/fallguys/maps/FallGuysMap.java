package net.mcfire.fallguys.maps;

import com.comphenix.protocol.events.ListeningWhitelist;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import net.mcfire.fallguys.FallGuys;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public interface FallGuysMap extends Listener, PacketListener {

    String getDisplayName();

    void onMapLoad();

    void onGameStart();

    boolean isGameFinished();

    void update();

    void cleanUp();

    /**
     * 每个tick执行一次，只在map开始了之后才会执行！
     */
    default void tick() { }

    /**
     * 当玩家按 Q 键的时候
     * @param p 玩家
     */
    default void onPressQ(Player p) { }


    default void onPacketSending(PacketEvent event) { }
    default void onPacketReceiving(PacketEvent event) { }
    default ListeningWhitelist getSendingWhitelist() { return ListeningWhitelist.EMPTY_WHITELIST; }
    default ListeningWhitelist getReceivingWhitelist() { return ListeningWhitelist.EMPTY_WHITELIST; }
    default Plugin getPlugin() { return FallGuys.getInstance(); }

}
