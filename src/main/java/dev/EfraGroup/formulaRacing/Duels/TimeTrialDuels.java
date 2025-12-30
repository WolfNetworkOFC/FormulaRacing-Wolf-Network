package dev.EfraGroup.formulaRacing.Duels;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.PacketSender;
import dev.EfraGroup.formulaRacing.Utils.ScoreboardDuelsTimeUtils;
import dev.EfraGroup.formulaRacing.Utils.TimeTrialDuelsAction;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TimeTrialDuels implements Listener {

    private final FormulaRacing plugin;
    private final DatabaseManager dm;
    private final PacketSender packet;
    private final TimeTrialDuelsAction ttda;
    private final ScoreboardDuelsTimeUtils scoreboardDuelsUtils;

    // Mapa para rastrear o estado de cada duelo ativo
    private final Map<Integer, DuelState> activeDuels = new ConcurrentHashMap<>();

    // Mapa para rastrear o estado individual de cada jogador no duelo
    private final Map<UUID, PlayerDuelState> playerStates = new ConcurrentHashMap<>();

    public TimeTrialDuels(FormulaRacing plugin, DatabaseManager dm, PacketSender packet, TimeTrialDuelsAction ttda, ScoreboardDuelsTimeUtils scoreboardDuelsUtils) {
        this.plugin = plugin;
        this.dm = dm;
        this.packet = packet;
        this.ttda = ttda;
        this.scoreboardDuelsUtils = scoreboardDuelsUtils;

    }

    public void startDuelPreparation(Player p1, Player p2, String trackName, int laps, int timeLimit, boolean lonely) {
        Location spawnLoc = dm.getTrackSpawn(trackName);

        if (spawnLoc == null) {
            p1.sendMessage("§cErro ao carregar o spawn da pista.");
            p2.sendMessage("§cErro ao carregar o spawn da pista.");
            return;
        }

        String trackNameWS = trackName.replace(" ", "");
        List<Player> participants = Arrays.asList(p1, p2);

        // 1. Registro no Banco de Dados (Sync para pegar o ID)
        final int[] duelIdHolder = {-1};

        // Criar duelo de forma síncrona para obter o ID
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Criar duelo e obter ID
            dm.createDuel(p1, participants, trackNameWS, laps, timeLimit, lonely);

            // Buscar o ID do duelo recém criado
            int duelId = dm.getActiveDuelId(p1.getUniqueId());
            duelIdHolder[0] = duelId;

            if (duelId == -1) {
                plugin.getLogger().severe("§c[ERRO CRÍTICO] Duelo criado mas ID não encontrado!");
                p1.sendMessage("§cErro ao criar duelo. Contate um administrador.");
                p2.sendMessage("§cErro ao criar duelo. Contate um administrador.");
                return;
            }

            // Criar estado do duelo
            DuelState duelState = new DuelState(duelId, trackNameWS, laps, timeLimit, lonely);
            duelState.addPlayer(p1.getUniqueId());
            duelState.addPlayer(p2.getUniqueId());
            activeDuels.put(duelId, duelState);

            // Criar estado individual dos jogadores
            playerStates.put(p1.getUniqueId(), new PlayerDuelState(p1.getUniqueId(), duelId));
            playerStates.put(p2.getUniqueId(), new PlayerDuelState(p2.getUniqueId(), duelId));

            plugin.getLogger().info("§a[DUEL] Duelo #" + duelId + " criado: " + p1.getName() + " vs " + p2.getName());

            // 2. Aplicar NBT/Tags dos barcos
            packet.applyBoatUtilsToPlayer(p1, trackNameWS);
            packet.applyBoatUtilsToPlayer(p2, trackNameWS);

            // 3. Ativar Visuais de HUD (Action Bar & Scoreboard)
            ttda.toggleVisuals(p1, duelId, true);
            ttda.toggleVisuals(p2, duelId, true);

            scoreboardDuelsUtils.applyDuelBoard(p1, duelId, laps, trackName);
            scoreboardDuelsUtils.applyDuelBoard(p2, duelId, laps, trackName);

            // 4. Lógica Lonely (Esconder jogadores)
            if (lonely) {
                packet.applyLonelyToPlayer(p1, true);
                packet.applyLonelyToPlayer(p2, true);
                p1.sendMessage("§d§lLONELY §8» §fModo fantasma ativado! Oponentes ocultos.");
                p2.sendMessage("§d§lLONELY §8» §fModo fantasma ativado! Oponentes ocultos.");
            } else {
                packet.applyLonelyToPlayer(p1, false);
                packet.applyLonelyToPlayer(p2, false);
            }

            // 5. Posicionamento no Grid
            // Em duelos sempre começam na mesma posição (não há vantagem física)
            // O modo lonely apenas esconde os jogadores um do outro
            setupPlayerInGrid(p1, spawnLoc.clone());
            setupPlayerInGrid(p2, spawnLoc.clone());

            // 6. Inicia a sequência de contagem
            // 6. Inicia a sequência de contagem
            startFullCountdownSequence(p1, p2, duelId, timeLimit);
        });
    }

    private void startFullCountdownSequence(Player p1, Player p2, int duelId, int timeLimit) {
        new BukkitRunnable() {
            int countdown = 5;

            @Override
            public void run() {
                // Verificar se os jogadores ainda estão online
                if (!p1.isOnline() || !p2.isOnline()) {
                    this.cancel();
                    endDuelByDisconnect(duelId);
                    return;
                }

                // Contagem regressiva com títulos grandes
                if (countdown > 0) {
                    String number = "" + countdown;

                    // Envia o Título Grande
                    p1.sendTitle(number, "", 0, 20, 5);
                    p2.sendTitle(number, "", 0, 20, 5);
                    p1.playSound(p1.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 1f, 0.5f);
                    p2.playSound(p2.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 1f, 0.5f);

                    countdown--;
                } else {
                    // LARGADA!
                    p1.sendTitle("§a§lGO!", "", 0, 20, 5);
                    p2.sendTitle("§a§lGO!", "", 0, 20, 5);
                    p1.playSound(p1.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                    p2.playSound(p2.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

                    // Liberar os jogadores
                    releasePlayers(p1, p2);

                    // Marcar duelo como em progresso
                    DuelState state = activeDuels.get(duelId);
                    if (state != null) {
                        state.setRaceStarted(true);
                        state.setRaceStartTime(System.currentTimeMillis());
                    }

                    plugin.getLogger().info("§a[DUEL] Duelo #" + duelId + " iniciado!");

                    // Inicia o timer de limite de tempo AGORA (se definido)
                    if (timeLimit > 0) {
                        startTimeLimitTimer(duelId, timeLimit);
                    }

                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Roda a cada 1 segundo (20 ticks)
    }

    /**
     * Inicia o timer de limite de tempo para o duelo.
     */
    private void startTimeLimitTimer(int duelId, int timeLimitMinutes) {
        DuelState duelState = activeDuels.get(duelId);
        if (duelState == null) return;

        long timeLimitTicks = timeLimitMinutes * 60L * 20L; // Converte minutos para ticks

        new BukkitRunnable() {
            int secondsRemaining = timeLimitMinutes * 60;

            @Override
            public void run() {
                DuelState state = activeDuels.get(duelId);

                // Se o duelo não existe mais, cancela o timer
                if (state == null) {
                    this.cancel();
                    return;
                }

                // Se todos já finalizaram, cancela o timer
                if (state.getFinishCount() >= state.getPlayerCount()) {
                    this.cancel();
                    return;
                }

                secondsRemaining--;

                // Avisos em momentos específicos
                if (secondsRemaining == 60 || secondsRemaining == 30 || secondsRemaining == 10) {
                    for (UUID uuid : state.getPlayers()) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null && p.isOnline()) {
                            p.sendMessage("§c§lTEMPO §8» §7" + secondsRemaining + " segundos restantes!");
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
                        }
                    }
                }

                // Tempo esgotado
                if (secondsRemaining <= 0) {
                    endDuelByTimeLimit(duelId);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Inicia após 1 segundo e executa a cada 1 segundo
    }

    /**
     * Finaliza o duelo quando o tempo limite é atingido.
     */
    private void endDuelByTimeLimit(int duelId) {
        DuelState duelState = activeDuels.get(duelId);
        if (duelState == null) return;

        plugin.getLogger().warning("§c[DUEL] Duelo #" + duelId + " finalizado por limite de tempo");

        // Determina o vencedor (quem está em 1º lugar no momento)
        UUID winnerUUID = determineWinnerByProgress(duelId);

        // Finaliza todos os jogadores e envia notificações
        for (UUID uuid : duelState.getPlayers()) {
            PlayerDuelState playerState = playerStates.get(uuid);
            if (playerState != null) {
                playerState.setFinished(true);
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    ttda.toggleTimer(player, duelId, false);

                    // Título para todos: TEMPO ESGOTADO
                    player.sendTitle("§c§lTEMPO ESGOTADO!", "", 10, 40, 10);

                    // Mensagem diferente para vencedor e perdedor
                    if (uuid.equals(winnerUUID)) {
                        player.sendMessage(" ");
                        player.sendMessage("§a§l✦ VITÓRIA! §fVocê estava em §a1º lugar §fquando o tempo acabou!");
                        player.sendMessage(" ");
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                    } else {
                        player.sendMessage(" ");
                        player.sendMessage("§c§l✦ DERROTA! §fO oponente estava na frente quando o tempo acabou.");
                        player.sendMessage(" ");
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    }
                }
            }
        }

        // Registra o vencedor no banco de dados
        if (winnerUUID != null) {
            dm.setDuelStateWithWinner(duelId, "FINISHED", winnerUUID);
        } else {
            dm.setDuelState(duelId, "FINISHED");
        }

        // Limpar recursos
        for (UUID uuid : duelState.getPlayers()) {
            cleanupPlayer(uuid, duelId);
        }

        activeDuels.remove(duelId);
    }

    /**
     * Determina o vencedor baseado em quem está mais avançado na pista.
     * Critérios de desempate:
     * 1. Volta atual (quem está em volta maior está mais avançado)
     * 2. Tempo total desde o início (em caso de empate de volta, quem chegou ali mais rápido)
     */
    private UUID determineWinnerByProgress(int duelId) {
        DuelState duelState = activeDuels.get(duelId);
        if (duelState == null) return null;

        UUID winner = null;
        int maxLap = -1;
        long bestTime = Long.MAX_VALUE;

        // Encontra quem está mais avançado
        for (UUID uuid : duelState.getPlayers()) {
            PlayerDuelState state = playerStates.get(uuid);
            if (state == null) continue;

            int currentLap = state.getCurrentLap();

            // Tempo total desde o início da corrida (quanto menor, mais rápido)
            long totalTime = 0;
            if (state.getFirstLapStartTime() > 0) {
                totalTime = System.currentTimeMillis() - state.getFirstLapStartTime();
            }

            // Critério 1: Quem está em volta maior está na frente
            if (currentLap > maxLap) {
                maxLap = currentLap;
                bestTime = totalTime;
                winner = uuid;
            }
            // Critério 2: Se empatados na volta, quem chegou ali mais rápido
            else if (currentLap == maxLap && totalTime < bestTime) {
                bestTime = totalTime;
                winner = uuid;
            }
        }

        return winner;
    }

    private void releasePlayers(Player p1, Player p2) {
        for (Player p : Arrays.asList(p1, p2)) {
            if (p.getVehicle() instanceof Boat boat) {
                // Se o barco estiver montado no ArmorStand, removemos o stand
                if (boat.getVehicle() instanceof ArmorStand stand) {
                    stand.remove();
                    // Ao remover o stand, o barco cai e o player ganha controle
                }
            }
        }
    }

    private void setupPlayerInGrid(Player player, Location baseLoc) {
        player.teleport(baseLoc);

        Location asLoc = baseLoc.clone().add(0, 1, 0);
        ArmorStand stand = (ArmorStand) baseLoc.getWorld().spawnEntity(asLoc, EntityType.ARMOR_STAND);

        stand.setVisible(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setMarker(true);

        Boat boat = (Boat) baseLoc.getWorld().spawnEntity(baseLoc, EntityType.OAK_BOAT);

        stand.addPassenger(boat);
        boat.addPassenger(player);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.5f);
    }

    /**
     * Chamado quando um jogador cruza a linha de START durante um duelo.
     * Incrementa a volta e inicia/reinicia o timer conforme necessário.
     */
    public void onPlayerCrossStart(Player player, int duelId) {
        PlayerDuelState state = playerStates.get(player.getUniqueId());
        DuelState duelState = activeDuels.get(duelId);

        if (state == null || duelState == null) {
            plugin.getLogger().warning("§e[DUEL] Estado não encontrado para " + player.getName() + " no duelo #" + duelId);
            return;
        }

        // Se a corrida ainda não começou (contagem regressiva), ignora
        if (!duelState.isRaceStarted()) {
            return;
        }

        // Se o jogador já finalizou, ignora
        if (state.isFinished()) {
            return;
        }

        // Se é a primeira passagem pela linha, inicia o timer
        if (state.getCurrentLap() == 0) {
            long now = System.currentTimeMillis();
            state.setCurrentLap(1);
            state.setLastCrossTime(now);
            state.setFirstLapStartTime(now); // Registra o início do tempo total
            ttda.toggleTimer(player, duelId, true);
            ttda.resetLapTimer(player); // Reseta o timer da volta para calcular delta
            scoreboardDuelsUtils.updatePlayerLap(player, 1);
            player.sendTitle("", "§e§lVOLTA 1", 0, 15, 5);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 2f);
            plugin.getLogger().info("§a[DUEL] " + player.getName() + " iniciou volta 1 no duelo #" + duelId);
        }
        // Se não é a primeira volta, incrementa apenas se já completou a volta anterior
        else {
            // Anti-spam: ignora se passou pela linha há menos de 3 segundos
            long timeSinceLastCross = System.currentTimeMillis() - state.getLastCrossTime();
            if (timeSinceLastCross < 3000) {
                return;
            }

            int currentLap = state.getCurrentLap();

            // Verifica se já não ultrapassou o limite de voltas
            if (currentLap >= duelState.getTotalLaps()) {
                return;
            }

            // Registra o tempo da volta anterior
            long lapTimeMillis = System.currentTimeMillis() - state.getLastCrossTime();
            double lapTime = lapTimeMillis / 1000.0;

            if (lapTime > 0) {
                dm.saveDuelLapTime(player.getUniqueId(), player.getName(), duelId, currentLap, lapTime, duelState.getTrackName());
                plugin.getLogger().info("§a[DUEL] " + player.getName() + " completou volta " + currentLap + " em " + String.format("%.3f", lapTime) + "s");

                // Atualiza o melhor tempo de volta para o sistema de delta
                ttda.updateBestLapTime(player, lapTime);
            }

            // Incrementa a volta
            int newLap = currentLap + 1;
            state.setCurrentLap(newLap);
            state.setLastCrossTime(System.currentTimeMillis());
            scoreboardDuelsUtils.updatePlayerLap(player, newLap);

            // Reseta o timer da volta para a próxima volta
            ttda.resetLapTimer(player);

            player.sendTitle("", "§e§lVOLTA " + newLap, 0, 15, 5);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
            plugin.getLogger().info("§a[DUEL] " + player.getName() + " iniciou volta " + newLap);

            // Se completou a última volta, marca como finalizado
            if (newLap > duelState.getTotalLaps()) {
                finishPlayerInDuel(player, duelId);
            }
        }
    }

    /**
     * Chamado quando um jogador cruza a linha de FINISH durante um duelo.
     * Se completou todas as voltas, finaliza a participação dele.
     */
    public void onPlayerCrossFinish(Player player, int duelId) {
        PlayerDuelState state = playerStates.get(player.getUniqueId());
        DuelState duelState = activeDuels.get(duelId);

        if (state == null || duelState == null) {
            return;
        }

        // Se a corrida ainda não começou ou jogador já finalizou, ignora
        if (!duelState.isRaceStarted() || state.isFinished()) {
            return;
        }

        // Verifica se completou todas as voltas
        if (state.getCurrentLap() >= duelState.getTotalLaps()) {
            finishPlayerInDuel(player, duelId);
        }
    }

    /**
     * Finaliza a participação de um jogador no duelo.
     */
    private void finishPlayerInDuel(Player player, int duelId) {
        PlayerDuelState state = playerStates.get(player.getUniqueId());
        DuelState duelState = activeDuels.get(duelId);

        if (state == null || duelState == null) return;

        // Se já está marcado como finalizado, ignora
        if (state.isFinished()) return;

        state.setFinished(true);

        // Calcula o tempo total desde o início da corrida
        long totalTimeMillis = System.currentTimeMillis() - state.getFirstLapStartTime();
        double totalTime = totalTimeMillis / 1000.0;

        // Salva o tempo total como o melhor tempo (PB)
        dm.saveDuelFinalTime(player.getUniqueId(), player.getName(), duelId, totalTime, duelState.getTrackName());

        ttda.toggleTimer(player, duelId, false);

        // Adiciona o jogador à ordem de chegada
        duelState.addFinisher(player.getUniqueId());

        int finishPosition = duelState.getFinishCount();

        player.sendTitle("§a§lFINALIZOU!", "§f" + finishPosition + "º Lugar", 10, 70, 20);
        player.sendMessage("§a§l✦ §fTempo total: §e" + formatTime(totalTime));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);

        plugin.getLogger().info("§a[DUEL] " + player.getName() + " finalizou em " + finishPosition + "º lugar no duelo #" + duelId + " - Tempo: " + String.format("%.3f", totalTime) + "s");

        // Em duelos, quando o primeiro jogador termina, o duelo acaba imediatamente
        if (finishPosition == 1) {
            // Aguarda 3 segundos antes de finalizar o duelo para dar tempo do vencedor ver a mensagem
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                endDuel(duelId);
            }, 60L); // 3 segundos (60 ticks)
        }
    }

    private String formatTime(double seconds) {
        long totalMillis = (long) (seconds * 1000);
        long minutes = totalMillis / 60000;
        long secs = (totalMillis % 60000) / 1000;
        long millis = totalMillis % 1000;
        if (minutes > 0) {
            return String.format("%d:%02d.%03d", minutes, secs, millis);
        }
        return String.format("%d.%03d", secs, millis);
    }

    /**
     * Finaliza um duelo e faz a limpeza.
     */
    private void endDuel(int duelId) {
        DuelState duelState = activeDuels.get(duelId);
        if (duelState == null) return;

        plugin.getLogger().info("§a[DUEL] Finalizando duelo #" + duelId);

        // Determinar vencedor (primeiro a finalizar)
        UUID winnerUUID = duelState.getWinner();

        if (winnerUUID != null) {
            dm.setDuelStateWithWinner(duelId, "FINISHED", winnerUUID);

            Player winner = Bukkit.getPlayer(winnerUUID);
            if (winner != null && winner.isOnline()) {
                winner.sendMessage(" ");
                winner.sendMessage("§a§l✦ VITÓRIA! §fVocê venceu o duelo!");
                winner.sendMessage(" ");
            }

            // Notificar o perdedor
            for (UUID uuid : duelState.getPlayers()) {
                if (!uuid.equals(winnerUUID)) {
                    Player loser = Bukkit.getPlayer(uuid);
                    if (loser != null && loser.isOnline()) {
                        PlayerDuelState loserState = playerStates.get(uuid);
                        if (loserState != null && !loserState.isFinished()) {
                            // Jogador não terminou ainda - perdeu por tempo
                            loser.sendTitle("§c§lDERROTA!", "§fO oponente venceu", 10, 70, 20);
                            loser.sendMessage(" ");
                            loser.sendMessage("§c§l✦ DERROTA! §fSeu oponente terminou primeiro.");
                            loser.sendMessage(" ");
                            loser.playSound(loser.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        } else {
                            // Jogador terminou mas ficou em segundo
                            loser.sendMessage(" ");
                            loser.sendMessage("§c§l✦ DERROTA! §fVocê ficou em segundo lugar.");
                            loser.sendMessage(" ");
                        }
                    }
                }
            }
        } else {
            dm.setDuelState(duelId, "FINISHED");
        }

        // Limpar recursos de todos os participantes
        for (UUID uuid : duelState.getPlayers()) {
            cleanupPlayer(uuid, duelId);
        }

        // Remover do mapa de duelos ativos
        activeDuels.remove(duelId);
    }

    /**
     * Limpa recursos de um jogador específico.
     */
    private void cleanupPlayer(UUID uuid, int duelId) {
        Player player = Bukkit.getPlayer(uuid);

        playerStates.remove(uuid);

        if (player != null && player.isOnline()) {
            ttda.stopAll(player);
            scoreboardDuelsUtils.removeBoard(player);

            DuelState state = activeDuels.get(duelId);
            if (state != null && state.isLonely()) {
                packet.applyLonelyToPlayer(player, false);
            }
        }
    }

    /**
     * Finaliza duelo por desconexão.
     */
    private void endDuelByDisconnect(int duelId) {
        DuelState duelState = activeDuels.get(duelId);
        if (duelState == null) return;

        plugin.getLogger().warning("§c[DUEL] Duelo #" + duelId + " cancelado por desconexão");

        dm.setDuelState(duelId, "CANCELLED");

        for (UUID uuid : duelState.getPlayers()) {
            cleanupPlayer(uuid, duelId);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage("§c§lDUEL §8» §7O duelo foi cancelado devido a desconexão de um jogador.");
            }
        }

        activeDuels.remove(duelId);
    }

    /**
     * Remove um jogador do duelo (quando ele sai usando /duel sair).
     */
    public void removePlayerFromDuel(UUID playerUUID, int duelId) {
        PlayerDuelState state = playerStates.get(playerUUID);
        DuelState duelState = activeDuels.get(duelId);

        if (state == null || duelState == null) return;

        cleanupPlayer(playerUUID, duelId);
        duelState.removePlayer(playerUUID);

        // Se só sobrou 1 jogador, ele vence automaticamente
        if (duelState.getPlayerCount() == 1) {
            UUID winnerUUID = duelState.getPlayers().iterator().next();
            dm.setDuelStateWithWinner(duelId, "FINISHED", winnerUUID);

            Player winner = Bukkit.getPlayer(winnerUUID);
            if (winner != null && winner.isOnline()) {
                winner.sendMessage(" ");
                winner.sendMessage("§a§lVITÓRIA! §fVocê venceu porque o oponente desistiu.");
                winner.sendMessage(" ");
                cleanupPlayer(winnerUUID, duelId);
            }

            activeDuels.remove(duelId);
        }
    }

    /**
     * Verifica se um jogador está em um duelo ativo.
     */
    public boolean isPlayerInDuel(UUID playerUUID) {
        return playerStates.containsKey(playerUUID);
    }

    /**
     * Obtém o ID do duelo ativo de um jogador.
     */
    public int getPlayerDuelId(UUID playerUUID) {
        PlayerDuelState state = playerStates.get(playerUUID);
        return state != null ? state.getDuelId() : -1;
    }

    /**
     * Calcula a posição atual de um jogador no duelo baseado na volta atual.
     * Durante a corrida, a posição é baseada em:
     * 1. Volta atual (maior volta = melhor posição)
     * 2. Se empatados na volta, usa o tempo decorrido da volta atual (menor tempo = melhor)
     */
    public int getPlayerPosition(int duelId, UUID playerUUID) {
        DuelState duelState = activeDuels.get(duelId);
        if (duelState == null) return 1;

        PlayerDuelState playerState = playerStates.get(playerUUID);
        if (playerState == null) return duelState.getPlayerCount();

        // Se a corrida ainda não começou, todos estão empatados
        if (!duelState.isRaceStarted()) {
            return 1;
        }

        int position = 1;
        int playerLap = playerState.getCurrentLap();
        long playerLapTime = System.currentTimeMillis() - playerState.getLastCrossTime();

        // Se o jogador ainda não começou (lap = 0), está em último
        if (playerLap == 0) {
            return duelState.getPlayerCount();
        }

        // Compara com todos os outros jogadores do duelo
        for (UUID otherUUID : duelState.getPlayers()) {
            if (otherUUID.equals(playerUUID)) continue;

            PlayerDuelState otherState = playerStates.get(otherUUID);
            if (otherState == null) continue;

            int otherLap = otherState.getCurrentLap();

            // Se o outro jogador não começou, ele está atrás
            if (otherLap == 0) continue;

            long otherLapTime = System.currentTimeMillis() - otherState.getLastCrossTime();

            // Se o outro jogador está em volta maior, ele está à frente
            if (otherLap > playerLap) {
                position++;
            }
            // Se estão na mesma volta, compara o tempo decorrido na volta atual
            else if (otherLap == playerLap && otherLapTime < playerLapTime) {
                position++;
            }
        }

        return position;
    }

    /**
     * Retorna o tempo restante do duelo em segundos.
     * Retorna -1 se não há limite de tempo ou se o duelo não foi encontrado.
     */
    public int getTimeRemaining(int duelId) {
        DuelState duelState = activeDuels.get(duelId);
        if (duelState == null) return -1;

        // Se não há limite de tempo, retorna -1
        if (duelState.getTimeLimit() <= 0) return -1;

        // Se a corrida ainda não começou, retorna o tempo limite total
        if (!duelState.isRaceStarted()) {
            return duelState.getTimeLimit() * 60;
        }

        // Calcula quanto tempo já passou desde o início
        long elapsedMillis = System.currentTimeMillis() - duelState.getRaceStartTime();
        long elapsedSeconds = elapsedMillis / 1000;

        // Calcula quanto tempo resta
        long totalSeconds = duelState.getTimeLimit() * 60L;
        long remainingSeconds = totalSeconds - elapsedSeconds;

        return (int) Math.max(0, remainingSeconds);
    }

    // Classes internas para gerenciar estado

    private static class DuelState {
        private final int duelId;
        private final String trackName;
        private final int totalLaps;
        private final int timeLimit;
        private final boolean lonely;
        private final Set<UUID> players;
        private final List<UUID> finishOrder;
        private boolean raceStarted;
        private long raceStartTime;

        public DuelState(int duelId, String trackName, int totalLaps, int timeLimit, boolean lonely) {
            this.duelId = duelId;
            this.trackName = trackName;
            this.totalLaps = totalLaps;
            this.timeLimit = timeLimit;
            this.lonely = lonely;
            this.players = new HashSet<>();
            this.finishOrder = new ArrayList<>();
            this.raceStarted = false;
            this.raceStartTime = 0;
        }

        public void addPlayer(UUID uuid) { players.add(uuid); }
        public void removePlayer(UUID uuid) { players.remove(uuid); }
        public Set<UUID> getPlayers() { return players; }
        public int getPlayerCount() { return players.size(); }
        public int getTotalLaps() { return totalLaps; }
        public int getTimeLimit() { return timeLimit; }
        public String getTrackName() { return trackName; }
        public boolean isLonely() { return lonely; }
        public boolean isRaceStarted() { return raceStarted; }
        public void setRaceStarted(boolean started) { this.raceStarted = started; }
        public long getRaceStartTime() { return raceStartTime; }
        public void setRaceStartTime(long time) { this.raceStartTime = time; }

        public int getFinishCount() {
            return finishOrder.size();
        }

        public UUID getWinner() {
            return finishOrder.isEmpty() ? null : finishOrder.get(0);
        }

        public void addFinisher(UUID uuid) {
            if (!finishOrder.contains(uuid)) {
                finishOrder.add(uuid);
            }
        }
    }

    private static class PlayerDuelState {
        private final UUID playerUUID;
        private final int duelId;
        private int currentLap;
        private boolean finished;
        private long lastCrossTime;
        private long firstLapStartTime; // Tempo de início da primeira volta

        public PlayerDuelState(UUID playerUUID, int duelId) {
            this.playerUUID = playerUUID;
            this.duelId = duelId;
            this.currentLap = 0;
            this.finished = false;
            this.lastCrossTime = 0;
            this.firstLapStartTime = 0;
        }

        public int getDuelId() { return duelId; }
        public int getCurrentLap() { return currentLap; }
        public void setCurrentLap(int lap) { this.currentLap = lap; }
        public boolean isFinished() { return finished; }
        public void setFinished(boolean finished) { this.finished = finished; }
        public long getLastCrossTime() { return lastCrossTime; }
        public void setLastCrossTime(long time) { this.lastCrossTime = time; }
        public long getFirstLapStartTime() { return firstLapStartTime; }
        public void setFirstLapStartTime(long time) { this.firstLapStartTime = time; }
    }
}

