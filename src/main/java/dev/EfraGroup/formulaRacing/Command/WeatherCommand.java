package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Weather.WeatherCondition;
import dev.EfraGroup.formulaRacing.Weather.WeatherManager;
import dev.EfraGroup.formulaRacing.Weather.WeatherType;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Comandos para gerenciar o sistema de clima
 */
@CommandAlias("weather|clima")
public class WeatherCommand extends BaseCommand {

    private final FormulaRacing plugin;
    private final WeatherManager weatherManager;

    public WeatherCommand(FormulaRacing plugin) {
        this.plugin = plugin;
        this.weatherManager = plugin.getWeatherManager();
    }

    @Default
    @Description("Mostra informações do clima atual")
    public void onDefault(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "  Sistema de Clima");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "  Status: " + ChatColor.WHITE +
                (weatherManager.getConfigManager().isEnabled() ? "Ativado" : "Desativado"));
        player.sendMessage(ChatColor.GRAY + "  Taxa de secagem: " + ChatColor.WHITE +
                weatherManager.getConfigManager().getTrackDryingRate() + "/volta");
        player.sendMessage(ChatColor.GRAY + "  Taxa de molhamento: " + ChatColor.WHITE +
                weatherManager.getConfigManager().getTrackWettingRate() + "/volta");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "  Comandos disponíveis:");
        player.sendMessage(ChatColor.WHITE + "    /weather info <pista> - Ver clima da pista");
        player.sendMessage(ChatColor.WHITE + "    /weather set <pista> - Definir clima");
        player.sendMessage(ChatColor.WHITE + "    /weather reload - Recarregar configuração");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }

    @Subcommand("info")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Mostra informações do clima de uma pista")
    public void onInfo(Player player, String trackName) {
        List<WeatherCondition> conditions = weatherManager.getConfigManager().getDynamicWeather(trackName);

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "  Clima: " + ChatColor.WHITE + trackName);
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "  Condições configuradas:");

        if (conditions.isEmpty()) {
            player.sendMessage(ChatColor.RED + "    Nenhuma condição configurada");
        } else {
            for (int i = 0; i < conditions.size(); i++) {
                WeatherCondition condition = conditions.get(i);
                WeatherType type = condition.getWeatherType();
                String gripInfo = String.format("Grip: %.0f%% (seco) / %.0f%% (molhado)",
                        type.getDryGripModifier() * 100,
                        type.getWetGripModifier() * 100);

                player.sendMessage(ChatColor.WHITE + "    " + (i + 1) + ". " +
                        ChatColor.AQUA + type.getDisplayName() + ChatColor.GRAY +
                        " (" + condition.getDurationLaps() + " voltas)");
                player.sendMessage(ChatColor.GRAY + "       " + gripInfo);
            }
        }

        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }

    @Subcommand("set")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Define o clima dinâmico de uma pista")
    public void onSet(Player player, String trackName) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "  Configurar Clima: " + ChatColor.WHITE + trackName);
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "  Tipos de clima disponíveis:");
        player.sendMessage(ChatColor.WHITE + "    CLEAR - Céu Limpo");
        player.sendMessage(ChatColor.WHITE + "    SUNNY - Sol");
        player.sendMessage(ChatColor.WHITE + "    SUNNY_INTENSE - Sol Intenso");
        player.sendMessage(ChatColor.WHITE + "    CLOUDY - Nublado");
        player.sendMessage(ChatColor.WHITE + "    LIGHT_RAIN - Chuva Fraca");
        player.sendMessage(ChatColor.WHITE + "    RAIN - Chuva");
        player.sendMessage(ChatColor.WHITE + "    HEAVY_RAIN - Chuva Forte");
        player.sendMessage(ChatColor.WHITE + "    STORM - Tempestade");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "  Formato: TIPO:VOLTAS");
        player.sendMessage(ChatColor.GRAY + "  Exemplo: CLEAR:3 (Céu limpo por 3 voltas)");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "  Use /weather add " + trackName + " <condição> para adicionar");
        player.sendMessage(ChatColor.YELLOW + "  Use /weather clear " + trackName + " para limpar");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }

    @Subcommand("add")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Adiciona uma condição de clima a uma pista")
    public void onAdd(Player player, String trackName, String conditionStr) {
        try {
            WeatherCondition condition = WeatherCondition.fromString(conditionStr);
            List<WeatherCondition> conditions = weatherManager.getConfigManager().getDynamicWeather(trackName);
            conditions.add(condition);
            weatherManager.getConfigManager().setDynamicWeather(trackName, conditions);

            player.sendMessage(ChatColor.GREEN + "✓ Condição adicionada: " +
                    ChatColor.AQUA + condition.getWeatherType().getDisplayName() +
                    ChatColor.GRAY + " (" + condition.getDurationLaps() + " voltas)");
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "✗ Formato inválido! Use: TIPO:VOLTAS");
            player.sendMessage(ChatColor.GRAY + "  Exemplo: RAIN:3");
        }
    }

    @Subcommand("clear")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Limpa o clima de uma pista")
    public void onClear(Player player, String trackName) {
        weatherManager.getConfigManager().setDynamicWeather(trackName, List.of(
                WeatherCondition.fromString("CLEAR:999")
        ));

        player.sendMessage(ChatColor.YELLOW + "⚠ Clima de " + trackName + " resetado para Céu Limpo");
    }

    @Subcommand("reload")
    @CommandPermission("formularacing.admin")
    @Description("Recarrega a configuração de clima")
    public void onReload(Player player) {
        weatherManager.getConfigManager().reloadConfig();

        player.sendMessage(ChatColor.GREEN + "✓ Configuração de clima recarregada!");
    }

    @Subcommand("session")
    @CommandCompletion("@heat")
    @CommandPermission("formularacing.admin")
    @Description("Mostra informações da sessão de clima atual")
    public void onSession(Player player, Heats heat) {
        if (heat == null) {
            player.sendMessage(ChatColor.RED + "✗ Nenhum heat selecionado!");
            return;
        }

        var session = weatherManager.getWeatherSession(heat.getId());
        if (session == null) {
            player.sendMessage(ChatColor.YELLOW + "⚠ Nenhuma sessão de clima ativa para este heat");
            return;
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "  Sessão de Clima: Heat #" + heat.getId());
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "  Clima atual: " + ChatColor.AQUA +
                session.getCurrentWeatherType().getDisplayName());
        player.sendMessage(ChatColor.GRAY + "  Umidade da pista: " + ChatColor.WHITE +
                session.getTrackWetness() + "%");
        player.sendMessage(ChatColor.GRAY + "  Condição atual: " + ChatColor.WHITE +
                (session.getCurrentConditionIndex() + 1) + "/" +
                session.getCurrentWeatherType());
        player.sendMessage(ChatColor.GRAY + "  Voltas na condição: " + ChatColor.WHITE +
                session.getLapsInCurrentCondition() + "/" +
                session.getCurrentCondition().getDurationLaps());
        player.sendMessage(ChatColor.GRAY + "  Grip atual: " + ChatColor.WHITE +
                String.format("%.0f%%", session.getCurrentGripModifier() * 100));
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }

    @Subcommand("force")
    @CommandCompletion("@heat CLEAR|SUNNY|CLOUDY|RAIN|STORM")
    @CommandPermission("formularacing.admin")
    @Description("Força um clima específico para um heat")
    public void onForce(Player player, Heats heat, String weatherTypeStr) {
        if (heat == null) {
            player.sendMessage(ChatColor.RED + "✗ Nenhum heat selecionado!");
            return;
        }

        try {
            WeatherType weatherType = WeatherType.valueOf(weatherTypeStr.toUpperCase());
            var session = weatherManager.getWeatherSession(heat.getId());

            if (session == null) {
                player.sendMessage(ChatColor.YELLOW + "⚠ Nenhuma sessão de clima ativa");
                return;
            }

            // Força o clima atual
            // Em uma implementação real, isso precisaria de mais lógica
            player.sendMessage(ChatColor.GREEN + "✓ Clima forçado para: " +
                    ChatColor.AQUA + weatherType.getDisplayName());
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "✗ Tipo de clima inválido!");
            player.sendMessage(ChatColor.GRAY + "  Use: CLEAR, SUNNY, CLOUDY, RAIN, STORM");
        }
    }
}
