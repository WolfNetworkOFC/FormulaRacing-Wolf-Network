    package dev.EfraGroup.formulaRacing;

    import dev.EfraGroup.formulaRacing.CommandHandler.*;
    import dev.EfraGroup.formulaRacing.Duels.TimeTrialDuels;
    import dev.EfraGroup.formulaRacing.TabCompleter.*;
    import dev.EfraGroup.formulaRacing.Controllers.*;
    import dev.EfraGroup.formulaRacing.Listener.*;
    import dev.EfraGroup.formulaRacing.Database.*;
    import dev.EfraGroup.formulaRacing.Utils.*;

    import org.bukkit.Bukkit;
    import org.bukkit.Location;
    import org.bukkit.configuration.file.YamlConfiguration;
    import org.bukkit.entity.Player;
    import org.bukkit.event.EventHandler;
    import org.bukkit.event.Listener;
    import org.bukkit.event.player.PlayerQuitEvent;
    import org.bukkit.plugin.java.JavaPlugin;


    import java.io.File;
    import java.nio.ByteBuffer;
    import java.sql.SQLException;
    import java.util.*;
    import java.util.logging.Level;

    public final class FormulaRacing extends JavaPlugin implements Listener {

        private static FormulaRacing instance;
        private final Map<UUID, String> lastTimeTrialTrack = new HashMap<>();
        private final Map<String, TrackLeaderboard> leaderboards = new HashMap<>();
        private static final Map<UUID, Boolean> playersWithMod = new HashMap<>();
        private static final Map<UUID, Integer> playersModVersion = new HashMap<>();
        private ScoreboardTimeTrialUtils stt;
        private LonelyController lonelyController;
        private PacketSender packetSender;
        private FileManager fileManager;
        private DatabaseManager dm;
        private WorldEditSelect worldEditSelect;
        private TimerUtils timerUtils;
        private RegionListener rcl;
        private TimeUtils tu;
        private APIFormulaRacing api;
        private CamUtils cu;
        private TrackEditorCommandHandler tetc;
        private DiscordUtils dcu;
        private EventsManager ev;
        //private RaceUtils ru;
        private TimeTrialDuelsAction ttda;
        private dev.EfraGroup.formulaRacing.Duels.TimeTrialDuels ttd;

        @Override
        public void onEnable() {
            instance = this;

            try {
                // ========== ETAPA 1: Arquivos e Banco ==========
                this.fileManager = new FileManager(this);
                this.dm = new DatabaseManager(this, fileManager);

                // ========== ETAPA 2: Utils simples ==========
                this.tu = new TimeUtils();
                this.worldEditSelect = new WorldEditSelect();
                this.dcu = new DiscordUtils();
                // ========== ETAPA 3: Utils que dependem de DB ==========
                this.stt = new ScoreboardTimeTrialUtils(dm);
                this.packetSender = new PacketSender(dm);
                this.timerUtils = new TimerUtils(this, dm);
                this.cu = new CamUtils(dm, this);
                this.lonelyController = new LonelyController(dm, this);
                this.api = new APIFormulaRacing(this, dm);
                this.tetc = new TrackEditorCommandHandler(dm, packetSender, worldEditSelect, this);
                this.ev = new EventsManager(this, fileManager, dm);
                this.ttda = new TimeTrialDuelsAction(this, dm);

                this.ttd = new TimeTrialDuels(this, dm, packetSender, ttda, new ScoreboardDuelsTimeUtils(this, dm, ttda));

                // CRIANDO A INSTÂNCIA ÚNICA DO HANDLER
                DuelCommandHandler duelHandler = new DuelCommandHandler(this, dm, ttd, ttda);

                this.rcl = new RegionListener(this, dm, timerUtils, packetSender, stt, ev, ttda);

                // ========== ETAPA 6: Scoreboard auto-update ==========
                stt.startAutoUpdate();

                // ========== ETAPA 7: Mod Channel ==========
                this.getServer().getMessenger().registerOutgoingPluginChannel(this, "openboatutils:settings");
                registerModChannel();

                // ========== ETAPA 8: Listeners & Commands ==========
                registerListeners(duelHandler); // Passando a instância única
                registerCommands(duelHandler);  // Passando a instância única

                // ========== ETAPA 9: Leaderboards ==========
                loadLeaderboards();
                startLeaderboardUpdater();

                getLogger().info("[FormulaRacing] Plugin ativado com sucesso!");
                getLogger().info("[FormulaRacing] Banco de dados conectado com sucesso!");

            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "[FormulaRacing] Erro ao inicializar o plugin: ", e);
                setEnabled(false);
            }
        }

        @Override
        public void onDisable() {
            getLogger().info("[FormulaRacing] Desativando plugin...");
            leaderboards.values().forEach(TrackLeaderboard::removeHologram);
            leaderboards.clear();
            try {
                dm.deleteAllParties();
                // Recomendado: dm.closeConnection();
            } catch (SQLException e) {
                getLogger().warning("Erro ao limpar dados no banco durante o desligamento.");
            }
        }

        // -------------------- Listeners --------------------
        private void registerListeners(DuelCommandHandler duelHandler) {
            Bukkit.getPluginManager().registerEvents(new FormulaRacingListener(this, timerUtils, api, dm, packetSender), this);
            Bukkit.getPluginManager().registerEvents(new JoinListener(this, dm, packetSender), this);
            Bukkit.getPluginManager().registerEvents(this, this); // PlayerQuitEvent
            Bukkit.getPluginManager().registerEvents(rcl, this);
            Bukkit.getPluginManager().registerEvents(new CamListener(this, cu), this);

            // CORREÇÃO: Usando a variável única para eventos
            Bukkit.getPluginManager().registerEvents(duelHandler, this);

            // Proteção de duelos contra comandos e ações não permitidas
            Bukkit.getPluginManager().registerEvents(new dev.EfraGroup.formulaRacing.Listener.DuelProtectionListener(this, dm), this);
        }

        // -------------------- Comandos --------------------
        private void registerCommands(DuelCommandHandler duelHandler) {
            try {
                // Tab Completers
                Objects.requireNonNull(this.getCommand("language")).setTabCompleter(new dev.EfraGroup.formulaRacing.TabCompleter.FRLanguageTabCompleter(this));
                Objects.requireNonNull(this.getCommand("timetrial")).setTabCompleter(new TimeTrialTabCompleter(dm));
                Objects.requireNonNull(this.getCommand("trackedit")).setTabCompleter(new TrackEditorTabCompleter(dm));
                Objects.requireNonNull(this.getCommand("voterace")).setTabCompleter(new VoteRaceTabCompleter(dm));
                Objects.requireNonNull(this.getCommand("race")).setTabCompleter(new RaceTabCompleter(dm));
                Objects.requireNonNull(this.getCommand("cam")).setTabCompleter(new CamTabCompleter());
                Objects.requireNonNull(this.getCommand("event")).setTabCompleter(new EventTabCompleter(ev, dm));
                Objects.requireNonNull(this.getCommand("lonely")).setTabCompleter(new LonelyTabCompleter(dm));
                Objects.requireNonNull(this.getCommand("settings")).setTabCompleter(new SettingsTabCompleter());
                Objects.requireNonNull(this.getCommand("track")).setTabCompleter(new TrackTabCompleter(dm));
                Objects.requireNonNull(this.getCommand("party")).setTabCompleter(new PartyTabCompleter(dm));
                Objects.requireNonNull(this.getCommand("duel")).setTabCompleter(new DuelTabCompleter());

                // Executors
                Objects.requireNonNull(this.getCommand("party")).setExecutor(new PartyCommandHandler(dm));

                // CORREÇÃO: Usando a variável única para o comando executor
                Objects.requireNonNull(this.getCommand("duel")).setExecutor(duelHandler);

                Objects.requireNonNull(this.getCommand("track")).setExecutor(new TrackCommandHandler(dm, this));
                Objects.requireNonNull(this.getCommand("settings")).setExecutor(new SettingsCommandHandler(this, dm));
                Objects.requireNonNull(this.getCommand("lonely")).setExecutor(new LonelyCommandHandler(dm));
                Objects.requireNonNull(this.getCommand("round")).setExecutor(new RoundCommandHandler(ev, dm, this));
                Objects.requireNonNull(this.getCommand("event")).setExecutor(new EventCommandHandler(ev, dm, this));
                Objects.requireNonNull(this.getCommand("camera")).setExecutor(new CamCommandHandler(this, dm, cu));
                Objects.requireNonNull(this.getCommand("timetrialcancel")).setExecutor(new TimeTrialCancelCommandHandler(timerUtils));
                Objects.requireNonNull(this.getCommand("timetrialrandom")).setExecutor(new TimeTrialRandomCommandHandler(dm, this, packetSender, timerUtils, api, stt));
                Objects.requireNonNull(this.getCommand("reset")).setExecutor(new ResetCommandHandler(this, dm, timerUtils, api));
                Objects.requireNonNull(this.getCommand("language")).setExecutor(new FRLanguageCommandHandler(this, fileManager, dm));
                Objects.requireNonNull(this.getCommand("timetrial")).setExecutor(new TimeTrialCommandHandler(dm, this, packetSender, timerUtils, rcl, api, stt, ev));
                Objects.requireNonNull(this.getCommand("debug")).setExecutor(new DebugCommandHandler(this));
                Objects.requireNonNull(this.getCommand("formularacingreload")).setExecutor(new FormulaRacingReloadCommandHandler(fileManager, dm, this));
                Objects.requireNonNull(this.getCommand("boat")).setExecutor(new BoatCommandHandler(api));
                Objects.requireNonNull(this.getCommand("trackedit")).setExecutor(new TrackEditorCommandHandler(dm, packetSender, worldEditSelect, this));

            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "[FormulaRacing] Erro ao registrar comandos: ", e);
            }
        }
        /**
         * Busca uma tradução diretamente de um arquivo na pasta /lang/
         * @param key A chave no arquivo (ex: "lang_set")
         * @param langCode O código da linguagem (ex: "pt-BR")
         * @return A mensagem formatada ou a chave caso não encontre
         */
        public String getDirectTranslation(String key, String langCode) {
            // Caminho para o arquivo: plugins/FormulaRacing/lang/pt-BR.yml
            File langFile = new File(getDataFolder(), "lang/" + langCode + ".yml");

            if (!langFile.exists()) {
                // Fallback: se o idioma do jogador não existir, tenta o padrão (ex: en-US)
                langFile = new File(getDataFolder(), "lang/en_US.yml");
                if (!langFile.exists()) return "§c[Lang Error] File not found: " + langCode;
            }

            // Carrega o arquivo YAML
            YamlConfiguration config = YamlConfiguration.loadConfiguration(langFile);

            // Pega o valor da chave
            String message = config.getString(key);

            if (message == null) {
                return "§c[Lang Error] Key '" + key + "' not found in " + langCode + ".yml";
            }

            // Traduz as cores (& para §)
            return org.bukkit.ChatColor.translateAlternateColorCodes('&', message);
        }

        /**
         * Busca uma tradução com suporte a placeholders
         * @param key A chave no arquivo (ex: "track_not_found")
         * @param langCode O código da linguagem (ex: "pt_BR")
         * @param placeholders Array de pares chave-valor para substituir (ex: "{track}", "MinhaTrack")
         * @return A mensagem formatada com placeholders substituídos
         */
        public String getTranslation(String key, String langCode, String... placeholders) {
            String message = getDirectTranslation(key, langCode);

            // Substitui placeholders
            if (placeholders != null && placeholders.length > 0) {
                for (int i = 0; i < placeholders.length - 1; i += 2) {
                    String placeholder = placeholders[i];
                    String value = placeholders[i + 1];
                    message = message.replace(placeholder, value);
                }
            }

            return message;
        }

        /**
         * Helper method para enviar uma mensagem traduzida para um jogador
         * @param player O jogador que receberá a mensagem
         * @param key A chave de tradução
         * @param placeholders Placeholders opcionais para substituir
         */
        public void sendMessage(org.bukkit.entity.Player player, String key, String... placeholders) {
            String langCode = dm.getPlayerLanguage(player.getUniqueId());
            String message = getTranslation(key, langCode, placeholders);
            player.sendMessage(message);
        }

        // -------------------- Leaderboards --------------------
        public void startLeaderboardUpdater() {
            // Mantemos runTaskTimer para disparar a lógica,
            // mas a classe TrackLeaderboard cuidará do Async internamente
            Bukkit.getScheduler().runTaskTimer(this, () -> {

                // Verificação de segurança: se não houver jogadores online,
                // podemos pular a atualização para poupar recursos
                if (Bukkit.getOnlinePlayers().isEmpty()) return;

                leaderboards.values().forEach(leaderboard -> {
                    // Chamamos o update que agora é Thread-Safe e Assíncrono
                    leaderboard.updateLeaderboard();
                });

            }, 100L, 20L * 15); // Aumentado para 15 segundos (mais do que suficiente para hologramas)
        }
        public TrackLeaderboard getOrCreateLeaderboard(String trackName, Location defaultLocation) {
            return leaderboards.computeIfAbsent(trackName,
                    tn -> new TrackLeaderboard(this, tn, defaultLocation, dm));
        }

        private void loadLeaderboards() {
            try {
                for (String trackName : dm.getAllTracks()) {
                    Location defaultLoc = getServer().getWorlds().get(0).getSpawnLocation();
                    getOrCreateLeaderboard(trackName, defaultLoc);
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "[FormulaRacing] Erro ao carregar leaderboards: ", e);
            }
        }

        // -------------------- Mod OpenBoatUtils --------------------
        private void registerModChannel() {
            getServer().getMessenger().registerIncomingPluginChannel(this, "openboatutils:settings", (channel, player, message) -> {
                if (!channel.equals("openboatutils:settings")) return;
                ByteBuffer buf = ByteBuffer.wrap(message);
                short packetId = buf.getShort();
                int versionId = buf.getInt();
                if (packetId == 0) {
                    setPlayerHasMod(player.getUniqueId(), true);
                    setPlayerModVersion(player.getUniqueId(), versionId);
                    getLogger().info("[FormulaRacing] Player " + player.getName() + " entrou com OpenBoatUtils v" + versionId);
                }
            });
        }

        public static void setPlayerHasMod(UUID uuid, boolean hasMod) {
            playersWithMod.put(uuid, hasMod);
            if (!hasMod) playersModVersion.remove(uuid);
        }

        public static boolean hasOpenBoatUtilsMod(Player player) {
            return playersWithMod.getOrDefault(player.getUniqueId(), false);
        }

        public static void setPlayerModVersion(UUID uuid, int version) {
            playersModVersion.put(uuid, version);
        }

        public static int getPlayerModVersion(Player player) {
            return playersModVersion.getOrDefault(player.getUniqueId(), -1);
        }

        // -------------------- Getters --------------------
        public TimerUtils getTimerUtils() {
            return timerUtils;
        }

        public FileManager getFileManager() {
            return fileManager;
        }

        public void setLastTimeTrialTrack(UUID playerId, String track) {
            lastTimeTrialTrack.put(playerId, track);
        }

        public String getLastTimeTrialTrack(UUID playerId) {
            return lastTimeTrialTrack.get(playerId);
        }

        public static FormulaRacing getInstance() {
            return instance;
        }

        // -------------------- Eventos --------------------
        @EventHandler
        public void onPlayerQuit(PlayerQuitEvent event) {
            setPlayerHasMod(event.getPlayer().getUniqueId(), false);
        }
    }
