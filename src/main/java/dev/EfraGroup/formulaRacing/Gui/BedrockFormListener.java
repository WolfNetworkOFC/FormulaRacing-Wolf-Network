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
            Class<?> handlerClass = Class.forName("org.geysermc.cumulus.event.FormResponseEvent$Handler");
            Object formListener = java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{handlerClass},
                (proxy, method, args) -> {
                    plugin.getLogger().info("[BedrockForm] Evento recebido: " + method.getName());
                    if (method.getName().equals("handle")) {
                        handleFormResponse(args[0]);
                    }
                    return null;
                }
            );

            // Registrar listener
            floodgateApi.getMethod("addFormListener", handlerClass).invoke(instance, formListener);

            plugin.getLogger().info("Bedrock Form Listener registrado com sucesso!");

        } catch (Exception e) {
            plugin.getLogger().warning("Nao foi possivel registrar Bedrock Form Listener: " + e.getMessage());
            e.printStackTrace();
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
            String formId = null;
            try {
                formId = form.getClass().getMethod("getId").invoke(form).toString();
            } catch (Exception e) {
                // Tenta obter ID via toString do form se getId não existir
                formId = form.toString();
            }

            plugin.getLogger().info("[BedrockForm] Resposta recebida - Form ID: " + formId + ", Player: " + player);

            // Verificar se e um Ready Check
            if (formId != null && formId.startsWith("ready-check")) {
                // Extrair botao clicado - tenta diferentes métodos
                String buttonText = extractButtonText(response);

                plugin.getLogger().info("[BedrockForm] Botao clicado: " + buttonText);

                // Pegar player
                Player bukkitPlayer = Bukkit.getPlayer(java.util.UUID.fromString(
                    player.getClass().getMethod("getUuid").invoke(player).toString()
                ));

                if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                    plugin.getReadyCheckManager().handleBedrockFormResponse(bukkitPlayer, buttonText);
                } else {
                    plugin.getLogger().warning("[BedrockForm] Player nao encontrado ou offline");
                }
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Erro ao processar Bedrock Form response: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Extrai o texto do botao clicado da resposta do form
     */
    private String extractButtonText(Object response) {
        try {
            // Tenta diferentes métodos para extrair a resposta
            // SimpleForm retorna o indice do botao (Integer) ou texto
            
            // Método 1: toString()
            String text = response.toString();
            if (text != null && !text.isEmpty()) {
                // Se for um numero (indice), converte para texto do botao
                try {
                    int index = Integer.parseInt(text);
                    if (index == 0) return "Ready"; // Primeiro botao
                    return "Button " + index;
                } catch (NumberFormatException e) {
                    // Não é numero, retorna o texto direto
                    return text;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erro ao extrair botao: " + e.getMessage());
        }
        return "";
    }
}
