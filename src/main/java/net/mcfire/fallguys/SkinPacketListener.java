package net.mcfire.fallguys;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;

public class SkinPacketListener extends PacketAdapter {

    public SkinPacketListener(Plugin plugin) {
        super(plugin, PacketType.Play.Server.PLAYER_INFO);
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if(event.getPacket().getPlayerInfoAction().read(0) == EnumWrappers.PlayerInfoAction.ADD_PLAYER) {
            // 发送皮肤
            List<PlayerInfoData> list = event.getPacket().getPlayerInfoDataLists().read(0);
            list.forEach(info -> {
                final UUID uuid = info.getProfile().getUUID();
                if(uuid == null) return;
                final String skin = ((FallGuys) plugin).getSkinFor(uuid);
                if(skin != null) {
                    plugin.getLogger().info(String.format("Sending skin for %s (%s)", info.getProfile().getName(), uuid.toString()));
                    // 发送皮肤id
                    event.getPlayer().sendMessage(String.format("PSKIN=%s;%s", uuid.toString(), skin));
                }
            });
        }
    }

}
