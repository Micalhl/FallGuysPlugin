package net.mcfire.fallguys.utils;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class ItemTools {

    public static ItemStack item(Material material, String name) {
        return item(new ItemStack(material), name, null, false);
    }

    public static ItemStack item(ItemStack item, String name) {
        return item(item, name, null, false);
    }

    public static ItemStack item(Material material, String name, boolean enchanted) {
        return item(new ItemStack(material), name, null, enchanted);
    }

    public static ItemStack item(ItemStack item, String name, boolean enchanted) {
        return item(item, name, null, enchanted);
    }

    public static ItemStack item(ItemStack item, String name, List<String> lore, boolean enchanted) {
        ItemMeta m = item.getItemMeta();
        m.setUnbreakable(true);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        m.setDisplayName(name);
        if(enchanted) m.addEnchant(Enchantment.DURABILITY, 1, true);
        if(lore != null) {
            m.setLore(lore);
        }
        item.setItemMeta(m);
        return item;
    }

}
