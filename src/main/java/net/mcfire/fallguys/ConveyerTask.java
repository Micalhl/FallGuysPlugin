package net.mcfire.fallguys;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class ConveyerTask implements Runnable {
    @Override
    public void run() {
        Bukkit.getOnlinePlayers().forEach(this::process);
    }

    private void process(Player player) {
        Location location = player.getLocation();
        if(location.getY() - location.getBlockY() > 0.2d) return;
        Block stepping = location.getBlock().getRelative(BlockFace.DOWN);
        if(stepping.getType() != Material.MAGENTA_GLAZED_TERRACOTTA) return;
        Bukkit.broadcastMessage(stepping.getBlockData().getClass().getSimpleName());
        Directional data = (Directional) stepping.getBlockData();
        Vector vel = data.getFacing().getOppositeFace().getDirection().multiply(0.25).setY(0.15);
        player.setVelocity(vel);
    }
}
