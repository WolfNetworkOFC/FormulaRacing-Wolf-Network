//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

public class PodiumManager implements Listener {
    private final FormulaRacing plugin;
    private boolean isCeremonyActive = false;
    private static final Location AUDIENCE_LOCATION = new Location(Bukkit.getWorld("world"), (double)-18.0F, (double)132.0F, (double)50.0F, -180.0F, 0.0F);
    private static final Location PODIUM_1 = new Location(Bukkit.getWorld("world"), (double)-18.0F, (double)139.0F, (double)29.0F, 0.0F, 0.0F);
    private static final Location PODIUM_2 = new Location(Bukkit.getWorld("world"), (double)-25.0F, (double)138.0F, (double)29.0F, 0.0F, 0.0F);
    private static final Location PODIUM_3 = new Location(Bukkit.getWorld("world"), (double)-11.0F, (double)137.0F, (double)29.0F, 0.0F, 0.0F);

    public PodiumManager(FormulaRacing plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void startCeremony(Events event, List<Driver> results) {
        if (results != null && !results.isEmpty()) {
            this.plugin.getDebugManager().logRaceSystem("Iniciando cerimônia de pódio para evento: " + event.getDisplayName());
            this.isCeremonyActive = true;

            for(Player p : Bukkit.getOnlinePlayers()) {
                p.teleport(AUDIENCE_LOCATION);
                p.getInventory().addItem(new ItemStack[]{new ItemStack(Material.SNOWBALL, 1)});
                p.sendMessage("§b❄ Você recebeu uma bola de neve para celebrar!");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
            }

            (new PodiumRunnable(results)).runTaskTimer(this.plugin, 60L, 60L);
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        if (this.isCeremonyActive) {
            if (e.getEntity() instanceof Snowball) {
                ProjectileSource var3 = e.getEntity().getShooter();
                if (var3 instanceof Player) {
                    final Player p = (Player)var3;
                    (new BukkitRunnable() {
                        public void run() {
                            if (p.isOnline()) {
                                p.getInventory().setItemInMainHand(new ItemStack(Material.SNOWBALL, 1));
                            }

                        }
                    }).runTaskLater(this.plugin, 1L);
                }
            }

        }
    }

    private void endCeremony() {
        (new BukkitRunnable() {
            public void run() {
                PodiumManager.this.isCeremonyActive = false;

                for(Player p : Bukkit.getOnlinePlayers()) {
                    p.getInventory().remove(Material.SNOWBALL);
                    p.sendMessage("§eCerimônia encerrada.");
                }

            }
        }).runTaskLater(this.plugin, 200L);
    }

    private class PodiumRunnable extends BukkitRunnable {
        private final List<Driver> results;
        private int currentPositionIndex;

        public PodiumRunnable(List<Driver> results) {
            this.results = results;
            this.currentPositionIndex = Math.min(results.size(), 15) - 1;
        }

        public void run() {
            if (this.currentPositionIndex < 0) {
                PodiumManager.this.endCeremony();
                this.cancel();
            } else {
                int position = this.currentPositionIndex + 1;
                Driver driver = (Driver)this.results.get(this.currentPositionIndex);
                Player player = Bukkit.getPlayer(driver.getUuid());
                String driverName = player != null ? player.getName() : Bukkit.getOfflinePlayer(driver.getUuid()).getName();
                String title = "";
                String subtitle = "§f" + driverName;
                String chatMessage = "";
                if (position == 1) {
                    title = "§e§l\ud83e\udd47 1º LUGAR";
                    chatMessage = "§e§l\ud83e\udd47 1º Lugar: §f" + driverName;
                } else if (position == 2) {
                    title = "§7§l\ud83e\udd48 2º LUGAR";
                    chatMessage = "§7§l\ud83e\udd48 2º Lugar: §f" + driverName;
                } else if (position == 3) {
                    title = "§6§l\ud83e\udd49 3º LUGAR";
                    chatMessage = "§6§l\ud83e\udd49 3º Lugar: §f" + driverName;
                } else {
                    title = "§b" + position + "º LUGAR";
                    chatMessage = "§b" + position + "º Lugar: §f" + driverName;
                }

                for(Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle(title, subtitle, 0, 60, 20);
                    p.sendMessage(chatMessage);
                }

                if (position <= 3) {
                    for(Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
                    }

                    if (player != null && player.isOnline()) {
                        Location var10000;
                        switch (position) {
                            case 1 -> var10000 = PodiumManager.PODIUM_1;
                            case 2 -> var10000 = PodiumManager.PODIUM_2;
                            case 3 -> var10000 = PodiumManager.PODIUM_3;
                            default -> var10000 = null;
                        }

                        Location loc = var10000;
                        if (loc != null) {
                            player.teleport(loc);
                            Location fireworkLoc = loc.clone().add((double)0.0F, (double)1.0F, (double)0.0F);
                            player.getWorld().spawnParticle(Particle.FIREWORK, fireworkLoc, 50);
                        }
                    }
                } else {
                    for(Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.0F);
                    }
                }

                --this.currentPositionIndex;
            }
        }
    }
}
