package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Duels.TimeTrialDuels;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TimeTrialDuelsAction {

    private final FormulaRacing plugin;
    private final DatabaseManager dm;
    private TimeTrialDuels timeTrialDuels; // Referência para calcular posição em tempo real

    private final Map<UUID, DuelSession> activeTimers = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> activeVisuals = new ConcurrentHashMap<>(); // Armazena o duelId no visual

    private static final String ICON_TIMER = "§b§l⌚";
    private static final String ICON_RECORD = "§6§l✪";
    private static final String BRACKET = "§8§l»";

    public TimeTrialDuelsAction(FormulaRacing plugin, DatabaseManager dm) {
        this.plugin = plugin;
        this.dm = dm;
        startGlobalUpdateTask();
    }

    /**
     * Define a referência do TimeTrialDuels (chamado após a inicialização)
     */
    public void setTimeTrialDuels(TimeTrialDuels timeTrialDuels) {
        this.timeTrialDuels = timeTrialDuels;
    }

    /**
     * Liga a Action Bar para o jogador, mesmo antes da largada.
     */
    public void toggleVisuals(Player player, int duelId, boolean active) {
        UUID uuid = player.getUniqueId();
        if (active) {
            activeVisuals.put(uuid, duelId);
        } else {
            activeVisuals.remove(uuid);
            activeTimers.remove(uuid);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
        }
    }

    /**
     * Inicia apenas a contagem do tempo (cronômetro).
     */
    public void toggleTimer(Player player, int duelId, boolean active) {
        UUID uuid = player.getUniqueId();
        if (active) {
            // Se o visual estiver ligado, iniciamos a sessão de tempo
            if (activeVisuals.containsKey(uuid)) {
                activeTimers.put(uuid, new DuelSession(uuid, duelId));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            }
        } else {
            activeTimers.remove(uuid);
        }
    }

    /**
     * Reseta o timer da volta atual (chamado quando o jogador cruza a linha de start)
     */
    public void resetLapTimer(Player player) {
        DuelSession session = activeTimers.get(player.getUniqueId());
        if (session != null) {
            session.resetLapTimer();
        }
    }

    /**
     * Atualiza o melhor tempo de volta do jogador (chamado quando uma volta é completada)
     */
    public void updateBestLapTime(Player player, double lapTime) {
        DuelSession session = activeTimers.get(player.getUniqueId());
        if (session != null) {
            Double current = session.getBestLapTime();
            if (current == null || lapTime < current) {
                session.setBestLapTime(lapTime);
                // Força atualização imediata do cache de PB
                session.setPersonalBest("None");
            }
        }
    }

    public void stopAll(Player player) {
        UUID uuid = player.getUniqueId();
        activeTimers.remove(uuid);
        activeVisuals.remove(uuid);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
    }

    /**
     * Obtém o tempo decorrido em segundos para um jogador.
     */
    public double getPlayerElapsedSeconds(Player player) {
        DuelSession session = activeTimers.get(player.getUniqueId());
        if (session == null) return 0.0;
        return session.getCurrentTimeMillis() / 1000.0;
    }

    private void startGlobalUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                activeVisuals.forEach((uuid, duelId) -> {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline()) {
                        activeVisuals.remove(uuid);
                        activeTimers.remove(uuid);
                        return;
                    }

                    DuelSession session = activeTimers.get(uuid);

                    if (session != null) {
                        // MODO CORRIDA: Timer rodando
                        if (session.shouldUpdateData()) {
                            updateDataAsync(player, session);
                        }

                        if (session.getCachedPosition().contains("1º") || session.getCachedPosition().contains("1st")) {
                            spawnLeaderParticles(player);
                        }

                        sendDuelActionBar(player, session.getCachedPosition(), session.getFormattedTime(), session.getPersonalBest(), session.getCachedDelta());
                    } else {
                        // MODO ESPERA: Visual ON, mas timer ainda não começou
                        // Mostra a barra com 00:00.000 ou "Aguardando..."
                        String langCode = dm.getPlayerLanguage(uuid);
                        String waitingText = plugin.getDirectTranslation("duel_waiting", langCode);
                        sendDuelActionBar(player, "§f§l" + waitingText, "00:00.000", "§7--:--.---", "");
                    }
                });
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Método centralizado para enviar a Action Bar
     */
    private void sendDuelActionBar(Player player, String position, String time, String pb, String delta) {
        String langCode = dm.getPlayerLanguage(player.getUniqueId());
        String pbLabel = plugin.getDirectTranslation("duel_pb_label", langCode);

        String message = String.format("%s %s %s §f%s%s %s §8| %s §e%s: §7%s",
                BRACKET, position, BRACKET,
                time, delta, ICON_TIMER,
                ICON_RECORD, pbLabel, pb);

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

    private void updateDataAsync(Player player, DuelSession session) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Obtém o idioma do jogador
            String langCode = dm.getPlayerLanguage(player.getUniqueId());
            session.setLangCode(langCode);

            // Usa a posição em tempo real se TimeTrialDuels estiver disponível
            int pos = 1;
            if (timeTrialDuels != null) {
                pos = timeTrialDuels.getPlayerPosition(session.getDuelId(), player.getUniqueId());
            } else {
                // Fallback para o método antigo (caso ainda não tenha sido inicializado)
                pos = dm.getplayerpositiononduel(session.getDuelId(), player);
            }
            session.setCachedPosition(formatPosition(pos, langCode));

            // Atualiza o melhor tempo de volta do jogador no duelo atual
            if (session.getBestLapTime() == null) {
                Double bestLap = dm.getPlayerBestLapTimeInDuel(player.getUniqueId(), session.getDuelId());
                session.setBestLapTime(bestLap);
            }

            // Atualiza o display do PB no HUD (melhor tempo de volta)
            if (session.getPersonalBest().equals("None")) {
                Double bestLap = session.getBestLapTime();
                if (bestLap != null && bestLap > 0) {
                    session.setPersonalBest(formatTime(bestLap));
                } else {
                    session.setPersonalBest("--:--.---");
                }
            }

            // Calcula o delta em tempo real
            updateDelta(session);
        });
    }

    /**
     * Calcula o delta comparando o tempo da volta atual com o melhor tempo de volta
     */
    private void updateDelta(DuelSession session) {
        Double bestLap = session.getBestLapTime();
        if (bestLap == null || bestLap <= 0) {
            session.setCachedDelta("");
            return;
        }

        double currentLapTime = session.getCurrentLapTime();
        double delta = currentLapTime - bestLap;

        String deltaStr;
        String deltaColor;

        if (Math.abs(delta) < 0.0009) {
            deltaStr = " ±0.000";
            deltaColor = "§e";
        } else if (delta < 0) {
            deltaStr = String.format(" §a-%.3f", Math.abs(delta));
            deltaColor = "§a";
        } else {
            deltaStr = String.format(" §c+%.3f", delta);
            deltaColor = "§c";
        }

        session.setCachedDelta(deltaColor + deltaStr);
    }

    // ... (Métodos formatPosition, formatTime e spawnLeaderParticles permanecem iguais)

    private String formatPosition(int pos, String langCode) {
        String positionText;
        String color;

        switch (pos) {
            case 1:
                positionText = plugin.getDirectTranslation("duel_position_1st", langCode);
                color = "§a§l";
                break;
            case 2:
                positionText = plugin.getDirectTranslation("duel_position_2nd", langCode);
                color = "§e§l";
                break;
            case 3:
                positionText = plugin.getDirectTranslation("duel_position_3rd", langCode);
                color = "§6§l";
                break;
            default:
                positionText = plugin.getTranslation("duel_position_nth", langCode, "{position}", String.valueOf(pos));
                color = "§f§l";
                break;
        }

        return color + positionText;
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
        private String cachedPosition = "§f§l...";
        private String personalBest = "None";
        private int tickCounter = 0;
        private Double bestLapTime = null; // Melhor tempo de volta no duelo atual (em segundos)
        private double currentLapStartTime; // Início da volta atual
        private String cachedDelta = ""; // Delta formatado para exibição
        private String langCode = "en_US"; // Idioma do jogador

        public DuelSession(UUID uuid, int duelId) {
            this.uuid = uuid;
            this.duelId = duelId;
            this.startTime = System.currentTimeMillis();
            this.currentLapStartTime = System.currentTimeMillis();
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
        public Double getBestLapTime() { return bestLapTime; }
        public void setBestLapTime(Double time) { this.bestLapTime = time; }
        public double getCurrentLapTime() { return (System.currentTimeMillis() - currentLapStartTime) / 1000.0; }
        public void resetLapTimer() { this.currentLapStartTime = System.currentTimeMillis(); }
        public String getCachedDelta() { return cachedDelta; }
        public void setCachedDelta(String delta) { this.cachedDelta = delta; }
        public String getLangCode() { return langCode; }
        public void setLangCode(String langCode) { this.langCode = langCode; }
    }
}