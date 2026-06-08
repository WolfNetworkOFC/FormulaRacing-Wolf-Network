 /*
  * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
  *
  * Could not load the following classes:
  *  dev.EfraGroup.formulaRacing.Database.DatabaseManager
  *  net.md_5.bungee.api.ChatMessageType
  *  net.md_5.bungee.api.chat.BaseComponent
  *  net.md_5.bungee.api.chat.TextComponent
  *  org.bukkit.Bukkit
  *  org.bukkit.Color
  *  org.bukkit.Particle
  *  org.bukkit.Particle$DustOptions
  *  org.bukkit.Sound
  *  org.bukkit.entity.Player
  *  org.bukkit.plugin.Plugin
  *  org.bukkit.scheduler.BukkitRunnable
  */
 package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Duels.TimeTrialDuels;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import org.bukkit.World;

import java.util.*;
 import java.util.concurrent.ConcurrentHashMap;
 import net.md_5.bungee.api.ChatMessageType;
 import net.md_5.bungee.api.chat.BaseComponent;
 import net.md_5.bungee.api.chat.TextComponent;
  import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
  import org.bukkit.Bukkit;
  import org.bukkit.Color;
  import org.bukkit.Particle;
  import org.bukkit.Sound;
  import org.bukkit.entity.Player;

 public class TimeTrialDuelsAction {
     private final FormulaRacing plugin;
     private final DatabaseManager dm;
     private TimeTrialDuels timeTrialDuels;
     private final Map<UUID, DuelSession> activeTimers = new ConcurrentHashMap<UUID, DuelSession>();
     private final Map<UUID, Integer> activeVisuals = new ConcurrentHashMap<UUID, Integer>();
     private static final String ICON_TIMER = "\u00a7b\u00a7l\u231a";
     private static final String ICON_RECORD = "\u00a76\u00a7l\u272a";
     private static final String BRACKET = "\u00a78\u00a7l\u00bb";

     public TimeTrialDuelsAction(FormulaRacing plugin, DatabaseManager dm) {
         this.plugin = plugin;
         this.dm = dm;
         this.startGlobalUpdateTask();
     }

     public void setTimeTrialDuels(TimeTrialDuels timeTrialDuels) {
         this.timeTrialDuels = timeTrialDuels;
     }

     public void toggleVisuals(Player player, int duelId, boolean active) {
         UUID uuid = player.getUniqueId();
         if (active) {
             this.activeVisuals.put(uuid, duelId);
         } else {
             this.activeVisuals.remove(uuid);
             this.activeTimers.remove(uuid);
             player.spigot().sendMessage(ChatMessageType.ACTION_BAR, (BaseComponent)new TextComponent(""));
         }
     }

     public void toggleTimer(Player player, int duelId, boolean active) {
         UUID uuid = player.getUniqueId();
         if (active) {
             if (this.activeVisuals.containsKey(uuid)) {
                 this.activeTimers.put(uuid, new DuelSession(uuid, duelId));
                 player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
             }
         } else {
             this.activeTimers.remove(uuid);
         }
     }

     public void resetLapTimer(Player player) {
         DuelSession session = this.activeTimers.get(player.getUniqueId());
         if (session != null) {
             session.resetLapTimer();
         }
     }

     public void setWaitingForOthers(Player player, boolean waiting) {
         UUID uuid = player.getUniqueId();
         DuelSession session = this.activeTimers.get(uuid);
         if (session != null) {
             session.setWaitingForOthers(waiting);
         }
     }

     public void pauseLapTimer(Player player) {
         DuelSession session = this.activeTimers.get(player.getUniqueId());
         if (session != null) {
             session.pauseLapTimer();
         }
     }

     public void resumeLapTimer(Player player) {
         DuelSession session = this.activeTimers.get(player.getUniqueId());
         if (session != null) {
             session.resumeLapTimer();
         }
     }

     public void updateBestLapTime(Player player, double lapTime) {
         DuelSession session = this.activeTimers.get(player.getUniqueId());
         if (session != null) {
             boolean shouldSaveCheckpoints;
             Double currentBest = session.getBestLapTime();
             Map<Integer, Double> bestLapCheckpoints = session.getBestLapCheckpointTimes();
             Map currentCheckpoints = this.dm.getDuelCheckpointTimes(player.getUniqueId(), session.getDuelId());
             Double bestTimeWithCheckpoints = null;
             if (bestLapCheckpoints != null && !bestLapCheckpoints.isEmpty()) {
                 bestTimeWithCheckpoints = currentBest;
             }
             boolean isNewRecord = currentBest == null || lapTime < currentBest;
             boolean bl = shouldSaveCheckpoints = bestTimeWithCheckpoints == null || lapTime < bestTimeWithCheckpoints;
             if (isNewRecord) {
                 session.setBestLapTime(lapTime);
                 session.setPersonalBest(this.formatTime(lapTime));
                 this.plugin.getDebugManager().logDuelSystem(player.getName() + " - PB atualizado no HUD: " + this.formatTime(lapTime));
             }
             if (shouldSaveCheckpoints && !currentCheckpoints.isEmpty()) {
                 session.setBestLapCheckpointTimes(currentCheckpoints);
                 if (isNewRecord) {
                     this.plugin.getDebugManager().logDuelSystem(player.getName() + " estabeleceu novo melhor tempo com " + currentCheckpoints.size() + " checkpoints salvos para compara\u00e7\u00e3o de delta");
                 } else {
                     this.plugin.getDebugManager().logDuelSystem(player.getName() + " n\u00e3o bateu recorde, mas salvou checkpoints melhores (" + currentCheckpoints.size() + " CPs) para delta - Tempo: " + this.formatTime(lapTime) + " vs PB: " + this.formatTime(currentBest));
                 }
             }
         }
     }

     public void stopAll(Player player) {
         UUID uuid = player.getUniqueId();
         this.activeTimers.remove(uuid);
         this.activeVisuals.remove(uuid);
         player.spigot().sendMessage(ChatMessageType.ACTION_BAR, (BaseComponent)new TextComponent(""));
     }

     public double getPlayerElapsedSeconds(Player player) {
         DuelSession session = this.activeTimers.get(player.getUniqueId());
         if (session == null) {
             return 0.0;
         }
         return (double)session.getCurrentTimeMillis() / 1000.0;
     }

public double getPlayerLapElapsedSeconds(Player player) {
          DuelSession session = this.activeTimers.get(player.getUniqueId());
          if (session == null) {
              return 0.0;
          }
          return session.getCurrentLapTime();
      }

      private void startGlobalUpdateTask() {
          World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
          if (world == null) {
              SchedulerHelper.runTaskTimer(this.plugin, () -> {
                  this.updateDuelVisuals();
              }, 1L, 20L);
          } else {
              SchedulerHelper.runTaskTimerAt(this.plugin, world, 0, 0, task -> {
                  this.updateDuelVisuals();
              }, 1L, 20L);
          }
      }
      
      private void updateDuelVisuals() {
           TimeTrialDuelsAction.this.activeVisuals.forEach((uuid, duelId) -> {
               Player player = Bukkit.getPlayer((UUID)uuid);
               if (player == null || !player.isOnline()) {
                   TimeTrialDuelsAction.this.activeVisuals.remove(uuid);
                   TimeTrialDuelsAction.this.activeTimers.remove(uuid);
                   return;
               }
               if (TimeTrialDuelsAction.this.isPlayerInActiveHeatRace((UUID)uuid)) {
                   return;
               }
               DuelSession session = TimeTrialDuelsAction.this.activeTimers.get(uuid);
               if (session != null) {
                   if (session.shouldUpdateData()) {
                       TimeTrialDuelsAction.this.updateDataAsync(player, session);
                   }
                   if (session.getCachedPosition().contains("1\u00ba") || session.getCachedPosition().contains("1st")) {
                       TimeTrialDuelsAction.this.spawnLeaderParticles(player);
                   }
                   if (session.isWaitingForOthers()) {
                       String langCode = TimeTrialDuelsAction.this.dm.getPlayerLanguage(uuid);
                       String waitingMsg = TimeTrialDuelsAction.this.plugin.getDirectTranslation("duel_waiting_others", langCode);
                       if (waitingMsg == null || waitingMsg.isEmpty()) {
                           waitingMsg = "Aguardando outros jogadores...";
                          }
                          TimeTrialDuelsAction.this.sendWaitingForOthersActionBar(player, session.getFormattedLapTime(), waitingMsg);
                      } else {
                          TimeTrialDuelsAction.this.sendDuelActionBar(player, session.getCachedPosition(), session.getFormattedLapTime(), session.getPersonalBest(), session.getCachedDelta());
                      }
                  } else {
                      String langCode = TimeTrialDuelsAction.this.dm.getPlayerLanguage(uuid);
                      String waitingText = TimeTrialDuelsAction.this.plugin.getDirectTranslation("duel_waiting", langCode);
                      TimeTrialDuelsAction.this.sendDuelActionBar(player, "\u00a7f\u00a7l" + waitingText, "00:00.000", "\u00a77--:--.---", "");
                  }
              });
}
              
      private boolean isPlayerInActiveHeatRace(UUID playerUUID) {
         if (this.plugin.getRaceEventManager() == null) {
             return false;
         }
         for (Events event : this.plugin.getRaceEventManager().getAllEvents()) {
             for (Rounds round : event.getEventSchedule().getRounds().values()) {
                 Driver driver;
                 Heats heat;
                 HeatState heatState;
                 Optional<Heats> activeHeatOpt = round.getActiveHeat();
                 if (activeHeatOpt.isEmpty() || (heatState = (heat = activeHeatOpt.get()).getHeatState()) != HeatState.RACING && heatState != HeatState.STARTING && heatState != HeatState.PRACTICE || (driver = heat.getDriver(playerUUID)) == null) continue;
                 return true;
             }
         }
         return false;
     }

     private void sendDuelActionBar(Player player, String position, String time, String pb, String delta) {
         String langCode = this.dm.getPlayerLanguage(player.getUniqueId());
         String pbLabel = this.plugin.getDirectTranslation("duel_pb_label", langCode);
         String message = String.format("%s %s %s \u00a7f%s%s %s \u00a78| %s \u00a7e%s: \u00a77%s", BRACKET, position, BRACKET, time, delta, ICON_TIMER, ICON_RECORD, pbLabel, pb);
         player.spigot().sendMessage(ChatMessageType.ACTION_BAR, (BaseComponent)new TextComponent(message));
     }

     private void sendWaitingForOthersActionBar(Player player, String finalTime, String waitingMessage) {
         String message = String.format("\u00a7a\u00a7l\u2713 %s \u00a7f%s %s \u00a78| \u00a7e\u00a7l\u23f3 \u00a7f%s", ICON_TIMER, finalTime, BRACKET, waitingMessage);
         player.spigot().sendMessage(ChatMessageType.ACTION_BAR, (BaseComponent)new TextComponent(message));
     }

      private void updateDataAsync(Player player, DuelSession session) {
          SchedulerHelper.runAsync(this.plugin, () -> {
             Double bestLap;
             String langCode = this.dm.getPlayerLanguage(player.getUniqueId());
             session.setLangCode(langCode);
             if (session.getBestLapTime() == null) {
                 bestLap = this.dm.getPlayerBestLapTimeInDuel(player.getUniqueId(), session.getDuelId());
                 session.setBestLapTime(bestLap);
             }
             if ((bestLap = session.getBestLapTime()) == null || bestLap <= 0.0) {
                 String waitingText = this.plugin.getDirectTranslation("duel_waiting", langCode);
                 session.setCachedPosition("\u00a7f\u00a7l" + waitingText);
             } else {
                 int pos = 1;
                 pos = this.timeTrialDuels != null ? this.timeTrialDuels.getPlayerPosition(session.getDuelId(), player.getUniqueId()) : this.dm.getplayerpositiononduel(session.getDuelId(), player);
                 session.setCachedPosition(this.formatPosition(pos, langCode));
             }
             if (session.getPersonalBest().equals("None")) {
                 if (bestLap != null && bestLap > 0.0) {
                     session.setPersonalBest(this.formatTime(bestLap));
                 } else {
                     session.setPersonalBest("--:--.---");
                 }
             }
             this.updateDelta(session);
         });
     }

     private void updateDelta(DuelSession session) {
         double bestTime;
         Map<Integer, Double> bestCheckpoints = session.getBestLapCheckpointTimes();
         if (bestCheckpoints == null || bestCheckpoints.isEmpty()) {
             session.setCachedDelta("");
             return;
         }
         Map currentCheckpoints = this.dm.getDuelCheckpointTimes(session.uuid, session.getDuelId());
         if (currentCheckpoints.isEmpty()) {
             session.setCachedDelta("");
             return;
         }
         int lastCheckpointId = (int) currentCheckpoints.keySet().stream() // Força a conversão para Integer
                 .max(Comparator.naturalOrder()) // Usa a ordem natural (1, 2, 3...)
                 .orElse(-1);         if (lastCheckpointId == -1) {
             session.setCachedDelta("");
             return;
         }
         if (lastCheckpointId == session.lastProcessedCheckpointId) {
             return;
         }
         session.lastProcessedCheckpointId = lastCheckpointId;
         if (!bestCheckpoints.containsKey(lastCheckpointId)) {
             session.setCachedDelta("");
             return;
         }
         double currentTime = (Double)currentCheckpoints.get(lastCheckpointId);
         double delta = currentTime - (bestTime = bestCheckpoints.get(lastCheckpointId).doubleValue());
         String deltaStr = Math.abs(delta) < 0.001 ? " \u00a7e\u00b10.000" : (delta < 0.0 ? String.format(" \u00a7a%.3f", delta) : String.format(" \u00a7c+%.3f", delta));
         session.setCachedDelta(deltaStr);
         this.plugin.getDebugManager().logDuelSystem("Delta atualizado para checkpoint " + lastCheckpointId + ": current=" + String.format("%.3f", currentTime) + "s, best=" + String.format("%.3f", bestTime) + "s, delta=" + deltaStr);
     }

     private String formatPosition(int pos, String langCode) {
         String positionText;
         if (pos <= 0) {
             pos = 1;
         }
         return (switch (pos) {
             case 1 -> {
                 positionText = this.plugin.getDirectTranslation("duel_position_1st", langCode);
                 yield "\u00a7a\u00a7l";
             }
             case 2 -> {
                 positionText = this.plugin.getDirectTranslation("duel_position_2nd", langCode);
                 yield "\u00a7e\u00a7l";
             }
             case 3 -> {
                 positionText = this.plugin.getDirectTranslation("duel_position_3rd", langCode);
                 yield "\u00a76\u00a7l";
             }
             default -> {
                 positionText = this.plugin.getTranslation("duel_position_nth", langCode, "{position}", String.valueOf(pos));
                 yield "\u00a7f\u00a7l";
             }
         }) + positionText;
     }

     public String formatTime(double seconds) {
         long totalMillis = (long)(seconds * 1000.0);
         return String.format("%02d:%02d.%03d", totalMillis / 60000L, totalMillis % 60000L / 1000L, totalMillis % 1000L);
     }

     private void spawnLeaderParticles(Player player) {
         player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0.0, 0.1, 0.0), 1, (Object)new Particle.DustOptions(Color.AQUA, 1.0f));
     }

     private static class DuelSession {
         private final UUID uuid;
         private final int duelId;
         private final long startTime;
         private String cachedPosition = "\u00a7f\u00a7l...";
         private String personalBest = "None";
         private int tickCounter = 0;
         private Double bestLapTime = null;
         private Map<Integer, Double> bestLapCheckpointTimes = new HashMap<Integer, Double>();
         private double currentLapStartTime;
         private String cachedDelta = "";
         private String langCode = "en_US";
         private int lastProcessedCheckpointId = -1;
         private boolean isPaused = false;
         private long pausedTime = 0L;
         private long pauseStartTime = 0L;
         private boolean waitingForOthers = false;

         public DuelSession(UUID uuid, int duelId) {
             this.uuid = uuid;
             this.duelId = duelId;
             this.startTime = System.currentTimeMillis();
             this.currentLapStartTime = System.currentTimeMillis();
         }

         public boolean shouldUpdateData() {
             return this.tickCounter++ % 10 == 0;
         }

         public long getCurrentTimeMillis() {
             return System.currentTimeMillis() - this.startTime;
         }

         public String getFormattedTime() {
             long elapsed = this.getCurrentTimeMillis();
             return String.format("%02d:%02d.%03d", elapsed / 60000L % 60L, elapsed / 1000L % 60L, elapsed % 1000L);
         }

         public String getFormattedLapTime() {
             long elapsed = (long)(this.getCurrentLapTime() * 1000.0);
             return String.format("%02d:%02d.%03d", elapsed / 60000L % 60L, elapsed / 1000L % 60L, elapsed % 1000L);
         }

         public int getDuelId() {
             return this.duelId;
         }

         public String getCachedPosition() {
             return this.cachedPosition;
         }

         public void setCachedPosition(String pos) {
             this.cachedPosition = pos;
         }

         public String getPersonalBest() {
             return this.personalBest;
         }

         public void setPersonalBest(String personalBest) {
             this.personalBest = personalBest;
         }

         public Double getBestLapTime() {
             return this.bestLapTime;
         }

         public void setBestLapTime(Double time) {
             this.bestLapTime = time;
         }

         public Map<Integer, Double> getBestLapCheckpointTimes() {
             return this.bestLapCheckpointTimes;
         }

         public void setBestLapCheckpointTimes(Map<Integer, Double> checkpointTimes) {
             this.bestLapCheckpointTimes = new HashMap<Integer, Double>(checkpointTimes);
         }

         public double getCurrentLapTime() {
             if (this.isPaused) {
                 return ((double)this.pauseStartTime - this.currentLapStartTime - (double)this.pausedTime) / 1000.0;
             }
             return ((double)System.currentTimeMillis() - this.currentLapStartTime - (double)this.pausedTime) / 1000.0;
         }

         public void resetLapTimer() {
             this.currentLapStartTime = System.currentTimeMillis();
             this.lastProcessedCheckpointId = -1;
             this.pausedTime = 0L;
             this.isPaused = false;
         }

         public void pauseLapTimer() {
             if (!this.isPaused) {
                 this.isPaused = true;
                 this.pauseStartTime = System.currentTimeMillis();
             }
         }

         public void resumeLapTimer() {
             if (this.isPaused) {
                 this.isPaused = false;
                 this.pausedTime += System.currentTimeMillis() - this.pauseStartTime;
             }
         }

         public String getCachedDelta() {
             return this.cachedDelta;
         }

         public void setCachedDelta(String delta) {
             this.cachedDelta = delta;
         }

         public String getLangCode() {
             return this.langCode;
         }

         public void setLangCode(String langCode) {
             this.langCode = langCode;
         }

         public boolean isWaitingForOthers() {
             return this.waitingForOthers;
         }

         public void setWaitingForOthers(boolean waiting) {
             this.waitingForOthers = waiting;
             if (waiting) {
                 this.pauseLapTimer();
             }
         }
     }
 }