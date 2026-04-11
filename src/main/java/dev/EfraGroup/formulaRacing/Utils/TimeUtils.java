 /*
  * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
  *
  * Could not load the following classes:
  *  org.bukkit.entity.Player
  */
 package dev.EfraGroup.formulaRacing.Utils;

 import org.bukkit.entity.Player;

 public class TimeUtils {
     public static void setPlayerTime(Player player, long time) {
         player.setPlayerTime(time, false);
     }

     public static void resetPlayerTime(Player player) {
         player.resetPlayerTime();
     }
 }

    