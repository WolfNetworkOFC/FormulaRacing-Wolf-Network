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
import java.util.concurrent.ConcurrentHashMap;

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

        // 1. Filtros de Performance e Segurança
        if (justTeleported.contains(uuid)) return;
        if (!(player.getVehicle() instanceof Boat)) return;

        Location current = player.getLocation();
        Location previous = lastLocation.get(uuid);
        lastLocation.put(uuid, current);

        if (previous == null) previous = current;
        if (previous.distanceSquared(current) < 0.05) return;

        // 2. Verificação de Mundo
        String worldName = current.getWorld().getName().toLowerCase();
        List<RegionData> worldRegions = regions.get(worldName);

        if (worldRegions == null || worldRegions.isEmpty()) {
            if (!warnedWorlds.contains(worldName)) {
                warnedWorlds.add(worldName);
                plugin.getLogger().warning("[FormulaRacing] Nenhuma região registrada para o mundo " + worldName);
            }
            return;
        }

        // 3. Detecção de START / END
        RegionData region = getRegionAtLine(previous, current, worldRegions);

        if (region != null) {
            String regionTrack = region.getTrackName();
            String type = region.getType().toUpperCase();

            // Criamos uma chave única: "Pista_Tipo" (ex: "London_START")
            String currentKey = regionTrack + "_" + type;
            String lastKey = playerRegion.get(uuid);

            // Se a chave mudou, processamos o novo bloco
            if (!Objects.equals(currentKey, lastKey)) {
                // Salvamos a chave atual para evitar repetições no mesmo bloco
                playerRegion.put(uuid, currentKey);

                // Dispara a lógica principal (Timer, Banco, etc)
                handleRegion(player, region);
            }
        } else {
            // Se saiu de qualquer região, limpamos o cache para permitir re-entrada
            if (playerRegion.containsKey(uuid)) {
                playerRegion.remove(uuid);
            }
        }

        // 4. Detecção de Checkpoints (Lógica de Linha Fina)
        String activeTrack = timerUtils.getActiveTrack(player);
        if (activeTrack != null) {
            List<RegionData> checkpoints = database.getCheckpoints(activeTrack);
            TimerUtils.PlayerTimerData data = timerUtils.getTimerData(player, activeTrack);

            if (data != null) {
                for (RegionData cp : checkpoints) {
                    // Se já passou nesse CP nesta volta, ignora
                    if (data.getCheckpointsReached().contains(cp.getId())) continue;

                    if (intersectsRegion(previous, current, cp)) {
                        // Registra o checkpoint síncronamente
                        timerUtils.addCheckpoint(player, cp.getId());

                        // Salva no cache de deltas (HUD)
                        double elapsed = timerUtils.getPlayerElapsedTime(player);
                        timerUtils.addTempCheckpoint(uuid, cp.getId(), elapsed, activeTrack);

                        // Som de feedback
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.5f);
                    }
                }
            }
        }
    }


    private void handleRegion(Player player, RegionData region) {
        if (!(player.getVehicle() instanceof Boat)) return;

        UUID uuid = player.getUniqueId();
        String regionTrack = region.getTrackName();
        String type = region.getType().toUpperCase();
        String lang_code = database.getPlayerLanguage(uuid);

        // 1. DEBOUNCE: Impede que o mesmo bloco dispare várias vezes enquanto o jogador está em cima dele
        String lastState = playerRegion.get(uuid);
        if (Objects.equals(lastState, regionTrack + "_" + type + "_DONE")) return;

        // Só processamos blocos de START ou END
        if (!type.equals("START") && !type.equals("END")) return;

        plugin.getLogger().info("§e[DEBUG-REGION] §fPlayer: " + player.getName() + " | Bloco: " + type + " | Pista: " + regionTrack);

        // --- 🏁 PASSO 1: FINALIZAÇÃO (Se já estiver correndo e passar no START ou END) ---
        // Em circuitos, tanto o bloco START quanto o END podem finalizar a volta anterior.
        if (timerUtils.isTimerRunning(player, regionTrack)) {
            TimerUtils.PlayerTimerData data = timerUtils.getTimerData(player, regionTrack);
            if (data != null) {
                double rawElapsed = timerUtils.getPlayerElapsedTime(player, regionTrack);
                int checkpoints = data.getCheckpointsReached().size();
                int totalCheckpoints = data.getTotalCheckpoints();

                // Só finaliza se tiver os CPs necessários
                if (checkpoints >= totalCheckpoints && totalCheckpoints > 0) {
                    database.saveFullTime(uuid, player.getName(), regionTrack, rawElapsed, checkpoints);

                    String msg = (lang_code.startsWith("pt")) ? "§6🏁 §fTempo: §b" : "§6🏁 §fTime: §b";
                    player.sendMessage(msg + formatTime(rawElapsed));
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                } else {
                    plugin.getLogger().warning("§c[DEBUG-FINISH] Volta anulada: CPs insuficientes (" + checkpoints + "/" + totalCheckpoints + ")");
                }
            }
            // Para o timer antigo antes de começar o novo
            timerUtils.stopTimer(player, regionTrack);
        }

        // --- 🚀 PASSO 2: INÍCIO (Sempre inicia uma nova volta ao passar no START ou END) ---
        // Se o jogador estiver com o modo Time Trial ativo, iniciamos uma nova volta imediatamente.
        if (database.getTimeTrialEnabled(uuid)) {
            String soloTrack = plugin.getLastTimeTrialTrack(uuid);

            // Ajuste automático de pista (Ex: Londres)
            if (soloTrack == null || !regionTrack.equalsIgnoreCase(soloTrack)) {
                plugin.setLastTimeTrialTrack(uuid, regionTrack);
            }

            plugin.getLogger().info("§a[DEBUG-START] Iniciando nova volta para: " + regionTrack);

            stt.setPlayerTrack(player, regionTrack);
            timerUtils.startTimer(player, regionTrack);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        }

        // 2. Marca como processado para o debounce
        playerRegion.put(uuid, regionTrack + "_" + type + "_DONE");
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