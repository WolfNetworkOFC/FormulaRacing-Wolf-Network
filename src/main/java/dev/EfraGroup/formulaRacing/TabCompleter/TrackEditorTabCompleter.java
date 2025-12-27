package dev.EfraGroup.formulaRacing.TabCompleter;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TrackEditorTabCompleter implements TabCompleter {

    private final DatabaseManager mysql;

    public TrackEditorTabCompleter(DatabaseManager mysql) {
        this.mysql = mysql;
    }

    // ==============================================================
    // MAIN TAB ROUTER
    // ==============================================================

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, Command command, @NotNull String alias, String[] args) {

        if (!command.getName().equalsIgnoreCase("trackedit"))
            return List.of();

        if (args.length == 1)
            return matchPrefix(MAIN_COMMANDS, args[0]);

        String sub = args[0].toLowerCase();

        CommandHandler handler = ROUTES.get(sub);
        if (handler != null)
            return handler.handle(args);

        return List.of();
    }

    // ==============================================================
    // ROUTING
    // ==============================================================

    private interface CommandHandler {
        List<String> handle(String[] args);
    }

    private static final List<String> MAIN_COMMANDS = List.of(
            "boatutils", "create", "delete", "region", "select", "location",
            "icon", "open", "close", "spawn", "grid", "time", "checkpoint",
            "resetalltimes", "cam", "broadcast", "setowner"
    );

    private final Map<String, CommandHandler> ROUTES = Map.ofEntries(
            Map.entry("boatutils", this::boatUtils),
            Map.entry("select", this::trackNameOnly),
            Map.entry("delete", this::trackNameOnly),
            Map.entry("resetalltimes", this::trackNameOnly),
            Map.entry("region", this::region),
            Map.entry("location", this::location),
            Map.entry("icon", this::icon),
            Map.entry("grid", this::grid),
            Map.entry("checkpoint", this::grid),
            Map.entry("time", this::time),
            Map.entry("cam", this::cam),
            Map.entry("broadcast", this::broadcast),
            Map.entry("setowner", this::setOwner)
    );

    // ==============================================================
    // BOATUTILS
    // ==============================================================

    private List<String> boatUtils(String[] args) {

        if (args.length == 2)
            return List.of("config", "set");

        // /trackedit boatutils set ...
        if (args[1].equalsIgnoreCase("set"))
            return boatUtilsSet(args);

        // /trackedit boatutils config ...
        if (args[1].equalsIgnoreCase("config"))
            return boatUtilsConfig(args);

        return List.of();
    }

    // ==============================================================
    // boatutils set
    // ==============================================================

    private List<String> boatUtilsSet(String[] args) {

        if (args.length == 3)
            return List.of("group");

        if (args[2].equalsIgnoreCase("group")) {

            if (args.length == 4)
                return getGroupModes(args[3]);

            if (args.length == 5)
                return matchTracks(args[4]);
        }

        return List.of();
    }

    // Auto-puxa os modos do enum BoatUtilsGroupMode
    private List<String> getGroupModes(String prefix) {
        return Arrays.stream(BoatUtilsGroupMode.values())
                .map(Enum::name)
                .filter(s -> s.startsWith(prefix.toUpperCase()))
                .toList();
    }

    // ==============================================================
    // boatutils config
    // ==============================================================

    private List<String> boatUtilsConfig(String[] args) {

        if (args.length == 3)
            return matchPrefix(List.of(
                    "stepheight",
                    "customslipperiness",
                    "aircontrol"
            ), args[2]);

        String option = args[2].toLowerCase();

        return switch (option) {
            case "stepheight" -> tabStepHeight(args);
            case "customslipperiness" -> tabCustomSlipperiness(args);
            case "aircontrol" -> tabAirControl(args);
            default -> List.of();
        };
    }

    // ----------------------------------------
    private List<String> tabStepHeight(String[] args) {
        return switch (args.length) {
            case 4 -> List.of("set");
            case 5 -> List.of("<valor>");
            case 6 -> matchTracks(args[5]);
            default -> List.of();
        };
    }

    // ----------------------------------------
    private List<String> tabCustomSlipperiness(String[] args) {

        if (args.length == 4)
            return matchPrefix(List.of("add", "reset"), args[3]);

        // ADD
        if (args[3].equalsIgnoreCase("add")) {
            return switch (args.length) {
                case 5 -> getAllBlocks(args[4]); // bloco
                case 6 -> List.of("<valor>");    // valor
                case 7 -> matchTracks(args[6]);  // pista
                default -> List.of();
            };
        }

        // RESET
        if (args[3].equalsIgnoreCase("reset")) {
            return args.length == 5 ? matchTracks(args[4]) : List.of();
        }

        return List.of();
    }

    // ----------------------------------------
    private List<String> tabAirControl(String[] args) {
        return switch (args.length) {
            case 4 -> List.of("set");
            case 5 -> matchPrefix(List.of("true", "false"), args[4]);
            case 6 -> matchTracks(args[5]);
            default -> List.of();
        };
    }

    // ==============================================================
    // SELECT / DELETE / RESETALLTIMES — somente nome de pista
    // ==============================================================

    private List<String> trackNameOnly(String[] args) {
        return args.length == 2 ? matchTracks(args[1]) : List.of();
    }

    // ==============================================================
    // REGION
    // ==============================================================

    private List<String> region(String[] args) {
        return args.length == 2 ? List.of("start", "end") : List.of();
    }

    // ==============================================================
    // LOCATION
    // ==============================================================

    private List<String> location(String[] args) {

        if (args.length == 2)
            return List.of("leaderboard");

        if (args.length == 3 && args[1].equalsIgnoreCase("leaderboard"))
            return List.of("set");

        return List.of();
    }

    // ==============================================================
    // ICON
    // ==============================================================

    private List<String> icon(String[] args) {
        return args.length == 2 ? getAllBlocks(args[1]) : List.of();
    }

    // ==============================================================
    // GRID / CHECKPOINT
    // ==============================================================

    private List<String> grid(String[] args) {
        return args.length == 2 ? List.of("add", "remove") : List.of();
    }

    // ==============================================================
    // TIME
    // ==============================================================

    private List<String> time(String[] args) {
        return args.length == 2 ? List.of("<ticks>") : List.of();
    }

    // ==============================================================
    // CAM
    // ==============================================================

    private List<String> cam(String[] args) {

        if (args.length == 2)
            return List.of("edit", "set");

        if (args.length == 3 && args[1].equalsIgnoreCase("set"))
            return List.of("<id>");

        if (args.length == 4 && args[1].equalsIgnoreCase("set"))
            return matchTracks(args[3]);

        return List.of();
    }

    // ==============================================================
    // BROADCAST
    // ==============================================================

    private List<String> broadcast(String[] args) {
        return args.length == 2 ? matchTracks(args[1]) : List.of();
    }

    // ==============================================================
    // SETOWNER
    // ==============================================================

    private List<String> setOwner(String[] args) {
        return args.length == 2 ? matchTracks(args[1]) : List.of();
    }

    // ==============================================================
    // HELPERS
    // ==============================================================

    private List<String> matchPrefix(List<String> list, String prefix) {
        return list.stream()
                .filter(s -> s.startsWith(prefix.toLowerCase()))
                .toList();
    }

    private List<String> matchTracks(String start) {
        return mysql.getAllTracks().stream()
                .filter(s -> s.toLowerCase().startsWith(start.toLowerCase()))
                .toList();
    }

    private List<String> getAllBlocks(String start) {
        return Arrays.stream(Material.values())
                .map(Enum::name)
                .filter(n -> n.startsWith(start.toUpperCase()))
                .toList();
    }


    public enum BoatUtilsGroupMode {

        RALLY(8, 0.98f, true, false, 1.25f),
        RALLY_BLUE(9, 0.989f, true, false, 1.25f),

        BA_NOFD(10, 0.98f, true, false, 1.25f, true),
        PARKOUR(11, 0.98f, true, false, 0.5f, 0.36f),

        BA_BLUE_NOFD(12, 0.989f, true, false, 1.25f, true),
        PARKOUR_BLUE(13, 0.989f, true, false, 0.5f, 0.36f),

        BA(14, 0.98f, true, true, 1.25f),
        BA_BLUE(15, 0.989f, true, true, 1.25f),

        // -------- BROKEN SLIME (deprecated fix) --------
        BROKEN_SLIME_RALLY(0, 0.98f, true, false, 1.25f),
        BROKEN_SLIME_RALLY_BLUE(1, 0.989f, true, false, 1.25f),
        BROKEN_SLIME_BA_NOFD(2, 0.98f, true, false, 1.25f, true),
        BROKEN_SLIME_PARKOUR(3, 0.98f, true, false, 0.5f, 0.36f),
        BROKEN_SLIME_BA_BLUE_NOFD(4, 0.989f, true, false, 1.25f, true),
        BROKEN_SLIME_PARKOUR_BLUE(5, 0.989f, true, false, 0.5f, 0.36f),
        BROKEN_SLIME_BA(6, 0.98f, true, true, 1.25f),
        BROKEN_SLIME_BA_BLUE(7, 0.989f, true, true, 1.25f);

        public final int id;
        public final float slipperiness;
        public final boolean airControl;
        public final boolean waterElevation;
        public final float stepHeight;
        public final Float jumpForce; // null = usar padrão
        public final boolean noFallDamage;

        BoatUtilsGroupMode(int id, float slip, boolean air, boolean water, float step) {
            this(id, slip, air, water, step, false, null);
        }

        BoatUtilsGroupMode(int id, float slip, boolean air, boolean water, float step, boolean noFallDamage) {
            this(id, slip, air, water, step, noFallDamage, null);
        }

        BoatUtilsGroupMode(int id, float slip, boolean air, boolean water, float step, Float jumpForce) {
            this(id, slip, air, water, step, false, jumpForce);
        }

        BoatUtilsGroupMode(int id, float slip, boolean air, boolean water, float step, boolean noFallDamage, Float jumpForce) {
            this.id = id;
            this.slipperiness = slip;
            this.airControl = air;
            this.waterElevation = water;
            this.stepHeight = step;
            this.noFallDamage = noFallDamage;
            this.jumpForce = jumpForce;
        }
    }



}
