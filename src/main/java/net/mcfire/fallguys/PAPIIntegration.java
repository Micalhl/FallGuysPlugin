package net.mcfire.fallguys;

import me.clip.placeholderapi.PlaceholderHook;
import org.bukkit.entity.Player;

public class PAPIIntegration extends PlaceholderHook {

    @Override
    public String onPlaceholderRequest(Player p, String params) {
        if(params == null || params.isEmpty()) return "<NO_PARAM>";
        return "<?>";
    }
}
