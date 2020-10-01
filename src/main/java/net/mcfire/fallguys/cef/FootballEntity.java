package net.mcfire.fallguys.cef;

import de.tr7zw.nbtapi.NBTCompound;
import io.github.definitlyevil.bukkitces.api.CustomEntity;
import io.github.definitlyevil.bukkitces.entities.base.BaseCustomEntity;
import net.mcfire.fallguys.FallGuys;
import net.mcfire.fallguys.utils.CustomModelUtils;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FootballEntity extends BaseCustomEntity {
    public static final String TYPE = "Football";

    private static final double RADIUS = 1.2d;
    private static final double RADIUS_SQUARED = RADIUS * RADIUS;
    private static final Vector GROUND_FORCE = new Vector(.8d, 1d, .8d); // 摩擦力

    private boolean onGround = true;

    private static ItemStack HEAD = CustomModelUtils.generateItem(6);

    private Vector velocity = null;

    @Override
    protected void _setup(ArmorStand armorStand, NBTCompound nbtCompound) {
        armorStand.getEquipment().setHelmet(HEAD);
    }

    @Override
    protected void _preUpdate(boolean force) {
        final Location currentLocation = getLocation();
        if(currentLocation.getY() < -10) {
            getBukkitEntity().remove();
            Bukkit.broadcastMessage("清理掉啦！");
            return;
        }
        final Location centerLocation = currentLocation.clone().add(0,1.62d-RADIUS,0d);
        Block under = centerLocation.getBlock();
        double groundDistance = currentLocation.getY()-under.getY();
        // Bukkit.broadcastMessage(String.format("离地距离: %.2f", groundDistance));
        if(under.getRelative(BlockFace.DOWN).isEmpty()) {
            addVelocity(new Vector(0, -.06d, 0)); // 重力！
            velocity.setY(Math.max(-.6d, velocity.getY())); // 最快下坠速度
            onGround = false;
        } else {
            if(groundDistance>.01d) {
                onGround = true;
                if(velocity != null && velocity.getY()<0) velocity.setY(0);
            }
        }
        if(velocity != null) {
            boolean[] collisions = checkCollision();
            if(collisions != null) {
                if(velocity != null) {
                    if(collisions[3] && velocity.getY() < 0d) { // 下边有东西的话
                        velocity.setY(Math.abs(velocity.getY() * .4d));
                        if (velocity.getY() < -.4) velocity.setY(-.4d);
                    }
                    if(collisions[4]) { // 上边顶到东西了
                        velocity.setY(-(velocity.getY()*.8d));
                    }
                    if(collisions[0]||collisions[1]) velocity.setX(velocity.getX() * -1);
                    if(collisions[4]||collisions[5]) velocity.setZ(velocity.getZ() * -1);
                }
            }
        }
        if(velocity != null) {
            setLocation(currentLocation.add(velocity));
        }
    }

    /**
     * 障碍物检测
     * @return 是否碰到了障碍？
     */
    private boolean[] checkCollision() {
        if(velocity == null) return null;
        boolean[] ret = {false,false,false,false,false,false, false};
        final Location currentLocation = getLocation().add(0,1.62d,0);
        RayTraceResult ray1 = trace(currentLocation.clone().add(RADIUS,0,0));
        if(ray1 != null && ray1.getHitBlock() != null) { ret[0] = true; ret[6] = true; }
        RayTraceResult ray2 = trace(currentLocation.clone().add(-RADIUS,0,0));
        if(ray2 != null && ray2.getHitBlock() != null) { ret[1] = true; ret[6] = true; }
        RayTraceResult ray3 = trace(currentLocation.clone().add(0,RADIUS,0));
        if(ray3 != null && ray3.getHitBlock() != null) { ret[2] = true; ret[6] = true; }
        RayTraceResult ray4 = trace(currentLocation.clone().add(0,-RADIUS,0));
        if(ray4 != null && ray4.getHitBlock() != null) { ret[3] = true; ret[6] = true; }
        RayTraceResult ray5 = trace(currentLocation.clone().add(0,0,RADIUS));
        if(ray5 != null && ray5.getHitBlock() != null) { ret[4] = true; ret[6] = true; }
        RayTraceResult ray6 = trace(currentLocation.clone().add(0,0,-RADIUS));
        if(ray6 != null && ray6.getHitBlock() != null) { ret[5] = true; ret[6] = true; }
        if(!ret[6]) return null;
        return ret;
    }

    private RayTraceResult trace(Location currentLocation) {
        if(velocity == null || velocity.lengthSquared() < 0.0001d) return null;
        return currentLocation.getWorld().rayTraceBlocks(
            currentLocation, velocity, velocity.length()+.08d,
            FluidCollisionMode.ALWAYS,
            true
        );
    }

    @Override
    protected void _postUpdate(boolean force) {
        if(velocity != null) {
            if (velocity.lengthSquared() <= 0.00001d) {
                // Bukkit.broadcastMessage("vel set to null");
                velocity = null;
            } else {
                if(onGround) { // 摩擦力
                    // Bukkit.broadcastMessage(String.format("onGround = %s", onGround ? "YES" : "NO"));
                    velocity.multiply(GROUND_FORCE);
                }
            }
        }
    }

    public void addVelocity(Vector delta) {
        if(velocity == null) {
            velocity = delta.clone();
        } else{
            velocity.add(delta);
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }

    // 物理数据 玩家最后推的时间
    private Map<UUID, Long> lastPush = new HashMap<>();

    /**
     * 处理足球的物理
     */
    public static class FootballPhysicsListener implements Listener {
        private static final FootballPhysicsListener INSTANCE = new FootballPhysicsListener();
        private static final double RAD_90DEG = Math.toRadians(90d);

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onPlayerMove(PlayerMoveEvent event) {
            final Player p = event.getPlayer();
            if(event.getTo() == null) return;
            if(!FallGuys.getInstance().isPlayerPlaying(p)) return;
            Location from = event.getFrom();
            Location to = event.getTo().clone();
            if(to.distanceSquared(from) < 0.001) return; // 根本没有动！
            Collection<Entity> nearBy = to.getWorld().getNearbyEntities(to, 2,2,2, (_e) -> ArmorStand.class.isAssignableFrom(_e.getClass()));
            // Bukkit.broadcastMessage(to.toString());
            if(nearBy.stream().anyMatch(_e -> this.process(p, from, to, _e))) {
                event.setCancelled(true);
            }
        }

        private boolean process(Player p, Location from, Location to, Entity entity) {
            if(!ArmorStand.class.isAssignableFrom(entity.getClass()) || !entity.hasMetadata(CustomEntity.ENTITY_META)) return false;
            Location loc = entity.getLocation(); // 获得盔甲架头部
            Location diffToFootball = to.clone().subtract(loc);
            double distSqr = diffToFootball.lengthSquared();
            // Bukkit.broadcastMessage(String.format("dist=%.2f", Math.sqrt(distSqr)));
            if(distSqr > RADIUS_SQUARED) return false;
            // double dist = Math.sqrt(distSqr);
            // double force = (RADIUS - dist) / RADIUS; // 算出力度
            CustomEntity ce = (CustomEntity) entity.getMetadata(CustomEntity.ENTITY_META).get(0).value();
            if(ce == null || !FootballEntity.class.isAssignableFrom(ce.getClass())) return false;
            FootballEntity football = (FootballEntity) ce;
            // 保证 0.5 秒以内之内踢到一次
            Location travel = to.clone().subtract(from);
            double angle = travel.toVector().setY(0d).angle(diffToFootball.toVector().setY(0).multiply(-1d)); // 从上方看的角度
            // if(angle > RAD_90DEG) return true;
            if(System.currentTimeMillis() - football.lastPush.getOrDefault(p.getUniqueId(), 0L) < 1000) return true;
            football.lastPush.put(p.getUniqueId(), System.currentTimeMillis());
            // p.sendMessage(String.format("处于碰撞体积范围 力度, %.2f, 角度: %.2f°", travel.length(), Math.toDegrees(angle)));

            // 判断在左在右
            double leftRight = Math.signum((travel.getX() * diffToFootball.getZ()) - (travel.getZ() * diffToFootball.getX()));
            // Bukkit.broadcastMessage(String.format("dir = %.2f", dir));

            // 确定已经踢到了足球
            Vector kickDirection = travel.toVector().normalize();
            kickDirection.multiply(travel.length() * 3d);
            kickDirection.setY(.4d);
            kickDirection.rotateAroundY(leftRight * angle);

            football.addVelocity(kickDirection);

            p.getWorld().playSound(loc, "fallguys.football_kick", SoundCategory.MASTER, 1.0f, 1.0f);
            return true;
        }

        public static final void register(Plugin plugin) {
            HandlerList.unregisterAll(INSTANCE);
            Bukkit.getPluginManager().registerEvents(INSTANCE, plugin);
        }
    }
}
