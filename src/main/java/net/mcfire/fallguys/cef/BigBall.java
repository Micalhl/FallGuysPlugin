package net.mcfire.fallguys.cef;

import de.tr7zw.nbtapi.NBTCompound;
import io.github.definitlyevil.bukkitces.CustomEntityFramework;
import io.github.definitlyevil.bukkitces.entities.base.BaseCustomEntity;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

public class BigBall extends BaseCustomEntity {
    public static final String TYPE = "BigBall";

    private Vector vel = null;

    private static final ItemStack HEAD = new ItemStack(Material.GOLDEN_HOE);
    static {
        ItemMeta m = HEAD.getItemMeta();
        m.setCustomModelData(1);
        HEAD.setItemMeta(m);
    }

    @Override
    protected void _setup(ArmorStand armorStand, NBTCompound nbtCompound) {
        armorStand.getEquipment().setHelmet(HEAD);
    }

    public Vector getVelocity() {
        return vel;
    }

    public void setVelocity(Vector vel) {
        this.vel = vel;
    }

    @Override
    protected void _preUpdate(boolean force) {
        if(vel != null) {
            setLocation(getLocation().add(vel));
        }
    }

    @Override
    protected void _postUpdate(boolean force) {
        Location location = getLocation();
        for(Entity entity : location.getWorld().getNearbyEntities(location, 1.6d, 1.6d, 1.6d)) {
            if(entity == getBukkitEntity()) continue;
            if(CustomEntityFramework.isCEEntity(entity)) continue;
            if(entity.getType() == EntityType.PLAYER && ((Player) entity).getGameMode() != GameMode.ADVENTURE) continue;
            Vector newVel = entity.getLocation().subtract(location).toVector().normalize().multiply(0.4d);
            newVel.setY(0.6d);
            entity.setVelocity(newVel);
        }

    }

    @Override
    public String getType() {
        return TYPE;
    }
}
