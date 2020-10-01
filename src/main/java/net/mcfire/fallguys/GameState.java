package net.mcfire.fallguys;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public interface GameState extends Listener, Runnable {
    default void onEnterState() { }
    default void onLeaveState() { }

    default boolean onPlayerJoin(Player player) { return true; }
    default void onPlayerQuit(Player player) { }

    @Override
    default void run() { }
}
