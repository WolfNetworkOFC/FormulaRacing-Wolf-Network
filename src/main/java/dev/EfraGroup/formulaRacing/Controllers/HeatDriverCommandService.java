package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class HeatDriverCommandService {
    private static final long LOCK_TIMEOUT_MS = 400L;
    private final FormulaRacing plugin;
    private final RaceEventManager eventManager;
    private final ConcurrentHashMap<String, ReentrantLock> roundLocks = new ConcurrentHashMap<>();

    public HeatDriverCommandService(FormulaRacing plugin) {
        this.plugin = plugin;
        this.eventManager = plugin.getRaceEventManager();
    }

    public CompletableFuture<DriverMutationResult> addDriver(Heats heat, UUID targetUuid, String targetName, Integer requestedPosition) {
        if (heat == null || targetUuid == null) {
            return CompletableFuture.completedFuture(DriverMutationResult.of(DriverMutationStatus.INVALID_CONTEXT));
        }

        return this.supplyAsync(() -> this.withRoundLock(heat, () -> this.addDriverLocked(heat, targetUuid, targetName, requestedPosition)));
    }

    public DriverMutationResult addDriverSync(Heats heat, UUID targetUuid, String targetName, Integer requestedPosition) {
        if (heat == null || targetUuid == null) {
            return DriverMutationResult.of(DriverMutationStatus.INVALID_CONTEXT);
        }

        return this.withRoundLock(heat, () -> this.addDriverLocked(heat, targetUuid, targetName, requestedPosition));
    }

    public CompletableFuture<DriverMutationResult> removeDriver(Heats heat, UUID targetUuid, String targetName) {
        if (heat == null || targetUuid == null) {
            return CompletableFuture.completedFuture(DriverMutationResult.of(DriverMutationStatus.INVALID_CONTEXT));
        }

        return this.supplyAsync(() -> this.withRoundLock(heat, () -> this.removeDriverLocked(heat, targetUuid, targetName)));
    }

    public DriverMutationResult removeDriverSync(Heats heat, UUID targetUuid, String targetName) {
        if (heat == null || targetUuid == null) {
            return DriverMutationResult.of(DriverMutationStatus.INVALID_CONTEXT);
        }

        return this.withRoundLock(heat, () -> this.removeDriverLocked(heat, targetUuid, targetName));
    }

    private DriverMutationResult addDriverLocked(Heats heat, UUID targetUuid, String targetName, Integer requestedPosition) {
        if (!this.isEditable(heat.getHeatState())) {
            return DriverMutationResult.of(DriverMutationStatus.INVALID_HEAT_STATE);
        }

        if (heat.getDrivers().containsKey(targetUuid)) {
            return DriverMutationResult.of(DriverMutationStatus.ALREADY_IN_HEAT);
        }

        Rounds round = heat.getRound();
        if (round != null) {
            for (Heats siblingHeat : round.getHeats().values()) {
                if (siblingHeat != heat && siblingHeat.getDrivers().containsKey(targetUuid)) {
                    return DriverMutationResult.of(DriverMutationStatus.ALREADY_IN_ROUND);
                }
            }
        }

        int currentSize = heat.getDrivers().size();
        int maxDrivers = heat.getMaxDrivers();
        if (maxDrivers > 0 && currentSize >= maxDrivers) {
            return DriverMutationResult.of(DriverMutationStatus.HEAT_FULL);
        }

        int insertPosition = requestedPosition == null ? currentSize + 1 : requestedPosition;
        if (insertPosition < 1 || insertPosition > currentSize + 1) {
            return DriverMutationResult.of(DriverMutationStatus.INVALID_POSITION);
        }

        boolean persisted = this.eventManager.getDatabaseManager().addDriverToHeatWithShiftSync(targetUuid, heat.getId(), insertPosition);
        if (!persisted) {
            return DriverMutationResult.of(DriverMutationStatus.PERSISTENCE_ERROR);
        }

        if (!this.syncHeatDriversFromDatabase(heat)) {
            return DriverMutationResult.of(DriverMutationStatus.SYNC_ERROR);
        }

        return DriverMutationResult.success(insertPosition, targetName);
    }

    private DriverMutationResult removeDriverLocked(Heats heat, UUID targetUuid, String targetName) {
        if (!this.isEditable(heat.getHeatState())) {
            return DriverMutationResult.of(DriverMutationStatus.INVALID_HEAT_STATE);
        }

        if (!heat.getDrivers().containsKey(targetUuid)) {
            return DriverMutationResult.of(DriverMutationStatus.NOT_IN_HEAT);
        }

        boolean persisted = this.eventManager.getDatabaseManager().removeDriverFromHeatWithShiftSync(targetUuid, heat.getId());
        if (!persisted) {
            return DriverMutationResult.of(DriverMutationStatus.PERSISTENCE_ERROR);
        }

        if (!this.syncHeatDriversFromDatabase(heat)) {
            return DriverMutationResult.of(DriverMutationStatus.SYNC_ERROR);
        }

        return DriverMutationResult.success(-1, targetName);
    }

    private boolean syncHeatDriversFromDatabase(Heats heat) {
        try {
            List<Driver> persisted = this.eventManager.getDatabaseManager().loadDriversByHeatId(heat.getId());
            persisted.sort(Comparator.comparingInt(Driver::getStartPosition));

            Map<UUID, Driver> inMemory = heat.getDrivers();
            inMemory.clear();
            List<Driver> startGrid = heat.getStartPositions();
            startGrid.clear();

            int position = 1;
            for (Driver driver : persisted) {
                driver.setStartPosition(position);
                driver.setPosition(position);
                inMemory.put(driver.getUuid(), driver);
                startGrid.add(driver);
                position++;
            }

            heat.reorderGrid();
            return true;
        } catch (Exception exception) {
            this.plugin.getDebugManager().logDatabaseOperation("[HeatDriverCommandService] Falha ao sincronizar heat " + heat.getId() + ": " + exception.getMessage());
            return false;
        }
    }

    private DriverMutationResult withRoundLock(Heats heat, Supplier<DriverMutationResult> operation) {
        Rounds round = heat.getRound();
        if (round == null) {
            return DriverMutationResult.of(DriverMutationStatus.INVALID_CONTEXT);
        }

        String lockKey = "round:" + round.getId();
        ReentrantLock lock = this.roundLocks.computeIfAbsent(lockKey, unused -> new ReentrantLock());
        boolean acquired = false;

        try {
            acquired = lock.tryLock(LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!acquired) {
                return DriverMutationResult.of(DriverMutationStatus.CONFLICT);
            }

            return operation.get();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return DriverMutationResult.of(DriverMutationStatus.CONFLICT);
        } finally {
            if (acquired) {
                lock.unlock();
            }
            if (!lock.isLocked() && !lock.hasQueuedThreads()) {
                this.roundLocks.remove(lockKey, lock);
            }
        }
    }

    private boolean isEditable(HeatState heatState) {
        return heatState == HeatState.SETUP || heatState == HeatState.LOADED || heatState == HeatState.IDLE;
    }

    private <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        SchedulerHelper.runAsync(this.plugin, () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    public enum DriverMutationStatus {
        SUCCESS,
        INVALID_CONTEXT,
        INVALID_HEAT_STATE,
        ALREADY_IN_HEAT,
        ALREADY_IN_ROUND,
        HEAT_FULL,
        INVALID_POSITION,
        NOT_IN_HEAT,
        CONFLICT,
        PERSISTENCE_ERROR,
        SYNC_ERROR
    }

    public static final class DriverMutationResult {
        private final DriverMutationStatus status;
        private final int finalPosition;
        private final String targetName;

        private DriverMutationResult(DriverMutationStatus status, int finalPosition, String targetName) {
            this.status = status;
            this.finalPosition = finalPosition;
            this.targetName = targetName;
        }

        public static DriverMutationResult of(DriverMutationStatus status) {
            return new DriverMutationResult(status, -1, null);
        }

        public static DriverMutationResult success(int finalPosition, String targetName) {
            return new DriverMutationResult(DriverMutationStatus.SUCCESS, finalPosition, targetName);
        }

        public DriverMutationStatus getStatus() {
            return this.status;
        }

        public int getFinalPosition() {
            return this.finalPosition;
        }

        public String getTargetName() {
            return this.targetName;
        }
    }
}
