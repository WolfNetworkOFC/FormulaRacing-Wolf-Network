package dev.EfraGroup.formulaRacing.Cosmetics;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BoatTrailManager {

    private final JavaPlugin plugin;
    private final Map<UUID, BlockDisplay> trails = new HashMap<>();
    private final Team noCollision;

    public BoatTrailManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.noCollision = setupNoCollisionTeam();
        startUpdateTask();
    }

    /** Cria o team que remove colisão */
    private Team setupNoCollisionTeam() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = sb.getTeam("no_collision_trails");

        if (t == null) t = sb.registerNewTeam("no_collision_trails");

        t.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        return t;
    }

    /** Ativa rastro */
    public void setTrail(Boat boat) {
        if (boat == null) return;

        UUID id = boat.getUniqueId();
        if (trails.containsKey(id)) return;

        BlockDisplay display = boat.getWorld().spawn(boat.getLocation(), BlockDisplay.class);

        display.setBlock(Bukkit.createBlockData(Material.LIGHTNING_ROD));

        // physics
        display.setGravity(false);
        display.setInvulnerable(true);

        // remove sombra
        display.setShadowRadius(0f);
        display.setShadowStrength(0f);

        // remove colisão via scoreboard
        noCollision.addEntry(display.getUniqueId().toString());

        // escala bonita
        display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new Quaternionf(),
                new Vector3f(0.7f, 0.7f, 0.7f),
                new Quaternionf()
        ));

        trails.put(id, display);
    }

    /** Remove rastro */
    public void removeTrail(Boat boat) {
        if (boat == null) return;

        BlockDisplay display = trails.remove(boat.getUniqueId());
        if (display != null && !display.isDead()) {
            display.remove();
        }
    }

    /** Atualiza displays */
    private void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                trails.entrySet().removeIf(entry -> {
                    UUID id = entry.getKey();
                    BlockDisplay display = entry.getValue();
                    Boat boat = (Boat) Bukkit.getEntity(id);

                    if (boat == null || boat.isDead() || display.isDead()) {
                        if (display != null) display.remove();
                        return true;
                    }

                    updateDisplayPosition(boat, display);
                    return false;
                });

            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Move o display para trás do barco */
    private void updateDisplayPosition(Boat boat, BlockDisplay display) {
        Location boatLoc = boat.getLocation();
        Vector dir = boatLoc.getDirection().normalize();

        Location behind = boatLoc.clone().subtract(dir.multiply(1.0));
        behind.add(0, -0.4, 0); // ajuste fino

        display.teleport(behind);
    }
}
