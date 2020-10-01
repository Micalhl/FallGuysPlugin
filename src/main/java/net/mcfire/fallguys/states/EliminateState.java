package net.mcfire.fallguys.states;

import de.myzelyam.api.vanish.VanishAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.Trait;
import net.mcfire.fallguys.FallGuys;
import net.mcfire.fallguys.GameState;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;

public class EliminateState implements GameState {

    private static final ItemStack ITEM_SURVIVED = new ItemStack(Material.LIME_CONCRETE);
    private static final ItemStack ITEM_ELIMINATED = new ItemStack(Material.RED_CONCRETE);

    private final Set<UUID> eliminatedSet;
    private final List<UUID> eliminated;
    private final int totalEliminated;

    public EliminateState(Collection<UUID> eliminated) {
        this.eliminated = new ArrayList<>(eliminated);
        this.eliminatedSet = Collections.unmodifiableSet(new HashSet<>(eliminated));
        totalEliminated = eliminatedSet.size();
    }


    private Map<UUID, NPC> npcs = new HashMap<>();
    private List<UUID> uuids = new ArrayList<>();
    private boolean allSpawned = false;
    private boolean animationFinished = false;
    private int finishCountdown = 5;

    @Override
    public void onEnterState() {
        Location spawn = FallGuys.getInstance().readConfigLocation("eliminate.spawn");
        Bukkit.getOnlinePlayers().forEach(p -> {
            VanishAPI.hidePlayer(p);
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(spawn);
            p.stopSound("fallguys.bgm");
        });

        Location display = FallGuys.getInstance().readConfigLocation("eliminate.display.start");
        final int length = FallGuys.getInstance().getConfig().getInt("eliminate.display.length");
        final int height = FallGuys.getInstance().getConfig().getInt("eliminate.display.height");

        // 播放军鼓的声音
        Bukkit.getOnlinePlayers().forEach(p -> {
            p.playSound(p.getLocation(), "fallguys.eliminate.drum_full", SoundCategory.MASTER, 1.0f, 1.0f);
        });

        List<Integer> pool = new ArrayList<>();
        for(int i = 0; i < length * height; i++) pool.add(i);
        Random rnd = new Random(System.currentTimeMillis() - Bukkit.getOnlinePlayers().size());
        for(Player player : FallGuys.getInstance().getPlayingPlayers()) {
            UUID uuid = player.getUniqueId();

            final int i;
            if(pool.size() > 0) {
                i = pool.remove(rnd.nextInt(pool.size()));
            } else i = 0;

            final boolean bad = eliminatedSet.contains(player.getUniqueId());
            final String npcName = String.format("\u00a7%s%s", bad ? "c" : "a", player.getName());
            NPC npc = FallGuys.getInstance().getNpcRegistry().createNPC(
                EntityType.PLAYER,
                npcName
            );
            npc.addTrait(new Trait(String.format("_%d", System.currentTimeMillis())) {
                @Override
                public void onSpawn() {
                    ((Player) getNPC().getEntity()).getInventory().setItemInMainHand(bad ? ITEM_ELIMINATED : ITEM_SURVIVED);
                }
            });
            npcs.put(uuid, npc);
            uuids.add(uuid);

            Location at = display.clone();
            int xDiff = i % length;
            int yDiff = i / length;

            xDiff *= 4;
            yDiff *= 4;

            at.setYaw(180.f);
            at.add(xDiff + .5d, yDiff, .5d);

            Bukkit.broadcastMessage(String.format("NPC #%d at -> %s", i, at.toString()));

            npc.spawn(at);
        }
    }

    @Override
    public void run() {
        if(!allSpawned) {
            if(npcs.values().stream().allMatch(NPC::isSpawned)) {
                allSpawned = true;
                finishCountdown = 5;
            } else return;
        } else {
            if(!animationFinished) {
                if(finishCountdown >= 0) {
                    finishCountdown --;
                    return;
                }
                if (eliminated.size() <= 0) {
                    Bukkit.broadcastMessage("\u00a76淘汰完成! ");
                    Bukkit.getOnlinePlayers().forEach(p->p.stopSound("fallguys.eliminate.drum_full"));
                    FallGuys.playSoundForAll("fallguys.eliminate.finished");

                    animationFinished = true;
                    finishCountdown = 5;
                    return;
                }
                UUID u = eliminated.remove(0);
                NPC x = npcs.remove(u);
                x.getEntity().setVelocity(new Vector(0, 1.5, -2));

                float pitch = .9f + (1f - ((float)eliminated.size()) / ((float)totalEliminated)) * .3f;
                FallGuys.playSoundForAll("fallguys.eliminate.push", pitch);

                Bukkit.getScheduler().runTaskLater(FallGuys.getInstance(), x::destroy, 40L); // destroy
            } else {
                if(finishCountdown <= 0) {
                    FallGuys.getInstance().getPlayingPlayers().forEach(p -> {
                        if(eliminatedSet.contains(p.getUniqueId())) {
                            FallGuys.getInstance().removePlaying(p);
                            p.sendTitle("\u00a7b\u00a7l你被淘汰啦！", "Good luck next time! ", 10, 20, 10);
                        }
                    });
                    FallGuys.getInstance().enterState(new ResetState(
                        FallGuys.getInstance().getPlayingPlayers().size() <= 0
                    ));
                } else {
                    finishCountdown--;
                }
            }
        }
    }

    @Override
    public void onLeaveState() {
        FallGuys.getInstance().getNpcRegistry().forEach(NPC::destroy);
    }

}
