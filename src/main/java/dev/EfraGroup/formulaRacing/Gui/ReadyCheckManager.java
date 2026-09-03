package dev.EfraGroup.formulaRacing.Gui;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Utils.TitleHelper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import dev.EfraGroup.formulaRacing.Utils.FRTask;

public class ReadyCheckManager implements Listener {
    private final FormulaRacing plugin;
    private final Map<Integer, Set<UUID>> readyPlayersByHeat = new HashMap();
    private final Map<Integer, ReadyCheckView> activeViews = new HashMap();
    private final Map<Integer, Runnable> callbacks = new HashMap();
    private final Map<Integer, FRTask> activeTasks = new HashMap();
    private final Map<Integer, UUID> initiatorsByHeat = new HashMap();

    public ReadyCheckManager(FormulaRacing plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void startReadyCheck(Heats heat, Player admin) {
        this.startReadyCheckInternal(heat, admin != null ? admin.getUniqueId() : null, (Runnable)null);
    }

    public void startAutoReadyCheck(Heats heat, Runnable onComplete) {
        this.startReadyCheckInternal(heat, (UUID)null, onComplete);
    }

    private void startReadyCheckInternal(final Heats heat, UUID initiator, Runnable callback) {
        final int heatId = heat.getId();
        this.readyPlayersByHeat.put(heatId, new HashSet());
        if (initiator != null) {
            this.initiatorsByHeat.put(heatId, initiator);
        }

        if (callback != null) {
            this.callbacks.put(heatId, callback);
        }

        ReadyCheckView view = new ReadyCheckView(heat);
        this.activeViews.put(heatId, view);
        this.updateView(heatId);
        if (initiator != null) {
            Player admin = Bukkit.getPlayer(initiator);
            if (admin != null) {
                view.show(admin);
            }
        }

        FRTask task = SchedulerHelper.runTaskTimer(this.plugin, (scheduledTask) -> {
            Set<UUID> ready = ReadyCheckManager.this.readyPlayersByHeat.get(heatId);
            if (ready == null) {
                scheduledTask.cancel();
            } else {
                for(Driver driver : heat.getDrivers().values()) {
                    if (!ready.contains(driver.getUuid())) {
                        Player p = Bukkit.getPlayer(driver.getUuid());
                        if (p != null && p.isOnline()) {
                            // Enviar Bedrock Form para jogadores Bedrock
                            if (isBedrockPlayer(p)) {
                                sendBedrockReadyCheck(p, heat);
                            } else {
                                // Java: comportamento original
                                String playerLang = plugin.getDatabaseManager().getPlayerLanguage(p.getUniqueId());
                                p.sendMessage(plugin.getDirectTranslation("ready_check_ready_text", playerLang));
                                p.sendMessage(plugin.getDirectTranslation("ready_check_press_text", playerLang));
                                TitleHelper.sendThemedTitle(p,
                                    plugin.getTranslation("ready_check_title", playerLang),
                                    plugin.getDirectTranslation("ready_check_press_text", playerLang),
                                    10, 280, 10);
                                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.0F);
                            }
                        }
                    }
                }
            }
        }, 0L, 300L);
        this.activeTasks.put(heatId, task);
    }

    public void openReadyCheck(Heats heat, Player admin) {
        ReadyCheckView view = (ReadyCheckView)this.activeViews.get(heat.getId());
        if (view != null) {
            view.show(admin);
        } else {
            this.startReadyCheck(heat, admin);
        }

    }

    private void updateView(int heatId) {
        ReadyCheckView view = this.activeViews.get(heatId);
        Set<UUID> ready = this.readyPlayersByHeat.get(heatId);
        if (view != null && ready != null) {
            view.update(ready);
        }

    }

    private void notifyAllReady(Heats heat) {
        String msg = plugin.getTranslation("ready_check_all_ready", "en_US", new String[]{"{heat}", String.valueOf(heat.getId())});

        // Corrigido: HashSet agora com tipo <Player> definido
        Set<Player> playersToNotify = new HashSet<>();

        // Adiciona Administradores
        Bukkit.getOnlinePlayers().stream()
                .filter(px -> px.hasPermission("formularacing.event.admin"))
                .forEach(playersToNotify::add);

        // Adiciona os Pilotos do Heat
        for (UUID uuid : heat.getDrivers().keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                playersToNotify.add(p);
            }
        }

        // Envia notificações
        for (Player p : playersToNotify) {
            p.sendMessage(msg);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.2F);
        }

        // Gerenciamento de Callback
        Runnable callback = this.callbacks.get(heat.getId());
        if (callback != null) {
            this.plugin.getDebugManager().logRaceSystem("Ready Check completo para heat " + heat.getId() + ". Executando callback.");
            this.stopReadyCheck(heat.getId());
            callback.run();
        } else {
            this.stopReadyCheck(heat.getId());
        }
    }

    @EventHandler
    public void onShift(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            this.handleReady(event.getPlayer());
        }
    }

    private void handleReady(Player player) {
        UUID uuid = player.getUniqueId();

        for(Map.Entry<Integer, Set<UUID>> entry : this.readyPlayersByHeat.entrySet()) {
            int heatId = entry.getKey();
            Heats heat = this.plugin.getRaceEventManager().getHeat(heatId).orElse(null);
            if (heat != null && heat.getDriver(uuid) != null) {
                Set<UUID> ready = entry.getValue();
                if (!ready.contains(uuid)) {
                    ready.add(uuid);
                    String playerLang = plugin.getDatabaseManager().getPlayerLanguage(uuid);
                    player.sendMessage(plugin.getDirectTranslation("ready_check_player_ready", playerLang));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
                    player.resetTitle();
                    this.updateView(heatId);
                    String name = player.getName();
                    String readyMsg = plugin.getDirectTranslation("ready_check_player_ready", playerLang);
                    Bukkit.getOnlinePlayers().stream().filter((p) -> p.hasPermission("formularacing.event.admin")).forEach((p) -> {
                        String pLang = plugin.getDatabaseManager().getPlayerLanguage(p.getUniqueId());
                        p.sendMessage(ChatColor.GRAY + "[ReadyCheck] " + ChatColor.WHITE + name + " " + plugin.getDirectTranslation("ready_check_player_ready", pLang));
                    });
                    if (ready.size() >= heat.getDrivers().size()) {
                        this.notifyAllReady(heat);
                    }
                }
                break;
            }
        }

    }

    public boolean isReadyCheckActive(int heatId) {
        return this.activeViews.containsKey(heatId);
    }

    public void stopReadyCheck(int heatId) {
        this.activeViews.remove(heatId);
        this.readyPlayersByHeat.remove(heatId);
        this.initiatorsByHeat.remove(heatId);
        this.callbacks.remove(heatId);
        FRTask task = (FRTask)this.activeTasks.remove(heatId);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Verifica se um jogador e Bedrock via Floodgate
     */
    private boolean isBedrockPlayer(Player player) {
        try {
            Class<?> floodgateApi = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object instance = floodgateApi.getMethod("getInstance").invoke(null);
            return (Boolean) floodgateApi.getMethod("isFloodgatePlayer", UUID.class).invoke(instance, player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Envia Ready Check via Bedrock Forms para jogador Bedrock
     */
    private void sendBedrockReadyCheck(Player player, Heats heat) {
        try {
            // Cumulus Forms API - Floodgate 2.0+
            // SimpleForm.builder() retorna Builder
            Class<?> simpleFormClass = Class.forName("org.geysermc.cumulus.form.SimpleForm");
            Object builder = simpleFormClass.getMethod("builder").invoke(null);

            // Titulo e conteudo
            String title = "Ready Check - Heat #" + heat.getId();
            String content = "Pressione Ready para confirmar que esta pronto para a corrida!";

            // Adicionar titulo, conteudo e botao
            Class<?> builderClass = builder.getClass();
            builderClass.getMethod("title", String.class).invoke(builder, title);
            builderClass.getMethod("content", String.class).invoke(builder, content);
            builderClass.getMethod("button", String.class).invoke(builder, "Ready");

            // Response handler - forma correta de processar resposta
            // responseHandler(BiConsumer<? extends Form, String>)
            Object responseHandler = java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Class.forName("java.util.function.BiConsumer")},
                (proxy, method, args) -> {
                    if (method.getName().equals("accept")) {
                        handleFormResponse(args[0], args[1], player);
                    }
                    return null;
                }
            );
            builderClass.getMethod("responseHandler", java.util.function.BiConsumer.class).invoke(builder, responseHandler);

            // Build form
            Object form = builderClass.getMethod("build").invoke(builder);

            // Enviar form via Floodgate
            Class<?> floodgateApi = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object instance = floodgateApi.getMethod("getInstance").invoke(null);
            Class<?> formClass = Class.forName("org.geysermc.cumulus.form.Form");
            floodgateApi.getMethod("sendForm", UUID.class, formClass).invoke(instance, player.getUniqueId(), form);

            // Som de notificacao
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.0F);

        } catch (Exception e) {
            // Fallback: enviar mensagem normal se Forms nao disponivel
            String playerLang = plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId());
            player.sendMessage(plugin.getDirectTranslation("ready_check_ready_text", playerLang));
            player.sendMessage(plugin.getDirectTranslation("ready_check_press_text", playerLang));
            TitleHelper.sendThemedTitle(player,
                plugin.getTranslation("ready_check_title", playerLang),
                plugin.getDirectTranslation("ready_check_press_text", playerLang),
                10, 280, 10);
            plugin.getLogger().warning("Erro ao enviar Bedrock Form para " + player.getName() + ": " + e.getMessage());
        }
    }

    private void handleFormResponse(Object form, Object responseData, Player player) {
        try {
            plugin.getLogger().info("[BedrockForm] Resposta recebida de " + player.getName() + ": " + responseData);
            
            if (responseData == null || responseData.toString().isEmpty()) {
                // Form fechado sem resposta
                return;
            }

            // Parse response usando o metodo do form
            Object response = form.getClass().getMethod("parseResponse", String.class).invoke(form, responseData.toString());
            
            // Verificar se resposta e valida
            Boolean isCorrect = (Boolean) response.getClass().getMethod("isCorrect").invoke(response);
            if (!isCorrect) {
                plugin.getLogger().info("[BedrockForm] Resposta invalida de " + player.getName());
                return;
            }

            // SimpleForm retorna o indice do botao clicado
            // Button "Ready" e o primeiro (indice 0)
            plugin.getLogger().info("[BedrockForm] " + player.getName() + " clicou Ready!");
            
            if (player.isOnline()) {
                handleReady(player);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erro ao processar resposta: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Processa resposta do Bedrock Form (chamado pelo listener de Forms)
     */
    public void handleBedrockFormResponse(Player player, String buttonText) {
        if ("Ready".equalsIgnoreCase(buttonText)) {
            this.handleReady(player);
        }
    }
}

