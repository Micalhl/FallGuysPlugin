package net.mcfire.fallguys;

import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.FixedMetadataValue;

public class JumpSoundListener implements Listener {

    private static final String META_JUMPING = "FallGuys::Jump::Sound";
    private static final String META_FALLING = "FallGuys::Fall::Sound";

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().removeMetadata(META_JUMPING, FallGuys.getInstance());
        event.getPlayer().removeMetadata(META_FALLING, FallGuys.getInstance());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if(event.getTo() == null) return;
        Player p = event.getPlayer();

        if (p.hasMetadata(META_JUMPING) && System.currentTimeMillis() - ((long) p.getMetadata(META_JUMPING).get(0).value()) > 500) {
            p.removeMetadata(META_JUMPING, FallGuys.getInstance());
        }

        final boolean jumping = event.getTo().getY() > event.getFrom().getY();
        final boolean falling = !jumping && event.getTo().getY() < event.getFrom().getY();

        if(jumping) {
            p.removeMetadata(META_FALLING, FallGuys.getInstance());
        }
        if(falling) {
            p.removeMetadata(META_JUMPING, FallGuys.getInstance());
        }

        if (!p.hasMetadata(META_FALLING) && falling && p.getFallDistance() > 2f) {
            p.setMetadata(META_FALLING, new FixedMetadataValue(FallGuys.getInstance(), true));
            p.getWorld().playSound(p.getLocation(), "fallguys.wu", SoundCategory.AMBIENT, 1.0f, 1.0f);
        }

        if(!p.hasMetadata(META_JUMPING) && jumping) {
            // jump up
            p.setMetadata(META_JUMPING, new FixedMetadataValue(FallGuys.getInstance(), System.currentTimeMillis()));
            p.getWorld().playSound(p.getLocation(), "fallguys.jump", SoundCategory.AMBIENT, 1.0f, 1.0f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        event.getPlayer().removeMetadata(META_FALLING, FallGuys.getInstance());
    }

}
