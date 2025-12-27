package dev.EfraGroup.formulaRacing.CommandHandler;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Database.EventsManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class RoundCommandHandler implements CommandExecutor {

    private final EventsManager eventManager;
    private final DatabaseManager database;
    private final FormulaRacing plugin;

    public RoundCommandHandler(EventsManager eventManager, DatabaseManager database, FormulaRacing plugin) {
        this.eventManager = eventManager;
        this.database = database;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command cannot be executed from the console.");
            return true;
        }


        if (args.length < 1) {
            player.sendMessage("§cUso: /round <create/finish/fillheats/info/list/results/delete>");
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create":
                return handleCreate(player, args);

            case "finish":
                return handleFinish(player, args);

            case "fillheats":
               // return handleFillHeats(player, args);

            case "info":
               // return handleInfo(player, args);

            case "list":
                //return handleList(player, args);

            case "results":
               //return handleResults(player, args);

            case "delete":
                //return handleDelete(player, args);

            default:
                player.sendMessage("§cUso: /round <create/finish/fillheats/info/list/results/delete>");
                return true;
        }
    }

    // ========================================================================
//  /round create <final|qualification>
// ========================================================================
    private boolean handleCreate(Player player, String[] args) {

        if (args.length < 2) {
            player.sendMessage("§cUso correto: /round create <final|qualification>");
            return true;
        }

        String typeInput = args[1].toLowerCase();

        // Validar tipo de round
        String roundType;
        switch (typeInput) {
            case "final":
                roundType = "FINAL";
                break;

            case "qualification":
                roundType = "QUALIFICATION";
                break;

            default:
                player.sendMessage("§cTipo inválido! Use: §ffinal §cou §fqualification");
                return true;
        }

        if (eventManager.getEventTrack(eventManager.getEventIDByName(eventManager.getSelectedEvent(player.getUniqueId()))) == null) {
            player.sendMessage("§aVocê não setou a pista para o evento");
        } else {
            eventManager.createRound(eventManager.getEventIDByName(eventManager.getSelectedEvent(player.getUniqueId())), roundType, "SETUP");
            player.sendMessage("§aRound criado com sucesso! Tipo: §f" + roundType);
        }
        return true;
    }
    // ========================================================================
    //  /round finish <roundName>
    // ========================================================================
    private boolean handleFinish(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUso correto: /round finish <roundName>");
            return true;
        }

        String name = args[1];
        // TODO lógica
        player.sendMessage("§eRound finalizado: §f" + name);
        return true;
    }
/*
    // ========================================================================
// /round fillheats <sorted/random> <all/signed/reserves>
// ========================================================================
    private boolean handleFillHeats(Player player, String[] args) {

        if (args.length < 3) {
            player.sendMessage("§cUso correto: /round fillheats <sorted/random> <all/signed/reserves>");
            return true;
        }

        String mode = args[1].toLowerCase();        // sorted OR random
        String source = args[2].toLowerCase();      // all OR signed OR reserves

        // -----------------------------------------------------
        // Buscar evento ativo do player
        // -----------------------------------------------------
        String event = eventManager.getSelectedEvent(player.getUniqueId());
        if (event == null) {
            player.sendMessage("§cVocê não está com um evento selecionado.");
            return true;
        }

        int eventId = eventManager.getEventIDByName(event);
        String trackName = eventManager.getEventTrack(eventId);

        // -----------------------------------------------------
        // Round selecionado
        // -----------------------------------------------------
        //Rounds round = eventManager.getSelectedRound(player.getUniqueId());
        if (round == null) {
            player.sendMessage("§cVocê não selecionou nenhum round.");
            return true;
        }

        //int roundId = round.getId();

        // -----------------------------------------------------
        // Checar state do round
        // -----------------------------------------------------
        String roundState = eventManager.getRoundState(roundId);

        if (!roundState.equalsIgnoreCase("SETUP")) {
            player.sendMessage("§cEsse round não está em SETUP. Não pode preencher heats.");
            return true;
        }

        // -----------------------------------------------------
        // Buscar jogadores
        // -----------------------------------------------------
        List<UUID> players;

        switch (source) {
            case "all":
                players = new ArrayList<>();
                players.addAll(eventManager.getSubscribers(event));
                players.addAll(eventManager.getReserves(event));
                break;

            case "signed":
                players = new ArrayList<>(eventManager.getSubscribers(event));
                break;

            case "reserves":
                players = new ArrayList<>(eventManager.getReserves(event));
                break;

            default:
                player.sendMessage("§cTipo inválido. Use: all / signed / reserves");
                return true;
        }

        if (players.isEmpty()) {
            player.sendMessage("§cNenhum jogador disponível para preencher heats.");
            return true;
        }

        // -----------------------------------------------------
        // Ordenação / Randomização
        // -----------------------------------------------------
        if (mode.equals("sorted")) {

            players.sort((u1, u2) -> {

                // Buscar nomes
                String name1 = Bukkit.getOfflinePlayer(u1).getName();
                String name2 = Bukkit.getOfflinePlayer(u2).getName();

                if (name1 == null) name1 = "|||" + u1.toString(); // fallback
                if (name2 == null) name2 = "|||" + u2.toString();

                // Buscar tempos
                Object[] r1 = database.getPlayerBestTime(name1, trackName);
                Object[] r2 = database.getPlayerBestTime(name2, trackName);

                Double t1 = (r1 != null ? (Double) r1[0] : null);
                Double t2 = (r2 != null ? (Double) r2[0] : null);

                boolean h1 = t1 != null;
                boolean h2 = t2 != null;

                // Ambos têm tempo → comparar
                if (h1 && h2) return Double.compare(t1, t2);

                // Quem tem tempo vem primeiro
                if (h1) return -1;
                if (h2) return 1;

                // Sem tempo → comparar por nome
                return name1.compareToIgnoreCase(name2);
            });

        } else if (mode.equals("random")) {

            Collections.shuffle(players);

        } else {
            player.sendMessage("§cModo inválido. Use: sorted / random");
            return true;
        }

        // -----------------------------------------------------
        // Pegar quantidade de heats do round
        // -----------------------------------------------------
        int heatCount = eventManager.getHeatCountForRound(roundId);

        if (heatCount <= 0) {
            player.sendMessage("§cEsse round não tem heats criados ainda.");
            return true;
        }

        // -----------------------------------------------------
        // Criar os N heats
        // -----------------------------------------------------
        List<List<UUID>> heats = new ArrayList<>();
        for (int i = 0; i < heatCount; i++) {
            heats.add(new ArrayList<>());
        }

        // Distribuição balanceada
        int index = 0;
        for (UUID uuid : players) {
            heats.get(index).add(uuid);
            index = (index + 1) % heatCount;
        }

        // -----------------------------------------------------
        // Salvar no banco usando fillHeat()
        // -----------------------------------------------------
        for (int i = 0; i < heats.size(); i++) {

            int heatId = eventManager.getHeatIdByRoundAndIndex(roundId, i + 1);

            boolean ok = eventManager.fillHeat(heatId, heats.get(i));

            if (!ok) {
                player.sendMessage("§cErro ao salvar o Heat " + (i + 1));
                return true;
            }
        }

        // -----------------------------------------------------
        // Feedback
        // -----------------------------------------------------
        player.sendMessage("§aHeats preenchidos com sucesso!");
        player.sendMessage("§7Modo: §f" + mode.toUpperCase());
        player.sendMessage("§7Jogadores usados: §f" + source.toUpperCase());

        return true;
    }





    // ========================================================================
    // /round info <roundName>
    // ========================================================================
    private boolean handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUso correto: /round info <roundName>");
            return true;
        }

        String name = args[1];
        // TODO lógica
        player.sendMessage("§bInformações do round: §f" + name);
        return true;
    }

    // ========================================================================
    // /round list
    // ========================================================================
    private boolean handleList(Player player, String[] args) {
        // TODO: puxar do banco a lista real
        player.sendMessage("§aRounds registrados:");
        player.sendMessage("§7- (Exemplo) Round 1");
        player.sendMessage("§7- (Exemplo) Round 2");
        return true;
    }

    // ========================================================================
    // /round results <roundName>
    // ========================================================================
    private boolean handleResults(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUso correto: /round results <roundName>");
            return true;
        }

        String name = args[1];
        // TODO lógica
        player.sendMessage("§dResultados do round: §f" + name);
        return true;
    }

    private boolean handleDelete(Player player, String[] args) {

        if (args.length < 2) {
            player.sendMessage("§cUso correto: /round delete R<roundIndex>-<roundName>");
            return true;
        }

        String input = args[1];

        // ============================
        // VALIDAÇÃO DO FORMATO
        // ============================
        if (!input.contains("-") || !input.startsWith("R")) {
            player.sendMessage("§cFormato inválido! Use: §fR1-Qualy");
            return true;
        }

        String[] split = input.split("-", 2);
        String left = split[0];   // R1
        String roundName = split[1]; // Qualy

        // Extrair número após o R
        String numberStr = left.substring(1); // remove o "R"

        int roundIndex;

        try {
            roundIndex = Integer.parseInt(numberStr);
        } catch (NumberFormatException e) {
            player.sendMessage("§cNúmero de round inválido: §f" + numberStr);
            return true;
        }

        // ============================
        // PEGAR O EVENTO ATUAL DO PLAYER
        // ============================
        Integer eventId = eventManager.getEventIDByName(eventManager.getSelectedEvent(player.getUniqueId()));

        if (eventId == null) {
            player.sendMessage("§c/event select <event> Você não tem um evento selecionado");
            return true;
        }

        // ============================
        // BUSCAR O ROUND NO BANCO
        // ============================
        Integer roundId = eventManager.getRoundIdByEventAndNumber(eventId, roundIndex);

        if (roundId == null) {
            player.sendMessage("§cRound R" + roundIndex + " não existe no evento atual.");
            return true;
        }

        // ============================
        // DELETAR ROUND
        // ============================
        boolean deleted = eventManager.deleteRound(roundId);

        if (!deleted) {
            player.sendMessage("§cFalha ao deletar o round no banco.");
            return true;
        }

        player.sendMessage("§aRound deletado com sucesso: §fR" + roundIndex + "-" + roundName);
        return true;
    }
*/
}
