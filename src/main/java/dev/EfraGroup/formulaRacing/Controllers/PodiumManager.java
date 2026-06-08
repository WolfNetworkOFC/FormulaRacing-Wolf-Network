package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Utils.TitleHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class PodiumManager implements Listener {
    private static final String CONFIG_ROOT = "podium";
    private static final List<String> REQUIRED_LOCATIONS = List.of("audience", "p1", "p2", "p3", "lobby");

    private final FormulaRacing plugin;
    private boolean ceremonyActive = false;
    private PodiumCeremonySession activeSession;

    public PodiumManager(FormulaRacing plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public synchronized boolean isCeremonyActive() {
        return this.ceremonyActive;
    }

    public synchronized void startCeremony(Events event, List<Driver> results) {
        if (event == null || results == null || results.isEmpty()) {
            this.plugin.getDebugManager().logRaceSystem("Cerimonia de podio ignorada: evento/resultados invalidos.");
            return;
        }

        if (this.ceremonyActive) {
            this.plugin.getDebugManager().logRaceSystem("Cerimonia de podio ja ativa. Ignorando nova chamada para " + event.getDisplayName());
            return;
        }

        if (!this.config().getBoolean(CONFIG_ROOT + ".enabled", true)) {
            this.plugin.getDebugManager().logRaceSystem("Cerimonia de podio desabilitada em config.yml.");
            return;
        }

        for (String key : REQUIRED_LOCATIONS) {
            if (this.getConfiguredLocation(key) == null) {
                this.plugin.getDebugManager().logRaceSystem("Cerimonia de podio abortada: localizacao invalida em '" + CONFIG_ROOT + ".locations." + key + "'.");
                return;
            }
        }

        List<Driver> sortedResults = new ArrayList<>(results);
        sortedResults.sort(Comparator.comparingInt(Driver::getPosition));
        int topLimit = Math.max(1, this.config().getInt(CONFIG_ROOT + ".top-limit", 15));
        int topLimitUsed = Math.min(topLimit, sortedResults.size());
        if (topLimitUsed <= 0) {
            this.plugin.getDebugManager().logRaceSystem("Cerimonia de podio abortada: sem posicoes para revelar.");
            return;
        }

        Set<UUID> participants = this.resolveCeremonyParticipants(event);
        if (participants.isEmpty()) {
            this.plugin.getDebugManager().logRaceSystem("Cerimonia de podio abortada: sem participantes do evento.");
            return;
        }

        this.plugin.getDebugManager().logRaceSystem("Iniciando cerimonia de podio para evento: " + event.getDisplayName());
        this.ceremonyActive = true;

        PodiumCeremonySession session = new PodiumCeremonySession(
                event,
                Collections.unmodifiableList(sortedResults),
                Collections.unmodifiableSet(participants),
                topLimitUsed,
                new AtomicInteger(topLimitUsed)
        );
        this.activeSession = session;

        this.teleportAudienceAndPrepare(session);

        long periodTicks = Math.max(1L, this.config().getLong(CONFIG_ROOT + ".reveal-interval-ticks", 50L));
        session.revealTask = SchedulerHelper.runTaskTimer(this.plugin, new PodiumRunnable(session), periodTicks, periodTicks);
    }

    public boolean setLocation(String key, Location location) {
        if (!this.isLocationKey(key) || location == null || location.getWorld() == null) {
            return false;
        }

        String base = CONFIG_ROOT + ".locations." + key.toLowerCase();
        FileConfiguration cfg = this.config();
        cfg.set(base + ".world", location.getWorld().getName());
        cfg.set(base + ".x", location.getX());
        cfg.set(base + ".y", location.getY());
        cfg.set(base + ".z", location.getZ());
        cfg.set(base + ".yaw", location.getYaw());
        cfg.set(base + ".pitch", location.getPitch());
        this.plugin.getFileManager().saveConfig();
        return true;
    }

    public Location getConfiguredLocation(String key) {
        if (!this.isLocationKey(key)) {
            return null;
        }

        String base = CONFIG_ROOT + ".locations." + key.toLowerCase();
        ConfigurationSection section = this.config().getConfigurationSection(base);
        if (section == null) {
            return null;
        }

        String worldName = section.getString("world", "");
        if (worldName.isBlank()) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw", 0.0D),
                (float) section.getDouble("pitch", 0.0D)
        );
    }

    public List<String> getConfigStatusLines() {
        List<String> lines = new ArrayList<>();
        FileConfiguration cfg = this.config();

        lines.add(ChatColor.GOLD + "[Podio] " + ChatColor.GRAY + "enabled=" + ChatColor.WHITE + cfg.getBoolean(CONFIG_ROOT + ".enabled", true));
        lines.add(ChatColor.GOLD + "[Podio] " + ChatColor.GRAY + "top-limit=" + ChatColor.WHITE + cfg.getInt(CONFIG_ROOT + ".top-limit", 15));
        lines.add(ChatColor.GOLD + "[Podio] " + ChatColor.GRAY + "reveal-interval-ticks=" + ChatColor.WHITE + cfg.getLong(CONFIG_ROOT + ".reveal-interval-ticks", 50L));

        for (String key : REQUIRED_LOCATIONS) {
            Location location = this.getConfiguredLocation(key);
            if (location == null) {
                lines.add(ChatColor.RED + "[Podio] " + key + " = INVALID");
            } else {
                lines.add(String.format("%s[Podio] %s = %s (%.2f, %.2f, %.2f)", ChatColor.GREEN, key, location.getWorld().getName(), location.getX(), location.getY(), location.getZ()));
            }
        }

        return lines;
    }

    public void reloadConfiguration() {
        this.plugin.getFileManager().reloadConfig();
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        PodiumCeremonySession session = this.activeSession;
        if (!this.ceremonyActive || session == null) {
            return;
        }

        if (!this.config().getBoolean(CONFIG_ROOT + ".snowball.enabled", true)) {
            return;
        }

        if (!(event.getEntity() instanceof Snowball)) {
            return;
        }

        ProjectileSource shooter = event.getEntity().getShooter();
        if (!(shooter instanceof Player player)) {
            return;
        }

        if (!session.participants.contains(player.getUniqueId())) {
            return;
        }

        SchedulerHelper.runTaskLater(this.plugin, () -> {
            if (player.isOnline()) {
                player.getInventory().setItemInMainHand(new ItemStack(Material.SNOWBALL, 1));
            }
        }, 1L);
    }

    private void teleportAudienceAndPrepare(PodiumCeremonySession session) {
        Location audience = this.getConfiguredLocation("audience");
        boolean snowballEnabled = this.config().getBoolean(CONFIG_ROOT + ".snowball.enabled", true);
        String startMessage = this.formatConfigText(this.config().getString(CONFIG_ROOT + ".messages.start-chat", "&6Cerimonia do podio iniciada!"), 0, "", session.event.getDisplayName());

        for (UUID uuid : session.participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }

            player.teleport(audience);
            player.sendMessage(startMessage);

            if (snowballEnabled) {
                player.getInventory().addItem(new ItemStack(Material.SNOWBALL, 1));
            }

            if (this.config().getBoolean(CONFIG_ROOT + ".effects.sounds", true)) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
            }
        }
    }

    private Set<UUID> resolveCeremonyParticipants(Events event) {
        Set<UUID> participants = new LinkedHashSet<>();
        participants.addAll(event.getSubscribers().keySet());
        if (this.plugin.getSpectatorManager() != null) {
            participants.addAll(this.plugin.getSpectatorManager().getSpectatorsInEvent(event.getId()));
        }

        return participants;
    }

    private boolean isLocationKey(String key) {
        return key != null && REQUIRED_LOCATIONS.contains(key.toLowerCase());
    }

    private void revealPosition(PodiumCeremonySession session, int position, Driver driver) {
        String driverName = this.getDriverName(driver);
        String title = this.formatConfigText(this.config().getString(CONFIG_ROOT + ".messages.reveal-title", "&b#{pos}"), position, driverName, session.event.getDisplayName());
        String subtitle = this.formatConfigText(this.config().getString(CONFIG_ROOT + ".messages.reveal-subtitle", "&f{player}"), position, driverName, session.event.getDisplayName());
        String chat = this.formatConfigText(this.config().getString(CONFIG_ROOT + ".messages.reveal-chat", "&7#{pos} &e{player}"), position, driverName, session.event.getDisplayName());

        boolean soundsEnabled = this.config().getBoolean(CONFIG_ROOT + ".effects.sounds", true);
        for (UUID uuid : session.participants) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                continue;
            }

            TitleHelper.sendThemedTitle(p, title, subtitle, 0, 40, 10);
            p.sendMessage(chat);
            if (soundsEnabled) {
                if (position <= 3) {
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
                } else {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.0F);
                }
            }
        }

        if (position > 3) {
            return;
        }

        Player podiumPlayer = Bukkit.getPlayer(driver.getUuid());
        if (podiumPlayer == null || !podiumPlayer.isOnline()) {
            String offlineChat = this.formatConfigText(this.config().getString(CONFIG_ROOT + ".messages.offline-top3-chat", "&7#{pos} &e{player} &8(offline)"), position, driverName, session.event.getDisplayName());
            for (UUID uuid : session.participants) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.sendMessage(offlineChat);
                }
            }

            this.plugin.getDebugManager().logRaceSystem("Top " + position + " offline durante cerimonia de podio: " + driverName);
            return;
        }

        Location podiumLocation = switch (position) {
            case 1 -> this.getConfiguredLocation("p1");
            case 2 -> this.getConfiguredLocation("p2");
            default -> this.getConfiguredLocation("p3");
        };

        if (podiumLocation == null) {
            this.plugin.getDebugManager().logRaceSystem("Local de podio invalido para P" + position + ". Teleporte ignorado.");
            return;
        }

        podiumPlayer.teleport(podiumLocation);
        if (this.config().getBoolean(CONFIG_ROOT + ".effects.particles", true)) {
            podiumPlayer.getWorld().spawnParticle(Particle.FIREWORK, podiumLocation.clone().add(0.0D, 1.0D, 0.0D), 50);
        }
    }

    private void finishCeremonyWithDelay(PodiumCeremonySession session) {
        long delay = Math.max(0L, this.config().getLong(CONFIG_ROOT + ".final-delay-ticks", 60L));
        session.finalizeTask = SchedulerHelper.runTaskLater(this.plugin, () -> {
            synchronized (PodiumManager.this) {
                if (activeSession != session) {
                    return;
                }

                completeAndCleanup(session);
            }
        }, delay);
    }

    private void completeAndCleanup(PodiumCeremonySession session) {
        Location lobby = this.getConfiguredLocation("lobby");
        if (lobby == null) {
            this.plugin.getDebugManager().logRaceSystem("Local de lobby invalido ao finalizar cerimonia. Pulando teleporte final.");
        }

        boolean snowballEnabled = this.config().getBoolean(CONFIG_ROOT + ".snowball.enabled", true);
        String endMessage = this.formatConfigText(this.config().getString(CONFIG_ROOT + ".messages.end-chat", "&eCerimonia encerrada."), 0, "", session.event.getDisplayName());

        for (UUID uuid : session.participants) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                continue;
            }

            if (snowballEnabled) {
                p.getInventory().remove(Material.SNOWBALL);
            }

            p.sendMessage(endMessage);
            if (lobby != null) {
                p.teleport(lobby);
            }
        }

        this.ceremonyActive = false;
        this.activeSession = null;
    }

    private String getDriverName(Driver driver) {
        Player online = Bukkit.getPlayer(driver.getUuid());
        if (online != null) {
            return online.getName();
        }

        String offline = Bukkit.getOfflinePlayer(driver.getUuid()).getName();
        return offline != null ? offline : driver.getUuid().toString();
    }

    private String formatConfigText(String raw, int position, String playerName, String eventName) {
        String text = raw == null ? "" : raw;
        text = text.replace("{pos}", String.valueOf(position));
        text = text.replace("{player}", playerName == null ? "" : playerName);
        text = text.replace("{event}", eventName == null ? "" : eventName);
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private FileConfiguration config() {
        return this.plugin.getFileManager().getConfig();
    }

    private final class PodiumRunnable extends BukkitRunnable {
        private final PodiumCeremonySession session;

        private PodiumRunnable(PodiumCeremonySession session) {
            this.session = session;
        }

        @Override
        public void run() {
            synchronized (PodiumManager.this) {
                if (activeSession != this.session) {
                    this.cancel();
                    return;
                }

                try {
                    int currentPosition = this.session.currentRevealPosition.getAndDecrement();
                    if (currentPosition <= 0) {
                        finishCeremonyWithDelay(this.session);
                        this.cancel();
                        return;
                    }

                    int driverIndex = currentPosition - 1;
                    if (driverIndex >= this.session.results.size()) {
                        return;
                    }

                    Driver driver = this.session.results.get(driverIndex);
                    revealPosition(this.session, currentPosition, driver);
                } catch (Exception ex) {
                    plugin.getDebugManager().logRaceSystem("Erro na cerimonia de podio: " + ex.getMessage());
                    completeAndCleanup(this.session);
                    this.cancel();
                }
            }
        }
    }

    private static final class PodiumCeremonySession {
        private final Events event;
        private final List<Driver> results;
        private final Set<UUID> participants;
        private final int topLimitUsed;
        private final AtomicInteger currentRevealPosition;
        private ScheduledTask revealTask;
        private ScheduledTask finalizeTask;

        private PodiumCeremonySession(Events event, List<Driver> results, Set<UUID> participants, int topLimitUsed, AtomicInteger currentRevealPosition) {
            this.event = event;
            this.results = results;
            this.participants = participants;
            this.topLimitUsed = topLimitUsed;
            this.currentRevealPosition = currentRevealPosition;
        }
    }
}
