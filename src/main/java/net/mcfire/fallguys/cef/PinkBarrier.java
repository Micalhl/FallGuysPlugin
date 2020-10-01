package net.mcfire.fallguys.cef;

import de.tr7zw.nbtapi.NBTCompound;
import io.github.definitlyevil.bukkitces.CustomEntityFramework;
import io.github.definitlyevil.bukkitces.api.CustomEntity;
import io.github.definitlyevil.bukkitces.entities.base.BaseCustomEntity;
import io.github.definitlyevil.bukkitces.utils.ArmorStandUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class PinkBarrier extends BaseCustomEntity {
    public static final String TYPE = "PinkBarrier";

    private static final Vector VEC_Z_AXIS = new Vector(0, 0, 1);

    private static final double ROT_ACCEL_CHANGE = Math.toRadians(.4d);
    private static final double ROT_ACCEL_FADE = Math.toRadians(0.08);

    private static final double ROT_ACCEL_LIMIT = Math.toRadians(.8d);

    private static final double RAD_360DEG = Math.toRadians(360.d);
    private static final double RAD_180DEG = Math.toRadians(180.d);
    private static final double RAD_90DEG = Math.toRadians(90.d);
    private static final double RAD_45DEG = Math.toRadians(45.d);

    private static final ItemStack BARRIER_ROTATOR_ITEM = new ItemStack(Material.GOLDEN_HOE);
    private static final ItemStack BARRIER_ITEM = new ItemStack(Material.GOLDEN_HOE);

    static {
        ItemMeta m = BARRIER_ROTATOR_ITEM.getItemMeta();
        m.setCustomModelData(3);
        BARRIER_ROTATOR_ITEM.setItemMeta(m);

        m = BARRIER_ITEM.getItemMeta();
        m.setCustomModelData(4);
        BARRIER_ITEM.setItemMeta(m);
    }

    private double rot = 0f;
    private double rotAccel = 0f;

    private BoundingBox bb = new BoundingBox(-7,-2,-.1, 7, 4, .1);

    @Override
    protected void _setup(ArmorStand armorStand, NBTCompound nbtCompound) {
        ArmorStandUtils.spawnPart(this, BARRIER_ROTATOR_ITEM, null, false, null);
        ArmorStandUtils.spawnPart(this, BARRIER_ITEM, null, false, (prt) -> {
            // bb1
            ((ArmorStand) prt.getBukkitEntity()).setHeadPose(new EulerAngle(0d, RAD_180DEG, 0d));
            prt.setOffset(new Vector(-3.8d, 0, 0));
        });
        ArmorStandUtils.spawnPart(this, BARRIER_ITEM, null, false, (prt) -> {
            prt.setOffset(new Vector(3.8d, 0, 0));
            // bb2
        });

        setRotation(new EulerAngle(0d, 0d, 0d));
    }

    private void modifyY(double deltaRad) {
        rot += deltaRad;
        rot %= RAD_360DEG;
        setRotation(new EulerAngle(0d, rot, 0d));
    }

    @Override
    protected void _preUpdate(boolean force) {
        // Bukkit.broadcastMessage(String.format("rotAccel = %.2f", rotAccel));

        if(Math.abs(rotAccel) >= 0.001d) {
            modifyY(rotAccel);

            if(rotAccel < 0) {
                rotAccel += ROT_ACCEL_FADE;
            } else rotAccel -= ROT_ACCEL_FADE;
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }

    private Location processPush(Player p, Vector rayDirection) {
        final Location loc = getLocation();
        final Location targetLocation = p.getLocation();
        final Vector diff = targetLocation.subtract(loc).toVector();
        Vector rayStart = diff.rotateAroundY(rot);
        rayDirection.rotateAroundY(rot);
        if(rayDirection.lengthSquared() <= 0.001) return null;
        RayTraceResult ray = bb.rayTrace(rayStart, rayDirection, 1.5d);
        if(ray == null) return null;
        p.setVelocity(p.getVelocity().normalize().multiply(-.2d));

        Vector hitPosition = ray.getHitPosition();

            /*Location realHitLocation = hitPosition.clone().rotateAroundY(rot).toLocation(loc.getWorld()).add(loc);
            realHitLocation.getWorld().spawnParticle(Particle.DRIP_LAVA, realHitLocation, 1);*/

        double angle = VEC_Z_AXIS.angle(rayStart.clone().setY(0d).subtract(hitPosition));
        // Bukkit.broadcastMessage(String.format("hit @ angle %.2fdeg : %s", Math.toDegrees(angle), hitPosition.toString()));

        double angleForce = Math.max(0d, diff.length()/7);
        if(hitPosition.getX() > 0d) {
            // 右边的板
            if(Math.abs(angle) > RAD_90DEG) {
                if(rotAccel < 0) rotAccel = 0f;
                rotAccel += angleForce * ROT_ACCEL_CHANGE;
            } else {
                if(rotAccel > 0) rotAccel = 0f;
                rotAccel -= angleForce * ROT_ACCEL_CHANGE;
            }
        } else {
            // 左边的版
            if(Math.abs(angle) > RAD_90DEG) {
                if(rotAccel > 0) rotAccel = 0f;
                rotAccel -= angleForce * ROT_ACCEL_CHANGE;
            } else {
                if(rotAccel < 0) rotAccel = 0f;
                rotAccel += angleForce * ROT_ACCEL_CHANGE;
            }
            rotAccel = Math.min(Math.max(rotAccel, -ROT_ACCEL_LIMIT), ROT_ACCEL_LIMIT);
        }

        return loc.add(hitPosition.rotateAroundY(-rot));
    }

    public static class PinkBarrierPushListener implements Listener {
        private static final PinkBarrierPushListener instance = new PinkBarrierPushListener();
        public static void register(Plugin plugin) {
            HandlerList.unregisterAll(instance);
            Bukkit.getPluginManager().registerEvents(instance, plugin);
        }

        private PinkBarrierPushListener() { }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onPlayerMove(PlayerMoveEvent event) {
            if(event.getTo() == null) return;
            Player p = event.getPlayer();
            for(Entity e : p.getNearbyEntities(8,6,8)) {
                if(!CustomEntityFramework.isCEEntity(e)) continue;
                CustomEntity ce = (CustomEntity) e.getMetadata(CustomEntity.ENTITY_META).get(0).value();
                if(ce == null || !PinkBarrier.class.isAssignableFrom(ce.getClass())) continue;
                PinkBarrier barrier = (PinkBarrier) ce;
                final Vector dir = event.getTo().clone().subtract(event.getFrom()).toVector();
                final Location hitAt = barrier.processPush(p, dir);
                if(hitAt != null) {
                    // hitAt.add(dir.multiply(-.2));
                    // event.setTo(hitAt);
                    event.setCancelled(true);
                    // Bukkit.broadcastMessage("!!!!!!!!! MOVE = " + hitAt.toString());
                }
            }
        }
    }
}
