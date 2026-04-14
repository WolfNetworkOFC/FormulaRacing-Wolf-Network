package dev.EfraGroup.formulaRacing.Participant;

import dev.EfraGroup.formulaRacing.Heat.Heats;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DriverLookup {
    private final Map<UUID, Driver> playerToDriver = new ConcurrentHashMap<>();
    private final Map<UUID, Heats> playerToHeat = new ConcurrentHashMap<>();

    public void register(Driver driver, Heats heat) {
        playerToDriver.put(driver.getUuid(), driver);
        playerToHeat.put(driver.getUuid(), heat);
    }

    public void unregister(UUID playerUUID) {
        playerToDriver.remove(playerUUID);
        playerToHeat.remove(playerUUID);
    }

    public Driver getDriver(UUID playerUUID) {
        return playerToDriver.get(playerUUID);
    }

    public Heats getHeat(UUID playerUUID) {
        return playerToHeat.get(playerUUID);
    }

    public boolean isRacing(UUID playerUUID) {
        return playerToDriver.containsKey(playerUUID);
    }

    public void clear() {
        playerToDriver.clear();
        playerToHeat.clear();
    }
}
