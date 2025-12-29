package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager.RegionData;
import dev.EfraGroup.formulaRacing.Database.EventsManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
//import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.PacketSender;
import dev.EfraGroup.formulaRacing.Utils.ScoreboardTimeTrialUtils;
import dev.EfraGroup.formulaRacing.Utils.TimeTrialDuelsAction;
import dev.EfraGroup.formulaRacing.Utils.TimerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class RegionListener implements Listener {

    private final FormulaRacing plugin;
    private final DatabaseManager database;
    private final TimerUtils timerUtils;
    private final PacketSender packetSender;
    private final ScoreboardTimeTrialUtils stt;
    private final EventsManager ev;
    private final TimeTrialDuelsAction DuelsTimer;


    private final Map<UUID, String> playerRegion = new HashMap<>();
    private final Map<String, List<RegionData>> regions = new HashMap<>();
    private final Set<String> warnedWorlds = new HashSet<>();
    private final Map<UUID, Location> lastLocation = new HashMap<>();

    // 🔹 Jogadores ignorados após teleporte
    private final Set<UUID> justTeleported = new HashSet<>();

    public RegionListener(FormulaRacing plugin, DatabaseManager database, TimerUtils timerUtils, PacketSender packetSender, ScoreboardTimeTrialUtils stt, EventsManager ev, TimeTrialDuelsAction DuelsTimer) {
        this.plugin = plugin;
        this.database = database;
        this.timerUtils = timerUtils;
        this.packetSender = packetSender;
        this.stt = stt;
        this.ev = ev;
        this.DuelsTimer = DuelsTimer;


        startRegionLoader();
        startRegionChecker();
    }

    // ================================================================
    // 🔹 Marcar jogador como recém teleportado
    // ================================================================
    private void markJustTeleported(Player player) {
        justTeleported.add(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            justTeleported.remove(player.getUniqueId());
        }, 20L); // 1 segundo de proteção
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        // PROTEÇÃO: Impede teleporte durante duelo (exceto teleportes do sistema)
        if (database.isPlayerInActiveDuel(player.getUniqueId())) {
            // Permite apenas teleportes causados pelo plugin (ex: teleporte para largada)
            if (event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN &&
                event.getCause() != PlayerTeleportEvent.TeleportCause.COMMAND) {
                event.setCancelled(true);
                player.sendMessage("§c§lDUELO §8» §7Você não pode se teleportar durante um duelo!");
                return;
            }
        }

        lastLocation.put(player.getUniqueId(), event.getTo()); // reseta "previous"
        markJustTeleported(player); // protege contra disparos falsos
    }

    private void loadRegions() {
        regions.clear();
        for (RegionData r : database.getAllRegions()) {
            String world = r.getWorld().toLowerCase();
            regions.computeIfAbsent(world, k -> new ArrayList<>()).add(r);
        }
    }

    private void startRegionLoader() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Carrega de forma assíncrona para não travar o TPS
                List<RegionData> allRegions = database.getAllRegions();

                // Atualiza o mapa local de forma segura
                Bukkit.getScheduler().runTask(plugin, () -> {
                    regions.clear();
                    for (RegionData r : allRegions) {
                        String world = r.getWorld().toLowerCase();
                        regions.computeIfAbsent(world, k -> new ArrayList<>()).add(r);
                    }
                });
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 1200L); // 1 minuto é suficiente
    }
    private void startRegionChecker() {
        // Usamos o próprio agendador do Bukkit para evitar o erro de desligamento (IllegalPluginAccessException)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {

            for (Player player : Bukkit.getOnlinePlayers()) {
                // SÓ processa se o jogador estiver em um barco (filtro rápido)
                if (player.getVehicle() instanceof org.bukkit.entity.Boat) {

                    // Chamamos a lógica de checagem
                    // Como este loop já é ASYNC, as consultas SQL no checkPlayerRegions
                    // NÃO vão travar o MSPT do servidor.
                    checkPlayerRegions(player);
                }
            }

        }, 0L, 1L); // 2L = Roda a cada 2 ticks (10 vezes por segundo).
        // É mais que suficiente para detectar colisões e economiza muita CPU.
    }


    private void checkPlayerRegions(Player player) {
        UUID uuid = player.getUniqueId();

        // 🔹 Ignora se acabou de teleportar
        if (justTeleported.contains(uuid)) return;

        // 🔹 Ignora se não está em um barco (evita falsos positivos a pé)
        if (!(player.getVehicle() instanceof Boat)) return;

        String lastTrack = plugin.getLastTimeTrialTrack(uuid);

        Location current = player.getLocation();
        Location previous = lastLocation.get(uuid);
        lastLocation.put(uuid, current);
        if (previous == null) previous = current;

        // 🔹 Ignora se o jogador não se moveu o suficiente (reduz falsos disparos)
        if (previous.distanceSquared(current) < 0.05) return;

        String worldName = current.getWorld().getName().toLowerCase();
        List<RegionData> worldRegions = regions.get(worldName);
        if (worldRegions == null || worldRegions.isEmpty()) {
            if (!warnedWorlds.contains(worldName)) {
                warnedWorlds.add(worldName);
                Bukkit.getLogger().warning("[FormulaRacing] Nenhuma região registrada para o mundo " + worldName);
            }
            return;
        }

        // 🔹 Detecta START / END com tolerância (corrige falhas de detecção)
        RegionData region = getRegionAtLine(previous, current, worldRegions);
        String currentType = region != null ? region.getType() : null;
        String previousType = playerRegion.get(uuid);

        if (!Objects.equals(currentType, previousType)) {
            if (region != null) {
                // 🔹 START precisa ter o mesmo circuito ativo
                if (region.getType().equalsIgnoreCase("START")) {
                    String regionTrack = region.getTrackName();
                    if (lastTrack == null || !lastTrack.equalsIgnoreCase(regionTrack)) return;
                }

                playerRegion.put(uuid, region.getType());
                handleRegion(player, region);
            } else if (previousType != null) {
                playerRegion.remove(uuid);
            }
        }

        // 🔹 Checkpoints — detecta linhas finas com segurança
        String activeTrack = timerUtils.getActiveTrack(player);
        if (activeTrack != null) {
            List<RegionData> checkpoints = database.getCheckpoints(activeTrack);
            TimerUtils.PlayerTimerData data = timerUtils.getTimerData(player, activeTrack);
            if (data != null) {
                for (RegionData cp : checkpoints) {
                    if (data.getCheckpointsReached().contains(cp.getId())) continue;

                    if (intersectsRegion(previous, current, cp)) {
                        timerUtils.addCheckpoint(player, cp.getId());
                        double elapsed = timerUtils.getPlayerElapsedTime(player);

                        // 🔹 Salva temporariamente o checkpoint
                        timerUtils.addTempCheckpoint(uuid, cp.getId(), elapsed, activeTrack);
                    }
                }
            }
        }
    }

    private void handleRegion(Player player, RegionData region) {
        if (!(player.getVehicle() instanceof Boat)) return;
        String lang_code = database.getPlayerLanguage(player.getUniqueId());


        String type = region.getType().toUpperCase();
        String track = region.getTrackName();
        UUID uuid = player.getUniqueId();

        // 1. CHECAGEM DE MEMÓRIA: Verifica o que já está rodando agora
        boolean isRunningSolo = timerUtils.isTimerRunning(player, track);
        boolean isRunningDuel = database.isPlayerInActiveDuel(uuid);

        // Evita repetição de processamento no mesmo bloco (Debounce)
        String lastState = playerRegion.get(uuid);
        if (Objects.equals(lastState, track + "_DONE")) return;

        if (type.equals("START") || type.equals("END")) {

            // --- 🏁 FASE 1: FINALIZAR (Se houver cronômetro ativo) ---
            if (isRunningSolo || isRunningDuel) {

                // Lógica de finalização Solo
                if (isRunningSolo) {
                    TimerUtils.PlayerTimerData data = timerUtils.getTimerData(player);
                    double rawElapsed = timerUtils.getPlayerElapsedTime(player);
                    int checkpoints = (data != null) ? data.getCheckpointsReached().size() : 0;
                    int totalCheckpoints = (data != null) ? data.getTotalCheckpoints() : 0;

                    if (checkpoints >= totalCheckpoints && totalCheckpoints > 0) {
                        // 1. Busca os dados do recorde (Retorna o Object[])
                        Object[] bestData = database.getPlayerBestTime(player.getName(), track);

                        // 2. Extrai o tempo (index 0) convertendo para double
                        // Usamos um valor muito alto (Double.MAX_VALUE) caso o array seja nulo (sem recorde)
                        double personalBest = (bestData != null && bestData.length > 0) ? (double) bestData[0] : Double.MAX_VALUE;

                        // 3. Determina se é PB ANTES de salvar (para feedback imediato)
                        boolean isNewPB = (rawElapsed <= personalBest || personalBest == Double.MAX_VALUE);

                        // 4. Salva a volta atual no banco
                        database.saveFullTime(uuid, player.getName(), track, rawElapsed, checkpoints);

                        // 5. Sempre mostra o tempo da volta
                        String lapTimeMessage;
                        if ("pt_BR".equals(lang_code) || "pt_PT".equals(lang_code)) {
                            lapTimeMessage = "§6🏁 §fTempo da volta: §b" + formatTime(rawElapsed);
                        } else {
                            lapTimeMessage = "§6🏁 §fLap time: §b" + formatTime(rawElapsed);
                        }
                        player.sendMessage(lapTimeMessage);

                        // 6. Mostra feedback adicional se bateu PB
                        if (isNewPB) {
                            // ✅ NOVO PB!
                            String pbMessage;
                            if ("pt_BR".equals(lang_code) || "pt_PT".equals(lang_code)) {
                                pbMessage = "§a§l✓ §fMelhor Tempo Pessoal!";
                            } else {
                                // Inglês ou qualquer outro idioma
                                pbMessage = "§a§l✓ §fPersonal Best!";
                            }
                            player.sendMessage(pbMessage);
                            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                        } else {
                            // Mostra a diferença para o PB
                            double delta = rawElapsed - personalBest;
                            String deltaMessage;
                            if ("pt_BR".equals(lang_code) || "pt_PT".equals(lang_code)) {
                                deltaMessage = "§7(PB: §b" + formatTime(personalBest) + " §7| §c+" + formatTime(delta) + "§7)";
                            } else {
                                deltaMessage = "§7(PB: §b" + formatTime(personalBest) + " §7| §c+" + formatTime(delta) + "§7)";
                            }
                            player.sendMessage(deltaMessage);
                        }
                    } else if (checkpoints > 0) {
                        player.sendMessage("§c"+plugin.getDirectTranslation("checkpoints", lang_code));
                    }
                }

                // Lógica de finalização de Duelo
                if (isRunningDuel) {
                    player.sendMessage("§e🏁 Duelo concluído!");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                }

                // Para ambos os sistemas e limpa a tela
                timerUtils.stopTimer(player, track);
                DuelsTimer.toggleTimer(player, database.getActiveDuelId(uuid), false);

                // Se for apenas o fim da pista (sem restart), marca como feito e encerra
                if (type.equals("END") && !region.getType().equalsIgnoreCase("START")) {
                    playerRegion.put(uuid, track + "_DONE");
                    return;
                }
            }

            // --- 🚀 FASE 2: INICIAR / REINICIAR ---
            // CHECAGEM DE INTENÇÃO: Busca no SQLite se ele deve ser Duelo ou Solo
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

                // Verifica duelo pelo método do ID (que já valida o estado != FINISHED)
                int activeDuelId = database.getActiveDuelId(uuid);
                boolean isSoloEnabled = database.getTimeTrialEnabled(uuid);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    // PRIORIDADE: Se houver Duelo, o Solo é ignorado
                    if (activeDuelId != -1) {
                        // ⚔️ MODO DUELO
                        DuelsTimer.toggleTimer(player, activeDuelId, true);
                        player.sendMessage("§e§l⚔ DUELO INICIADO!");
                    }
                    else if (isSoloEnabled) {
                        // ⏱️ MODO SOLO
                        stt.setPlayerTrack(player, track);
                        timerUtils.startTimer(player, track);
                    }

                    playerRegion.put(uuid, track + "_DONE");
                });
            });
        }
    }
    public double roundTime(double sec) {
        return Math.round(sec * 100.0) / 100.0; // arredonda para 0.01
    }


    public String formatTime(double elapsed) {
        long totalMillis = Math.round(elapsed * 1000.0);
        long minutes = totalMillis / 60000;
        long seconds = (totalMillis % 60000) / 1000;
        long millis = totalMillis % 1000;

        if (minutes > 0) {
            return String.format("%02d:%02d.%03d", minutes, seconds, millis);
        } else {
            return String.format("%02d.%03d", seconds, millis);
        }
    }


    private boolean intersectsRegion(Location from, Location to, RegionData r) {
        double minX = Math.min(r.getMinX(), r.getMaxX());
        double maxX = Math.max(r.getMinX(), r.getMaxX());
        double minY = Math.min(r.getMinY(), r.getMaxY());
        double maxY = Math.max(r.getMinY(), r.getMaxY());
        double minZ = Math.min(r.getMinZ(), r.getMaxZ());
        double maxZ = Math.max(r.getMinZ(), r.getMaxZ());

        double fx = from.getX(), fy = from.getY(), fz = from.getZ();
        double tx = to.getX(), ty = to.getY(), tz = to.getZ();

        // 1. Checagem rápida: Se o ponto final já está dentro
        if (tx >= minX && tx <= maxX && ty >= minY && ty <= maxY && tz >= minZ && tz <= maxZ) {
            return true;
        }

        // 2. Cálculo de Interseção de Linha (Algoritmo de Slab)
        double dx = tx - fx;
        double dy = ty - fy;
        double dz = tz - fz;

        double tmin = 0.0, tmax = 1.0;

        // Eixo X
        if (Math.abs(dx) > 1e-7) {
            double t1 = (minX - fx) / dx;
            double t2 = (maxX - fx) / dx;
            tmin = Math.max(tmin, Math.min(t1, t2));
            tmax = Math.min(tmax, Math.max(t1, t2));
        } else if (fx < minX || fx > maxX) return false;

        // Eixo Y
        if (Math.abs(dy) > 1e-7) {
            double t1 = (minY - fy) / dy;
            double t2 = (maxY - fy) / dy;
            tmin = Math.max(tmin, Math.min(t1, t2));
            tmax = Math.min(tmax, Math.max(t1, t2));
        } else if (fy < minY || fy > maxY) return false;

        // Eixo Z
        if (Math.abs(dz) > 1e-7) {
            double t1 = (minZ - fz) / dz;
            double t2 = (maxZ - fz) / dz;
            tmin = Math.max(tmin, Math.min(t1, t2));
            tmax = Math.min(tmax, Math.max(t1, t2));
        } else if (fz < minZ || fz > maxZ) return false;

        return tmin <= tmax;
    }

    private RegionData getRegionAtLine(Location from, Location to, List<RegionData> worldRegions) {
        for (RegionData r : worldRegions) {
            String type = r.getType().toUpperCase();
            if (!type.equals("START") && !type.equals("END")) continue;
            if (intersectsRegion(from, to, r)) return r;
        }
        return null;
    }
}