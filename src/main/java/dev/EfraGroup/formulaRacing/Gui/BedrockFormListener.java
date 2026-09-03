package dev.EfraGroup.formulaRacing.Gui;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/**
 * Listener para processar respostas de Bedrock Forms do Floodgate
 */
public class BedrockFormListener implements Listener {

    private final FormulaRacing plugin;

    public BedrockFormListener(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    /**
     * Registra o listener de forms via reflection
     * (Cumulus Forms API do Floodgate)
     */
    public void register() {
        try {
            // Tentar registrar listener via Floodgate
            Class<?> floodgateApi = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object instance = floodgateApi.getMethod("getInstance").invoke(null);

            // Criar listener proxy para formularios
            Object formListener = java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Class.forName("org.geysermc.cumulus.event.FormResponseEvent$Handler")},
                (proxy, method, args) -> {
                    if (method.getName().equals("handle")) {
                        handleFormResponse(args[0]);
                    }
                    return null;
                }
            );

            // Registrar listener
            floodgateApi.getMethod("addFormListener", Class.forName("org.geysermc.cumulus.event.FormResponseEvent$Handler"))
                .invoke(instance, formListener);

            plugin.getLogger().info("Bedrock Form Listener registrado!");

        } catch (Exception e) {
            plugin.getLogger().warning("Nao foi possivel registrar Bedrock Form Listener: " + e.getMessage());
        }
    }

    private void handleFormResponse(Object event) {
        try {
            // Extrair dados do evento via reflection
            Object form = event.getClass().getMethod("form").invoke(event);
            Object player = event.getClass().getMethod("player").invoke(event);
            Object response = event.getClass().getMethod("response").invoke(event);

            if (response == null) return; // Form fechado sem resposta

            // Extrair ID do form e dados da resposta
            String formId = form.getClass().getMethod("getId").invoke(form).toString();

            // Verificar se e um Ready Check
            if (formId.startsWith("ready-check")) {
                // Extrair botao clicado
                String buttonText = response.toString();

                // Pegar player
                Player bukkitPlayer = Bukkit.getPlayer(java.util.UUID.fromString(
                    player.getClass().getMethod("getUuid").invoke(player).toString()
                ));

                if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                    plugin.getReadyCheckManager().handleBedrockFormResponse(bukkitPlayer, buttonText);
                }
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Erro ao processar Bedrock Form response: " + e.getMessage());
        }
    }
}
