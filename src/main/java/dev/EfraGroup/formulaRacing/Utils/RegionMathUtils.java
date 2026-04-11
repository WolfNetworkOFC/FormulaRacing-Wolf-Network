 /*
  * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
  *
  * Could not load the following classes:
  *  org.bukkit.Location
  */
 package dev.EfraGroup.formulaRacing.Utils;

 import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
 import dev.EfraGroup.formulaRacing.RegionBox;
 import org.bukkit.Location;

 public class RegionMathUtils {
     public static double calculateRegionEntryProportion(Location from, Location to, RegionBox region) {
         double low = 0.0;
         double high = 1.0;
         for (int i = 0; i < 15; ++i) {
             double mid = (low + high) / 2.0;
             Location midLocation = RegionMathUtils.interpolateLocation(from, to, mid);
             if (region.contains(midLocation)) {
                 high = mid;
                 continue;
             }
             low = mid;
         }
         return (low + high) / 2.0;
     }

     public static double calculateRegionEntryProportion(Location from, Location to, DatabaseManager.RegionData region) {
         double low = 0.0;
         double high = 1.0;
         for (int i = 0; i < 15; ++i) {
             double mid = (low + high) / 2.0;
             Location midLocation = RegionMathUtils.interpolateLocation(from, to, mid);
             if (RegionMathUtils.containsLocal(midLocation, region)) {
                 high = mid;
                 continue;
             }
             low = mid;
         }
         return (low + high) / 2.0;
     }

     public static Location interpolateLocation(Location from, Location to, double proportion) {
         double x = from.getX() + (to.getX() - from.getX()) * proportion;
         double y = from.getY() + (to.getY() - from.getY()) * proportion;
         double z = from.getZ() + (to.getZ() - from.getZ()) * proportion;
         return new Location(from.getWorld(), x, y, z);
     }

     private static boolean containsLocal(Location loc, DatabaseManager.RegionData r) {
         double minX = Math.min(r.getMinX(), r.getMaxX());
         double maxX = Math.max(r.getMinX(), r.getMaxX());
         double minY = Math.min(r.getMinY(), r.getMaxY());
         double maxY = Math.max(r.getMinY(), r.getMaxY());
         double minZ = Math.min(r.getMinZ(), r.getMaxZ());
         double maxZ = Math.max(r.getMinZ(), r.getMaxZ());
         double x = loc.getX();
         double y = loc.getY();
         double z = loc.getZ();
         return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
     }
 }