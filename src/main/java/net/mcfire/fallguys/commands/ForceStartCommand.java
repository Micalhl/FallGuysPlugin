package net.mcfire.fallguys.commands;

import net.mcfire.fallguys.FallGuys;
import net.mcfire.fallguys.GameState;
import net.mcfire.fallguys.states.WaitState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ForceStartCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(!sender.hasPermission("fallguys.admin")) {
            sender.sendMessage("\u00a7c你个闸总，没权限还敢得瑟！！！ ");
            return true;
        }
        GameState state = FallGuys.getInstance().getState();
        if(WaitState.class.isAssignableFrom(state.getClass())) {
            ((WaitState) state).doStartGame();
        }
        return true;
    }
}
