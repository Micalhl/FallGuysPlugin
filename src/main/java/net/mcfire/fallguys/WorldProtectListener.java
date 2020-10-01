package net.mcfire.fallguys;

import io.github.definitlyevil.bukkitces.CustomEntityRegister;
import io.github.definitlyevil.bukkitces.api.CustomEntity;
import net.mcfire.fallguys.cef.BigBall;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.Repeater;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.util.Vector;

import java.util.Random;

public class WorldProtectListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if(!perm(event.getPlayer())) {
            event.setCancelled(true);
            event.setBuild(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if(!perm(event.getPlayer())) {
            event.setCancelled(true);
            event.setDropItems(false);
            event.setExpToDrop(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketUse(PlayerBucketEmptyEvent event) {
        if(!perm(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketUse(PlayerBucketFillEvent event) {
        if(!perm(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldLoad(WorldLoadEvent event) {
        event.getWorld().setAutoSave(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChunkUnload(ChunkUnloadEvent event) {
        event.setSaveChunk(false);
    }

    public static boolean perm(Player player) {
        return player.hasPermission("fallguys.admin");
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if(Vehicle.class.isAssignableFrom(entity.getClass()) || Player.class.isAssignableFrom(entity.getClass())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if(Explosive.class.isAssignableFrom(event.getDamager().getClass())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChunkLoad(ChunkLoadEvent event) {
        for(Entity entity : event.getChunk().getEntities()) {
            if(entity.getType() == EntityType.PRIMED_TNT || entity.getType() == EntityType.ARMOR_STAND || entity.getType() == EntityType.DROPPED_ITEM) {
                entity.remove();
            }
        }
        World w = event.getWorld();
        int chunkX = event.getChunk().getX(), chunkZ = event.getChunk().getZ();
        Bukkit.getScheduler().runTaskLater(FallGuys.getInstance(), () -> {
            Chunk chunk = w.getChunkAt(chunkX, chunkZ);
            if(!chunk.isLoaded()) return;
            for(Entity ent : chunk.getEntities()) {
                if(!ent.hasMetadata(CustomEntity.ENTITY_META)) continue;
                CustomEntity ce = (CustomEntity) ent.getMetadata(CustomEntity.ENTITY_META).get(0).value();
                if(BigBall.class.isAssignableFrom(ce.getClass())) {
                    FallGuys.getInstance().getLogger().info("removed unused BigBall @ " + ent.getLocation().toString());
                    ent.remove();
                }
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplode(EntityExplodeEvent event) {
        event.setCancelled(true);
        event.blockList().clear(); // do not fucking damage any blocks!!!
        
        double rad = FallGuys.getInstance().getConfig().getDouble("explode.radius");
        double side_force = FallGuys.getInstance().getConfig().getDouble("explode.side-force");
        double y_force = FallGuys.getInstance().getConfig().getDouble("explode.y-force");
        Location location = event.getLocation();
        location.add(.5d, .5d, .5d);
        for(Entity target : location.getWorld().getNearbyEntities(location, rad, rad, rad)) {
            if(target == event.getEntity() || target.getType() == EntityType.PRIMED_TNT) continue;
            if(Player.class.isAssignableFrom(target.getClass()) && ((Player) target).getGameMode() != GameMode.ADVENTURE) continue;
            Vector diff = target.getLocation().subtract(location).toVector();
            diff.setY(0);
            double len = Math.max(diff.length(), 0.05d);
            double percent = Math.min(Math.max(rad-len, 0), 1);
            double force = percent * side_force;
            diff.setY(percent * y_force);
            // Bukkit.broadcastMessage(String.format("len = %.2f, force = %.2f", len, force));
            diff.normalize().multiply(force);
            target.setVelocity(diff);
        }
    }

    /**
     * TNT 生成器
     * @param event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRedstone(BlockRedstoneEvent event) {
        if(event.getBlock().getType() != Material.REPEATER) return;
        if(event.getNewCurrent() <= event.getOldCurrent()) return;
        Repeater repeater = (Repeater) event.getBlock().getBlockData();
        BlockFace dir = repeater.getFacing().getOppositeFace();
        Block related = event.getBlock().getRelative(dir);
        if(!related.getType().name().endsWith("_SIGN")) return;
        Sign state = (Sign) related.getState();
        if(!state.getLine(0).equalsIgnoreCase("[fallguys]")) return;
        String[] op = state.getLine(1).split(" ");
        if(op.length <= 0) return;
        if(op[0].equalsIgnoreCase("tnt")) {
            // [fallguys]
            // tnt <fuse ticks>
            // [far | x,y,z diff]
            // [velocity]
            Vector diff;
            if(!state.getLine(2).isEmpty()) {
                String[] prts = state.getLine(2).split(",");
                if(prts.length != 3) {
                    double dist = Double.parseDouble(prts[0]);
                    diff = dir.getDirection().multiply(dist);
                } else {
                    diff = new Vector(Double.parseDouble(prts[0]), Double.parseDouble(prts[1]), Double.parseDouble(prts[2]));
                }
            } else {
                diff = dir.getDirection();
            }
            Location spawnAt;
            spawnAt = related.getLocation().add(diff);
            TNTPrimed tnt = (TNTPrimed) related.getWorld().spawnEntity(
                spawnAt,
                EntityType.PRIMED_TNT
            );
            if(op.length > 1) tnt.setFuseTicks(Integer.parseInt(op[1]));
            String l3 = state.getLine(3);
            if(!l3.isEmpty()) {
                Vector initial_velocity = FallGuys.readVectorFromString(l3);
                tnt.setVelocity(initial_velocity);
            }
        } else if (op[0].equalsIgnoreCase("ball")) {
            // [fallguys]
            // ball [speed[,rnd]] [ttl]
            // diff
            // direction [random x,y,z]

            Vector diff = FallGuys.readVectorFromString(state.getLine(2));
            String[] direction_ops = state.getLine(3).split(" ");
            Location ball_dir = FallGuys.readYawPitchFromString(direction_ops[0]);
            if(direction_ops.length > 1) {
                // random
                Random rnd = new Random();
                Location yp = FallGuys.readYawPitchFromString(direction_ops[1]);
                float yawDiff = yp.getYaw() * rnd.nextFloat();
                float pitchDiff = yp.getPitch() * rnd.nextFloat();
                if(rnd.nextBoolean()) yawDiff *= -1;
                if(rnd.nextBoolean()) pitchDiff *= -1;
                ball_dir.setYaw(ball_dir.getYaw() + yawDiff);
                ball_dir.setPitch(ball_dir.getPitch() + pitchDiff);
            }

            Vector vel = ball_dir.getDirection();

            if(op.length > 1) {
                String[] speed_ops = op[1].split(",");
                double amnt = Double.parseDouble(speed_ops[0]);
                if(speed_ops.length > 1) {
                    Random rnd = new Random();
                    amnt += rnd.nextDouble() * Double.parseDouble(speed_ops[1]);
                }
                vel.multiply(amnt);
            } else {
                vel.multiply(0.2d);
            }

            CustomEntityRegister.getInstance().spawn("BigBall", related.getLocation().add(diff), (_e) -> {
                int delay = op.length > 2 ? Integer.parseInt(op[2]) : 100;
                Bukkit.getScheduler().runTaskLater(FallGuys.getInstance(), () -> {
                    _e.getBukkitEntity().remove();
                }, delay);

                ((BigBall)_e).setVelocity(vel);
            });
        }
    }

    private static final Vector SLIME_BLOCK_VELOCITY = new Vector(0d, .4d, 0d);

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStandEquip(PlayerArmorStandManipulateEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJumpSlimeBlock(PlayerMoveEvent event) {
        if(event.getTo().getY() <= event.getFrom().getY()) return; // falling or not moving at all
        Location loc = event.getPlayer().getLocation();
        if(loc.getBlockY() >= loc.getY()-.2d) return; // not jumping
        Block b = loc.getBlock().getRelative(BlockFace.DOWN);
        if(b.getType() != Material.SLIME_BLOCK) return;
        Vector vel = event.getPlayer().getVelocity().add(SLIME_BLOCK_VELOCITY);
        event.getPlayer().setVelocity(vel);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHungerChange(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if(Player.class.isAssignableFrom(event.getWhoClicked().getClass()) && perm((Player) event.getWhoClicked())) return;
        if(event.getClickedInventory() == null) return;
        if(event.getClickedInventory().getType() == InventoryType.PLAYER) {
            event.setCancelled(true);
        }
    }
}
