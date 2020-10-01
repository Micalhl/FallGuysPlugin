package net.mcfire.fallguys.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class CustomModelUtils {

    public static ItemStack generateItem(int modelId) {
        ItemStack HEAD = new ItemStack(Material.GOLDEN_HOE);
        ItemMeta m = HEAD.getItemMeta();
        m.setCustomModelData(modelId);
        HEAD.setItemMeta(m);
        return HEAD;
    }

}
