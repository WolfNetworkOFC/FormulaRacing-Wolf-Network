package dev.EfraGroup.formulaRacing.Cosmetics;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BoatTrailManager {

    private final JavaPlugin plugin;
    private final Map<UUID, BlockDisplay> trails = new HashMap<>();

    public BoatTrailManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

}
