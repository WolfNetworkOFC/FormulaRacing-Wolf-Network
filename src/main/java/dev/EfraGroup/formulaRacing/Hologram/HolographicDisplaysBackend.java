package dev.EfraGroup.formulaRacing.Hologram;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

/**
 * Hologram implementation backed by the HolographicDisplays plugin (filoghost API 3.x).
 * Uses reflection so the build does not depend on resolving the HD artifact (it is a
 * runtime-provided plugin). Opt-in via config: holograms.backend: holographicdisplays
 *
 * NOTE: HolographicDisplays is NOT Folia-compatible; the HologramManager only selects
 * this backend on non-Folia servers.
 */
public class HolographicDisplaysBackend implements HologramBackend {

    private final String name;
    private final Location location;
    private Object hologram; // com.gmail.filoghost.holographicdisplays.api.Hologram

    public HolographicDisplaysBackend(String name, Location location) {
        this.name = name;
        this.location = location;
        try {
            Plugin fr = Bukkit.getPluginManager().getPlugin("FormulaRacing");
            Class<?> apiClass = Class.forName("com.gmail.filoghost.holographicdisplays.api.HolographicDisplaysAPI");
            Method getApi = apiClass.getMethod("getHolographicDisplaysAPI", Plugin.class);
            Object api = getApi.invoke(null, fr);
            Method create = apiClass.getMethod("createHologram", Location.class);
            this.hologram = create.invoke(api, location);
        } catch (Throwable t) {
            this.hologram = null;
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Location getLocation() {
        return location;
    }

    @Override
    public void create(String name, Location loc, List<String> lines) {
        if (hologram == null) return;
        updateLines(lines);
    }

    @Override
    public void updateLines(List<String> lines) {
        if (hologram == null) return;
        try {
            Method clear = hologram.getClass().getMethod("clearLines");
            clear.invoke(hologram);
            Method append = hologram.getClass().getMethod("appendTextLine", String.class);
            for (String line : lines) {
                append.invoke(hologram, stripColor(line));
            }
        } catch (Throwable t) {
            // best-effort: ignore reflection failures at runtime
        }
    }

    @Override
    public void remove() {
        if (hologram != null) {
            try {
                Method delete = hologram.getClass().getMethod("delete");
                delete.invoke(hologram);
            } catch (Throwable ignored) {
            }
            hologram = null;
        }
    }

    private static String stripColor(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)§[0-9a-fk-or]", "").replaceAll("&[0-9a-fk-or]", "");
    }

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("HolographicDisplays") != null;
    }

    static {
        // Touch the locale class so shaded relocations don't strip it unexpectedly.
        Locale.getDefault();
    }
}
