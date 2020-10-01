package net.mcfire.fallguys.cef;

import de.tr7zw.nbtapi.NBTCompound;
import io.github.definitlyevil.bukkitces.api.CustomEntity;
import io.github.definitlyevil.bukkitces.entities.base.BaseCustomEntity;
import net.mcfire.fallguys.FallGuys;
import net.mcfire.fallguys.utils.CustomModelUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;

public class MoveableBlock extends BaseCustomEntity {
    public static final String TYPE = "MoveableBlock";

    private static ItemStack HEAD = CustomModelUtils.generateItem(7);

    public static final int WIDTH = 4;
    public static final int HEIGHT = 3;

    private static final int WIDTH_HALF = WIDTH/2;

    /**
     * 裆前举起这个方块的实体
     */
    private WeakReference<Player> holder = null;

    /**
     * 举起这个方块的玩家offset
     */
    private Vector holderOffset = null;

    /**
     * 修改了的方块
     */
    private List<Location> modifiedBlocks = new LinkedList<>();

    @Override
    protected void _setup(ArmorStand armorStand, NBTCompound nbtCompound) {
        armorStand.getEquipment().setHelmet(HEAD);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    protected void _preUpdate(boolean force) {
        // 如果当前有玩家拿这个方块，那么就同步位置
        if(holder != null) {
            Player p = holder.get();
            if(p == null || !p.isOnline()) {
                holder = null;
                holderOffset = null;
                return;
            }
            // 更新实体位置
            Location newLocation = p.getLocation().add(holderOffset);
            newLocation.setX(newLocation.getBlockX());
            newLocation.setY(newLocation.getBlockY());
            newLocation.setZ(newLocation.getBlockZ());
            setLocation(newLocation);
        }
    }

    /**
     * 清除掉修改过的方块
     */
    private void removeAllModifiedBlocks() {
        modifiedBlocks.removeIf(l -> {
            Block b = l.getBlock();
            if(b.getType() == Material.LADDER) {
                l.getBlock().setType(Material.AIR);
                return true;
            } else return false;
        });
        modifiedBlocks.removeIf(l -> {
            Block b = l.getBlock();
            if(b.getType() == Material.BARRIER) {
                l.getBlock().setType(Material.AIR);
                return true;
            } else return false;
        });
        modifiedBlocks.clear();
    }

    /**
     * 创建新的方块
     */
    private void modifyBlocks() {
        if (modifiedBlocks.size() > 0) removeAllModifiedBlocks();
        Location loc = getLocation();
        loc.setX(loc.getBlockX());
        loc.setY(loc.getBlockY());
        loc.setZ(loc.getBlockZ());
        for (int dy = -HEIGHT; dy < HEIGHT; dy++) {
            for (int dx = -WIDTH_HALF; dx < WIDTH_HALF; dx++) {
                for (int dz = -WIDTH_HALF; dz < WIDTH_HALF; dz++) {
                    Location blockLocation = loc.clone().add(dx + .5d, dy + .5d, dz + .5d);
                    Block b = loc.getWorld().getBlockAt(
                        blockLocation.getBlockX(),
                        blockLocation.getBlockY(),
                        blockLocation.getBlockZ()
                    );
                    if (b.isEmpty()) {
                        b.setType(Material.BARRIER);
                        modifiedBlocks.add(blockLocation);
                    }
                }
            }
        }
        /* 放置梯子 */
        {
            Location lSouth = loc.clone().add(-WIDTH_HALF, 0, WIDTH_HALF);
            Location lNorth = loc.clone().add(-WIDTH_HALF,0,-WIDTH_HALF-1);
            Location lEast = loc.clone().add(WIDTH_HALF,0,-WIDTH_HALF);
            Location lWest = loc.clone().add(-WIDTH_HALF-1,0,-WIDTH_HALF);
            for (int dw = 0; dw < WIDTH; dw++) {
                for (int dy = 1; dy < HEIGHT-1; dy++) {
                    Location loc1 = lSouth.clone().add(dw, dy, 0);
                    Location loc2 = lNorth.clone().add(dw, dy, 0);
                    Location loc3 = lEast.clone().add(0,dy,dw);
                    Location loc4 = lWest.clone().add(0,dy,dw);
                    Block block1 = loc1.getBlock();
                    Block block2 = loc2.getBlock();
                    Block block3 = loc3.getBlock();
                    Block block4 = loc4.getBlock();
                    if(block1.isEmpty()) {
                        block1.setType(Material.LADDER);
                        setBlockDirection(block1, BlockFace.SOUTH);
                        modifiedBlocks.add(loc1);
                    }
                    if(block2.isEmpty()) {
                        block2.setType(Material.LADDER);
                        setBlockDirection(block2, BlockFace.NORTH);
                        modifiedBlocks.add(loc2);
                    }
                    if(block3.isEmpty()) {
                        block3.setType(Material.LADDER);
                        setBlockDirection(block3, BlockFace.EAST);
                        modifiedBlocks.add(loc3);
                    }
                    if(block4.isEmpty()) {
                        block4.setType(Material.LADDER);
                        setBlockDirection(block4, BlockFace.WEST);
                        modifiedBlocks.add(loc4);
                    }
                }
            }
        }
    }

    private static void setBlockDirection(Block block, BlockFace direction) {
        Directional directional = (Directional) block.getBlockData();
        directional.setFacing(direction);
        block.setBlockData(directional);
    }


    /**
     * 用来处理物理计算的一个监听器模块
     */
    public static class MoveableBlockPhysicsListener implements Listener {
        private static final MoveableBlockPhysicsListener INSTANCE = new MoveableBlockPhysicsListener();

        private static String META_HOLDING = "FallGuys::CEF::MoveableBlock::Holding";
        private static String META_LAST_PICK = "FallGuys::CEF::MoveableBlock::LastPick";

        private MoveableBlockPhysicsListener() { }

        public static void register(Plugin plugin) {
            HandlerList.unregisterAll(INSTANCE);
            Bukkit.getPluginManager().registerEvents(INSTANCE, plugin);
        }

        /**
         * 使用右键捡起这个方块，移动的话可以让方块和玩家一起移动。
         * @param event
         */
        @EventHandler(priority = EventPriority.NORMAL)
        public void onPickUpEntity(PlayerInteractEvent event) {
            if(event.getHand() != EquipmentSlot.HAND || !event.getAction().name().startsWith("LEFT_CLICK_")) return;
            Player player = event.getPlayer();
            if((!player.hasMetadata(META_LAST_PICK) || (System.currentTimeMillis() - player.getMetadata(META_LAST_PICK).get(0).asLong()) > 1000L ) && player.hasMetadata(META_HOLDING)) {                // 已经有了拿着的实体
                WeakReference<MoveableBlock> refHolding = (WeakReference<MoveableBlock>) player.getMetadata(META_HOLDING).get(0).value();
                MoveableBlock holding = refHolding != null ? refHolding.get() : null;
                player.removeMetadata(META_HOLDING, FallGuys.getInstance());
                event.setCancelled(true);
                if(holding != null) {
                    // 取消拿着
                    holding.holder = null;
                    holding.holderOffset = null;
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("\u00a7a你放下了方块! "));
                    holding.modifyBlocks();
                }
                return;
            }
            Location playerLocation = player.getEyeLocation();
            World world = player.getWorld();
            // 射线追踪，碰撞到实体上
            RayTraceResult rt = world.rayTraceEntities(
                playerLocation,
                playerLocation.getDirection(),
                4.5d,
                2,
                (_e) -> {
                    if (!_e.hasMetadata(CustomEntity.ENTITY_META) || !ArmorStand.class.isAssignableFrom(_e.getClass())) return false;
                    if (!MoveableBlock.class.isAssignableFrom(_e.getMetadata(CustomEntity.ENTITY_META).get(0).value().getClass())) return false;
                    return true;
                }
            );
            if(rt == null || rt.getHitEntity() == null) return; // 没有点到
            
            event.setCancelled(true); // CANCEL

            ArmorStand as = (ArmorStand) rt.getHitEntity();
            // 获得了玩家点击的实体
            MoveableBlock moveableBlock = (MoveableBlock) as.getMetadata(CustomEntity.ENTITY_META).get(0).value();
            player.sendMessage("点击了实体 @ " + moveableBlock.getLocation().toString());
            moveableBlock.holder = new WeakReference<>(player);
            moveableBlock.holderOffset = moveableBlock.getLocation().subtract(player.getLocation()).toVector();
            moveableBlock.holderOffset.setY(0d);
            moveableBlock.holderOffset.add(moveableBlock.holderOffset.clone().normalize().multiply(2d));
            player.setMetadata(META_HOLDING, new FixedMetadataValue(FallGuys.getInstance(),
                    new WeakReference<>(moveableBlock) // 弱引用，避免内存泄漏
                ));
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("\u00a7a你拿起了方块! "));
            moveableBlock.removeAllModifiedBlocks();
            player.setMetadata(META_LAST_PICK, new FixedMetadataValue(FallGuys.getInstance(), System.currentTimeMillis()));
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPlayerQuitRemoveMeta(PlayerQuitEvent event) {
            event.getPlayer().removeMetadata(META_HOLDING, FallGuys.getInstance());
        }
    }
}
