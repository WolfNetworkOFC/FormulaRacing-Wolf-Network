package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TimeTrialDuelsAction {

    private final FormulaRacing plugin;
    private final DatabaseManager dm;

    private final Map<UUID, DuelSession> activeTimers = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> activeVisuals = new ConcurrentHashMap<>();

    private static final String ICON_TIMER = "§b§l⌚";
    private static final String ICON_RECORD = "§6§l✪";
    private static final String BRACKET = "§8§l»";

    public TimeTrialDuelsAction(FormulaRacing plugin, DatabaseManager dm) {
        this.plugin = plugin;
        this.dm = dm;
        startGlobalUpdateTask();
    }

    /**
     * Liga/Desliga a Action Bar.
     */
    public void toggleVisuals(Player player, boolean active) {
        UUID uuid = player.getUniqueId();
        if (active) {
            activeVisuals.put(uuid, true);
        } else {
            activeVisuals.remove(uuid);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
        }
    }

    /**
     * Liga o cronômetro APENAS se os visuais estiverem ativos (conforme sua regra).
     */
    public void toggleTimer(Player player, int duelId, boolean active) {
        UUID uuid = player.getUniqueId();
        if (active) {
            // REGRA: Só ativa o timer se o visual já estiver ligado para este jogador
            if (activeVisuals.containsKey(uuid)) {
                activeTimers.put(uuid, new DuelSession(uuid, duelId));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            }
        } else {
            activeTimers.remove(uuid);
        }
    }

    public void stopAll(Player player) {
        UUID uuid = player.getUniqueId();
        activeTimers.remove(uuid);
        activeVisuals.remove(uuid);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
    }

    private void startGlobalUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // O loop agora foca em quem tem VISUAL ativo.
                // Pois se o visual tá desligado, o timer (pela sua regra) não deve rodar/aparecer.
                activeVisuals.keySet().forEach(uuid -> {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline()) {
                        activeTimers.remove(uuid);
                        activeVisuals.remove(uuid);
                        return;
                    }

                    DuelSession session = activeTimers.get(uuid);

                    // Se houver uma sessão de timer ativa para este visual:
                    if (session != null) {
                        if (session.shouldUpdateData()) {
                            updateDataAsync(player, session);
                        }

                        if (session.getCachedPosition().contains("1º")) {
                            spawnLeaderParticles(player);
                        }

                        // Envia a barra com o tempo correndo
                        sendDuelActionBar(player, session);
                    } else {
                        // Se o visual está ON mas o timer está OFF,
                        // podemos mostrar uma barra neutra ou apenas limpar.
                        // player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§eAguardando largada..."));
                    }
                });
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void sendDuelActionBar(Player player, DuelSession session) {
        String message = String.format("%s %s %s §f%s %s §8| %s §ePB: §7%s",
                BRACKET, session.getCachedPosition(), BRACKET,
                session.getFormattedTime(), ICON_TIMER,
                ICON_RECORD, session.getPersonalBest());

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

    private void updateDataAsync(Player player, DuelSession session) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int pos = dm.getplayerpositiononduel(session.getDuelId(), player);
            session.setCachedPosition(formatPosition(pos));

            if (session.getPersonalBest().equals("None")) {
                Object[] data = dm.getPlayerBestTimeOnDuel(player.getUniqueId(), session.getDuelId());
                if (data != null) {
                    double time = (double) data[0];
                    String display = ((boolean) data[2]) ? formatTime(time) : "§e" + (int) data[1] + " CP";
                    session.setPersonalBest(display);
                } else {
                    session.setPersonalBest("--:--.---");
                }
            }
        });
    }

    public long getPlayerTimeMillis(Player player) {
        DuelSession session = activeTimers.get(player.getUniqueId());
        return (session != null) ? session.getCurrentTimeMillis() : 0L;
    }

    private String formatPosition(int pos) {
        return switch (pos) {
            case 1 -> "§a§l1º PLACE";
            case 2 -> "§e§l2º PLACE";
            case 3 -> "§6§l3º PLACE";
            default -> "§f§l" + pos + "º PLACE";
        };
    }

    public String formatTime(double seconds) {
        long totalMillis = (long) (seconds * 1000);
        return String.format("%02d:%02d.%03d", (totalMillis / 60000), (totalMillis % 60000) / 1000, totalMillis % 1000);
    }

    private void spawnLeaderParticles(Player player) {
        player.getWorld().spawnParticle(org.bukkit.Particle.DUST, player.getLocation().add(0, 0.1, 0), 1,
                new org.bukkit.Particle.DustOptions(Color.AQUA, 1));
    }

    private static class DuelSession {
        private final UUID uuid;
        private final int duelId;
        private final long startTime;
        private String cachedPosition = "§f§l-º PLACE";
        private String personalBest = "None";
        private int tickCounter = 0;

        public DuelSession(UUID uuid, int duelId) {
            this.uuid = uuid;
            this.duelId = duelId;
            this.startTime = System.currentTimeMillis();
        }

        public boolean shouldUpdateData() { return tickCounter++ % 10 == 0; }
        public long getCurrentTimeMillis() { return System.currentTimeMillis() - startTime; }
        public String getFormattedTime() {
            long elapsed = getCurrentTimeMillis();
            return String.format("%02d:%02d.%03d", (elapsed / 60000) % 60, (elapsed / 1000) % 60, elapsed % 1000);
        }
        public int getDuelId() { return duelId; }
        public String getCachedPosition() { return cachedPosition; }
        public void setCachedPosition(String pos) { this.cachedPosition = pos; }
        public String getPersonalBest() { return personalBest; }
        public void setPersonalBest(String personalBest) { this.personalBest = personalBest; }
    }
}