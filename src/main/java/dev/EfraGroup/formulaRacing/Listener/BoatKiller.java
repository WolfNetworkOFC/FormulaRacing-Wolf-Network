package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.AI.AIOpponentManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Removes orphaned boats (no player riding) when a player loads their chunk.
 * Ported from IBRUtils BoatKiller.
 *
 * <p>FormulaRacing-only guard: AI-controlled boats also have no Player passenger
 * (a client-side fake NPC rides them), so they are collected from the
 * AIOpponentManager and skipped — otherwise the AI opponents would be deleted
 * the moment any player loaded their chunk.
 */
public class BoatKiller implements Listener {

    private final FormulaRacing plugin;

    public BoatKiller(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(PlayerChunkLoadEvent event) {
        Set<UUID> aiBoats = collectAIBoatIds();

        Arrays.stream(event.getChunk().getEntities())
                .filter(Boat.class::isInstance)
                .filter(entity -> entity.getPassengers().stream().noneMatch(Player.class::isInstance))
                .filter(entity -> !aiBoats.contains(entity.getUniqueId()))
                .forEach(entity -> {
                    // Entity.remove() must run on the owning region thread (Folia).
                    Entity target = entity;
                    SchedulerHelper.runTaskFor(plugin, target, () -> {
                        if (!target.isValid()) {
                            return;
                        }
                        if (target.getPassengers().stream().noneMatch(Player.class::isInstance)) {
                            target.getPassengers().forEach(Entity::remove);
                            target.remove();
                        }
                    });
                });
    }

    private Set<UUID> collectAIBoatIds() {
        Set<UUID> aiBoats = new HashSet<>();
        AIOpponentManager aiManager = plugin.getAIOpponentManager();
        if (aiManager == null) {
            return aiBoats;
        }
        for (AIOpponentManager.AIOpponent ai : aiManager.getAIOpponents().values()) {
            Entity boat = ai.getControlledEntity();
            if (boat != null) {
                aiBoats.add(boat.getUniqueId());
            }
        }
        return aiBoats;
    }
}