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
         Location midLocation = new Location(from.getWorld(), 0, 0, 0);
         for (int i = 0; i < 15; ++i) {
             double mid = (low + high) / 2.0;
             double x = from.getX() + (to.getX() - from.getX()) * mid;
             double y = from.getY() + (to.getY() - from.getY()) * mid;
             double z = from.getZ() + (to.getZ() - from.getZ()) * mid;
             midLocation.setX(x);
             midLocation.setY(y);
             midLocation.setZ(z);
             if (region.contains(midLocation)) {
                 high = mid;
             } else {
                 low = mid;
             }
         }
         return (low + high) / 2.0;
     }

     public static double calculateRegionEntryProportion(Location from, Location to, DatabaseManager.RegionData region) {
         double low = 0.0;
         double high = 1.0;
         Location midLocation = new Location(from.getWorld(), 0, 0, 0);
         for (int i = 0; i < 15; ++i) {
             double mid = (low + high) / 2.0;
             double x = from.getX() + (to.getX() - from.getX()) * mid;
             double y = from.getY() + (to.getY() - from.getY()) * mid;
             double z = from.getZ() + (to.getZ() - from.getZ()) * mid;
             midLocation.setX(x);
             midLocation.setY(y);
             midLocation.setZ(z);
             if (RegionMathUtils.containsLocal(midLocation, region)) {
                 high = mid;
             } else {
                 low = mid;
             }
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

          if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
              return false;
          }

          if (r.isPoly()) {
              double[][] polygon = r.getPolyPoints();
              if (polygon == null || polygon.length < 3) {
                  return true;
              }
              return pointInPolygon(x, z, polygon);
          }

          return true;
      }

      public static boolean intersectsRegion(Location from, Location to, DatabaseManager.RegionData r) {
          double minX = Math.min(r.getMinX(), r.getMaxX());
          double maxX = Math.max(r.getMinX(), r.getMaxX());
          double minY = Math.min(r.getMinY(), r.getMaxY());
          double maxY = Math.max(r.getMinY(), r.getMaxY());
          double minZ = Math.min(r.getMinZ(), r.getMaxZ());
          double maxZ = Math.max(r.getMinZ(), r.getMaxZ());
          double fx = from.getX();
          double fy = from.getY();
          double fz = from.getZ();
          double tx = to.getX();
          double ty = to.getY();
          double tz = to.getZ();

          if (r.isPoly()) {
              return intersectsRegionPoly(from, to, r);
          }

          if (tx >= minX && tx <= maxX && ty >= minY && ty <= maxY && tz >= minZ && tz <= maxZ) {
              return true;
          } else {
              double dx = tx - fx;
              double dy = ty - fy;
              double dz = tz - fz;
              double tmin = 0.0;
              double tmax = 1.0;
              if (Math.abs(dx) > 1.0E-7) {
                  double t1 = (minX - fx) / dx;
                  double t2 = (maxX - fx) / dx;
                  tmin = Math.max(tmin, Math.min(t1, t2));
                  tmax = Math.min(tmax, Math.max(t1, t2));
              } else if (fx < minX || fx > maxX) {
                  return false;
              }
              if (Math.abs(dy) > 1.0E-7) {
                  double t1 = (minY - fy) / dy;
                  double t2 = (maxY - fy) / dy;
                  tmin = Math.max(tmin, Math.min(t1, t2));
                  tmax = Math.min(tmax, Math.max(t1, t2));
              } else if (fy < minY || fy > maxY) {
                  return false;
              }
              if (Math.abs(dz) > 1.0E-7) {
                  double t1 = (minZ - fz) / dz;
                  double t2 = (maxZ - fz) / dz;
                  tmin = Math.max(tmin, Math.min(t1, t2));
                  tmax = Math.min(tmax, Math.max(t1, t2));
              } else if (fz < minZ || fz > maxZ) {
                  return false;
              }
              return tmin <= tmax;
          }
      }

      private static boolean intersectsRegionPoly(Location from, Location to, DatabaseManager.RegionData r) {
          double minY = Math.min(r.getMinY(), r.getMaxY());
          double maxY = Math.max(r.getMinY(), r.getMaxY());
          double fy = from.getY();
          double ty = to.getY();

          if (fy < minY || fy > maxY || ty < minY || ty > maxY) {
              return false;
          }

          double[][] polygon = r.getPolyPoints();
          if (polygon == null || polygon.length < 3) {
              return false;
          }

          double fx = from.getX();
          double fz = from.getZ();
          double tx = to.getX();
          double tz = to.getZ();

          if (pointInPolygon(fx, fz, polygon) || pointInPolygon(tx, tz, polygon)) {
              return true;
          }

          return lineIntersectsPolygon(fx, fz, tx, tz, polygon);
      }

      private static boolean pointInPolygon(double x, double z, double[][] polygon) {
          int n = polygon.length;
          boolean inside = false;
          for (int i = 0, j = n - 1; i < n; j = i++) {
              double xi = polygon[i][0], zi = polygon[i][1];
              double xj = polygon[j][0], zj = polygon[j][1];
              if (((zi > z) != (zj > z)) && (x < (xj - xi) * (z - zi) / (zj - zi) + xi)) {
                  inside = !inside;
              }
          }
          return inside;
      }

      private static boolean lineIntersectsPolygon(double x1, double z1, double x2, double z2, double[][] polygon) {
          int n = polygon.length;
          for (int i = 0; i < n; i++) {
              int j = (i + 1) % n;
              double xi = polygon[i][0], zi = polygon[i][1];
              double xj = polygon[j][0], zj = polygon[j][1];
              if (lineIntersectsLine(x1, z1, x2, z2, xi, zi, xj, zj)) {
                  return true;
              }
          }
          return false;
      }

      private static boolean lineIntersectsLine(double x1, double z1, double x2, double z2, double x3, double z3, double x4, double z4) {
          double denom = (z4 - z3) * (x2 - x1) - (x4 - x3) * (z2 - z1);
          if (Math.abs(denom) < 1e-10) return false;
          double ua = ((x4 - x3) * (z1 - z3) - (z4 - z3) * (x1 - x3)) / denom;
          double ub = ((x2 - x1) * (z1 - z3) - (z2 - z1) * (x1 - x3)) / denom;
          return ua >= 0 && ua <= 1 && ub >= 0 && ub <= 1;
      }

      public static boolean isInsideRegion(Location loc, DatabaseManager.RegionData r) {
          double minX = Math.min(r.getMinX(), r.getMaxX());
          double maxX = Math.max(r.getMinX(), r.getMaxX());
          double minY = Math.min(r.getMinY(), r.getMaxY());
          double maxY = Math.max(r.getMinY(), r.getMaxY());
          double minZ = Math.min(r.getMinZ(), r.getMaxZ());
          double maxZ = Math.max(r.getMinZ(), r.getMaxZ());
          double x = loc.getX();
          double y = loc.getY();
          double z = loc.getZ();

          if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
              return false;
          }

          if (r.isPoly()) {
              double[][] polygon = r.getPolyPoints();
              if (polygon == null || polygon.length < 3) {
                  return true;
              }
              return pointInPolygon(x, z, polygon);
          }

          return true;
      }

      public static boolean isEnteringRegion(Location from, Location to, DatabaseManager.RegionData r) {
          return !isInsideRegion(from, r) && intersectsRegion(from, to, r);
      }
  }