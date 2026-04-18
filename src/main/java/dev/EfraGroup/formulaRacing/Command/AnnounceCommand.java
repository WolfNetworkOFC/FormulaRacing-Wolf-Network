package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.annotation.*;
import co.aikar.commands.BaseCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

@CommandAlias("announce|anuncio") // Define o comando e um alias opcional
public class AnnounceCommand extends BaseCommand {

    @Default // Define que este método será executado ao usar /announce diretamente
    @CommandPermission("formularacing.admin") // ACF cuida da permissão automaticamente
    @Description("Envia um anúncio global para todo o servidor.")
    @Syntax("<mensagem>") // Mensagem de erro automática se o argumento estiver faltando
    public void onAnnounce(CommandSender sender, String mensagem) {

        // O ACF já faz o "String.join" automaticamente se o último parâmetro for String

        Bukkit.broadcastMessage("§6=============== §c§lAnuncio §6===============");
        Bukkit.broadcastMessage("§f" + mensagem.replace("&", "§")); // Permite cores no anúncio
        Bukkit.broadcastMessage("§6=======================================");
    }
}