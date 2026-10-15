/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.boatlabs.boatlabsmod.protocol.track.TrackPacket
 *  net.minecraft.class_2338
 */
package net.boatlabs.boatlabsmod.track;

import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.boatlabs.boatlabsmod.protocol.track.TrackPacket;
import net.boatlabs.boatlabsmod.region.CuboidRegion;
import net.boatlabs.boatlabsmod.region.PolygonalRegion;
import net.boatlabs.boatlabsmod.region.Region;
import net.boatlabs.boatlabsmod.region.RegionType;
import net.minecraft.class_2338;

public final class Track {
    private final int id;
    public final boolean timingAllowed;
    public final Map<RegionType, List<Region>> regions = new EnumMap<RegionType, List<Region>>(RegionType.class);

    public Track(TrackPacket trackPacket) {
        this.id = trackPacket.id();
        this.timingAllowed = trackPacket.timingAllowed();
        trackPacket.regions().forEach(packetRegion -> {
            Optional<RegionType> regionType = RegionType.fromId(packetRegion.regionType());
            if (regionType.isEmpty()) {
                return;
            }
            List regionList = this.regions.computeIfAbsent(regionType.get(), type -> new LinkedList());
            Record newRegion = packetRegion.points().isEmpty() ? new CuboidRegion(packetRegion.id(), packetRegion.index(), regionType.get(), new class_2338((int)packetRegion.minPoint().x(), (int)packetRegion.minPoint().y(), (int)packetRegion.minPoint().z()), new class_2338((int)packetRegion.maxPoint().x(), (int)packetRegion.maxPoint().y(), (int)packetRegion.maxPoint().z())) : new PolygonalRegion(packetRegion.id(), packetRegion.index(), regionType.get(), new class_2338((int)packetRegion.minPoint().x(), (int)packetRegion.minPoint().y(), (int)packetRegion.minPoint().z()), new class_2338((int)packetRegion.maxPoint().x(), (int)packetRegion.maxPoint().y(), (int)packetRegion.maxPoint().z()), packetRegion.points().stream().map(vec -> new class_2338((int)vec.x(), (int)vec.y(), (int)vec.z())).toList());
            regionList.add(newRegion);
        });
    }

    public List<Region> getRegions(RegionType type) {
        return this.regions.getOrDefault((Object)type, List.of());
    }

    public void forEachRegion(Consumer<Region> consumer) {
        for (Map.Entry<RegionType, List<Region>> entry : this.regions.entrySet()) {
            for (Region region : entry.getValue()) {
                consumer.accept(region);
            }
        }
    }
}

