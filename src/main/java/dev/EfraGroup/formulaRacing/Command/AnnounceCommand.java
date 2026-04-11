package dev.EfraGroup.formulaRacing.Command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class AnnounceCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("formularacing.admin")) {
            sender.sendMessage("§cVocê não tem permissão!");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§eUso correto: /announce <mensagem>");
            return true;
        }

        String mensagem = String.join(" ", args);

        // Envia as Strings puras para todo o servidor
        Bukkit.broadcastMessage("§6=============== §c§lAnuncio §6===============");
        Bukkit.broadcastMessage("§f" + mensagem);
        Bukkit.broadcastMessage("§6=======================================");

        return true;
    }
}