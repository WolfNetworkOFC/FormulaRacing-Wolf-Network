 /*
  * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
  *
  * Could not load the following classes:
  *  org.bukkit.configuration.file.FileConfiguration
  */
 package dev.EfraGroup.formulaRacing.Utils;

 import dev.EfraGroup.formulaRacing.FileManager;
 import dev.EfraGroup.formulaRacing.FormulaRacing;
 import org.bukkit.configuration.file.FileConfiguration;

 public class DebugManager {
     private final FormulaRacing plugin;
     private final FileManager fileManager;
     private boolean regionDetection;
     private boolean duelSystem;
     private boolean duelSystemVerbose;
     private boolean timeTrialSystem;
     private boolean boatUtils;
     private boolean databaseOperations;
     private boolean eventSystem;
     private boolean packetHandling;
     private boolean performanceMetrics;
     private boolean raceSystem;
     private boolean raceSystemVerbose;
     private boolean qualificationSystem;
     private boolean pitStopSystem;
     private boolean spectatorSystem;
     private boolean guiSystem;
     private boolean fileSystem;

     public DebugManager(FormulaRacing plugin, FileManager fileManager) {
         this.plugin = plugin;
         this.fileManager = fileManager;
         this.reload();
     }

     public void reload() {
         FileConfiguration config = this.fileManager.getConfig();
         this.regionDetection = config.getBoolean("debug.region-detection", false);
         this.duelSystem = config.getBoolean("debug.duel-system", true);
         this.duelSystemVerbose = config.getBoolean("debug.duel-system-verbose", false);
         this.timeTrialSystem = config.getBoolean("debug.time-trial-system", false);
         this.boatUtils = config.getBoolean("debug.boat-utils", false);
         this.databaseOperations = config.getBoolean("debug.database-operations", false);
         this.eventSystem = config.getBoolean("debug.event-system", false);
         this.packetHandling = config.getBoolean("debug.packet-handling", false);
         this.performanceMetrics = config.getBoolean("debug.performance-metrics", false);
         this.raceSystem = config.getBoolean("debug.race-system", false);
         this.raceSystemVerbose = config.getBoolean("debug.race-system-verbose", false);
         this.qualificationSystem = config.getBoolean("debug.qualification-system", false);
         this.pitStopSystem = config.getBoolean("debug.pit-stop-system", false);
         this.spectatorSystem = config.getBoolean("debug.spectator-system", false);
         this.guiSystem = config.getBoolean("debug.gui-system", false);
         this.fileSystem = config.getBoolean("debug.file-system", false);
         this.plugin.getLogger().info("\u00a7a[DebugManager] Configura\u00e7\u00f5es de debug recarregadas:");
         this.plugin.getLogger().info("  \u00a77- Region Detection: " + (this.regionDetection ? "\u00a7aON" : "\u00a7cOFF"));
         this.plugin.getLogger().info("  \u00a77- Duel System: " + (this.duelSystem ? "\u00a7aON" : "\u00a7cOFF"));
         this.plugin.getLogger().info("  \u00a77- Duel System Verbose: " + (this.duelSystemVerbose ? "\u00a7aON" : "\u00a7cOFF"));
         this.plugin.getLogger().info("  \u00a77- Time Trial System: " + (this.timeTrialSystem ? "\u00a7aON" : "\u00a7cOFF"));
         this.plugin.getLogger().info("  \u00a77- Boat Utils: " + (this.boatUtils ? "\u00a7aON" : "\u00a7cOFF"));
         this.plugin.getLogger().info("  \u00a77- Database Operations: " + (this.databaseOperations ? "\u00a7aON" : "\u00a7cOFF"));
         this.plugin.getLogger().info("  \u00a77- Event System: " + (this.eventSystem ? "\u00a7aON" : "\u00a7cOFF"));
         this.plugin.getLogger().info("  \u00a77- Packet Handling: " + (this.packetHandling ? "\u00a7aON" : "\u00a7cOFF"));
         this.plugin.getLogger().info("  \u00a77- Performance Metrics: " + (this.performanceMetrics ? "\u00a7aON" : "\u00a7cOFF"));
         this.plugin.getLogger().info("  \u00a77- Race System: " + (this.raceSystem ? "\u00a7aON" : "\u00a7cOFF"));
         this.plugin.getLogger().info("  \u00a77- Race System Verbose: " + (this.raceSystemVerbose ? "\u00a7aON" : "\u00a7cOFF"));
         this.plugin.getLogger().info("  \u00a77- Qualification System: " + (this.qualificationSystem ? "\u00a7aON" : "\u00a7cOFF"));
         this.plugin.getLogger().info("  \u00a77- Pit Stop System: " + (this.pitStopSystem ? "\u00a7aON" : "\u00a7cOFF"));
         this.plugin.getLogger().info("  \u00a77- Spectator System: " + (this.spectatorSystem ? "\u00a7aON" : "\u00a7cOFF"));
         this.plugin.getLogger().info("  \u00a77- GUI System: " + (this.guiSystem ? "\u00a7aON" : "\u00a7cOFF"));
         this.plugin.getLogger().info("  \u00a77- File System: " + (this.fileSystem ? "\u00a7aON" : "\u00a7cOFF"));
     }

     public boolean isRegionDetectionEnabled() {
         return this.regionDetection;
     }

     public boolean isDuelSystemEnabled() {
         return this.duelSystem;
     }

     public boolean isDuelSystemVerboseEnabled() {
         return this.duelSystemVerbose;
     }

     public boolean isTimeTrialSystemEnabled() {
         return this.timeTrialSystem;
     }

     public boolean isBoatUtilsEnabled() {
         return this.boatUtils;
     }

     public boolean isDatabaseOperationsEnabled() {
         return this.databaseOperations;
     }

     public boolean isEventSystemEnabled() {
         return this.eventSystem;
     }

     public boolean isPacketHandlingEnabled() {
         return this.packetHandling;
     }

     public boolean isPerformanceMetricsEnabled() {
         return this.performanceMetrics;
     }

     public boolean isRaceSystemEnabled() {
         return this.raceSystem;
     }

     public boolean isRaceSystemVerboseEnabled() {
         return this.raceSystemVerbose;
     }

     public boolean isQualificationSystemEnabled() {
         return this.qualificationSystem;
     }

     public boolean isPitStopSystemEnabled() {
         return this.pitStopSystem;
     }

     public boolean isSpectatorSystemEnabled() {
         return this.spectatorSystem;
     }

     public void logRegionDetection(String message) {
         if (this.regionDetection) {
             this.plugin.getLogger().info("\u00a7b[REGION DEBUG] " + message);
         }
     }

     public void logDuelSystem(String message) {
         if (this.duelSystem) {
             this.plugin.getLogger().info("\u00a7d[DUEL DEBUG] " + message);
         }
     }

     public void logDuelSystemVerbose(String message) {
         if (this.duelSystemVerbose) {
             this.plugin.getLogger().info("\u00a7d[DUEL DEBUG VERBOSE] " + message);
         }
     }

     public void logTimeTrialSystem(String message) {
         if (this.timeTrialSystem) {
             this.plugin.getLogger().info("\u00a7e[TIME TRIAL DEBUG] " + message);
         }
     }

     public void logBoatUtils(String message) {
         if (this.boatUtils) {
             this.plugin.getLogger().info("\u00a73[BOAT DEBUG] " + message);
         }
     }

     public void logDatabaseOperation(String message) {
         if (this.databaseOperations) {
             this.plugin.getLogger().info("\u00a76[DATABASE DEBUG] " + message);
         }
     }

     public void logDatabaseOperations(String message) {
         this.logDatabaseOperation(message);
     }

     public void logEventSystem(String message) {
         if (this.eventSystem) {
             this.plugin.getLogger().info("\u00a75[EVENT DEBUG] " + message);
         }
     }

     public void logPacketHandling(String message) {
         if (this.packetHandling) {
             this.plugin.getLogger().info("\u00a79[PACKET DEBUG] " + message);
         }
     }

     public void logPerformanceMetric(String operation, long durationMs) {
         if (this.performanceMetrics) {
             String color = durationMs < 10L ? "\u00a7a" : (durationMs < 50L ? "\u00a7e" : "\u00a7c");
             this.plugin.getLogger().info("\u00a7f[PERFORMANCE] " + operation + ": " + color + durationMs + "ms");
         }
     }

     public void measurePerformance(String operation, Runnable task) {
         if (this.performanceMetrics) {
             long start = System.currentTimeMillis();
             task.run();
             long duration = System.currentTimeMillis() - start;
             this.logPerformanceMetric(operation, duration);
         } else {
             task.run();
         }
     }

     public void logRaceSystem(String message) {
         if (this.raceSystem) {
             this.plugin.getLogger().info("\u00a7c[RACE DEBUG] " + message);
         }
     }

     public void logRaceSystemVerbose(String message) {
         if (this.raceSystemVerbose) {
             this.plugin.getLogger().info("\u00a7c[RACE DEBUG VERBOSE] " + message);
         }
     }

     public void logQualificationSystem(String message) {
         if (this.qualificationSystem) {
             this.plugin.getLogger().info("\u00a76[QUALIFICATION DEBUG] " + message);
         }
     }

     public void logPitStopSystem(String message) {
         if (this.pitStopSystem) {
             this.plugin.getLogger().info("\u00a74[PIT STOP DEBUG] " + message);
         }
     }

     public void logSpectatorSystem(String message) {
         if (this.spectatorSystem) {
             this.plugin.getLogger().info("\u00a7b[SPECTATOR DEBUG] " + message);
         }
     }

     public void logGuiSystem(String message) {
         if (this.guiSystem) {
             this.plugin.getLogger().info("\u00a7a[GUI DEBUG] " + message);
         }
     }

     public void logFileSystem(String message) {
         if (this.fileSystem) {
             this.plugin.getLogger().info("\u00a77[FILE DEBUG] " + message);
         }
     }
 }