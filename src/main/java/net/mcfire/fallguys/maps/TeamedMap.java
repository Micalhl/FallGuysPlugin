package net.mcfire.fallguys.maps;

import net.mcfire.fallguys.FallGuys;
import net.mcfire.fallguys.states.MatchState;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.*;

/**
 * 团队竞技地图基础类
 */
public abstract class TeamedMap extends BaseMap {

    private int teamCount;
    protected Map<Integer, TeamColor> teamColor;

    // 玩家的队伍，从头到尾是不能改变的
    protected Map<UUID, Integer> teamMap;
    protected Map<Integer, List<UUID>> teamMemberListMap;

    public TeamedMap(MatchState state) {
        super(state);
    }

    @Override
    public final void onMapLoad() {
        teamCount = internal_getTeamCount(); // 获取队伍库
        // 随机给每一个队伍分配一个颜色
        List<TeamColor> colorPool = new ArrayList<>(Arrays.asList(TeamColor.values())); // 颜色池，所有的颜色会从这个里边来找
        if(teamCount > 5) throw new IllegalArgumentException("invalid team count");
        teamColor = new HashMap<>();
        Random random = new Random(System.currentTimeMillis() - FallGuys.getInstance().getPlayingPlayers().size());
        for(int i = 0; i < teamCount; i++) {
            int index = random.nextInt(colorPool.size());
            TeamColor c = colorPool.remove(index);
            teamColor.put(i, c); // 设置玩家的队伍
        }
        // 把队伍玩家随机放进队伍中，并放上带颜♂色的胸♂甲
        {
            teamMap = new HashMap<>();
            teamMemberListMap = new HashMap<>();
            final List<Player> playing = FallGuys.getInstance().getPlayingPlayers();
            int teamMemberAmount = playing.size() / teamCount; // 算出每一队的人数
            for(int i = 0; i < teamCount; i++) {
                List<UUID> ml = new ArrayList<>(playing.size());
                teamMemberListMap.put(i, ml);
                // 循环遍历每一个队伍，把人塞进去
                for(int j = 0; j < teamMemberAmount; j++) {
                    int pIndex = random.nextInt(playing.size());
                    Player player = playing.remove(pIndex);
                    setPlayerTeam(player, i);
                }
            }
            // 多余了几个玩家，随机塞进去
            while(playing.size() > 0) {
                final int pIndex = playing.size()-1;
                final int team = random.nextInt(teamCount);
                Player player = playing.remove(pIndex);
                setPlayerTeam(player, team);
            }
        }
        _internal_onMapLoad();
    }

    protected void _internal_onMapLoad() { }

    public int getTeamCount() {
        return teamCount;
    }

    private void setPlayerTeam(Player player, int teamIndex) {
        if(teamIndex >= teamCount) throw new IllegalArgumentException("invalid team");
        final TeamColor color = teamColor.get(teamIndex);
        teamMap.put(player.getUniqueId(), teamIndex); // 塞♂进去
        player.getInventory().clear();
        player.getInventory().setChestplate(generateChestplate(color)); // 胸♂甲套上
        teamMemberListMap.get(teamIndex).add(player.getUniqueId());
        player.sendMessage(String.format("\u00a76你被分配到了第 %s\u00a76 队", color.display));
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(String.format("\u00a76\u00a7l你是 %s\u00a76\u00a7l 队", color.display)));
    }

    /**
     * 淘汰整个队伍
     * @param teamIndex 队伍id
     * @param eliminate true的话就淘汰，false就胜出
     */
    public final void changeTeamState(int teamIndex, boolean eliminate) {
        checkTeams();
        List<UUID> members = teamMemberListMap.get(teamIndex);
        members.removeIf(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if(p == null || !p.isOnline()) return true;
            if(eliminate) {
                // 淘汰啦
                state.eliminatePlayer(p);;
            } else {
                // 达标啦
                state.playerFinish(p);
            }
            return false;
        });
    }

    @Override
    public final void tick() {
        checkTeams();
        _internal_tick();
    }

    protected abstract void _internal_tick();

    protected final void checkTeams() {
        // 清除离线了的玩家
        List<UUID> removed = new LinkedList<>();
        teamMap.entrySet().removeIf(e -> {
            final UUID uuid = e.getKey();
            Player p = Bukkit.getPlayer(uuid);
            boolean remove =  p == null || !p.isOnline() || !FallGuys.getInstance().isPlayerPlaying(p) || state.isEndedForPlayer(p);
            if(remove) removed.add(uuid);
            return remove;
        });
        teamMemberListMap.forEach((k, v) -> v.removeAll(removed)); // 删掉所有已经结束游戏的玩家
    }

    /**
     * 尝试获取玩家的队伍
     * @param uuid
     * @return
     */
    public Integer getPlayerTeam(UUID uuid) {
        return teamMap.get(uuid);
    }

    protected abstract int internal_getTeamCount();

    /**
     * 获得一个特定颜♂色的胸♂甲
     * @param color
     * @return
     */
    private static ItemStack generateChestplate(TeamColor color) {
        ItemStack itemStack = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta m = (LeatherArmorMeta) itemStack.getItemMeta();
        m.setColor(color.color);
        itemStack.setItemMeta(m);
        return itemStack;
    }

    public enum TeamColor {
        // Color.RED, Color.LIME, Color.BLUE, Color.PURPLE, Color.ORANGE
        RED(Color.RED, "\u00a7c\u00a7l红队"),
        LIME(Color.LIME, "\u00a7a\u00a7l青柠队"),
        BLUE(Color.BLUE, "\u00a7b\u00a7l蓝队"),
        PURPLE(Color.PURPLE, "\u00a7d\u00a7l紫队"),
        ORANGE(Color.ORANGE, "\u00a76\u00a7l橙队");

        public final Color color;
        public final String display;

        TeamColor(Color color, String display) {
            this.color = color;
            this.display = display;
        }
    }

}
