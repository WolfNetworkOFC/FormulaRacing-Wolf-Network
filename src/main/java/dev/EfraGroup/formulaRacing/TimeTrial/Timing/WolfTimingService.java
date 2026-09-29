package dev.EfraGroup.formulaRacing.TimeTrial.Timing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.entity.Player;

public final class WolfTimingService {

    private final FormulaRacing plugin;
    private final Map<UUID, ClientState> clients = new ConcurrentHashMap<>();
    private final Map<UUID, SoloTimingAttempt> attempts = new ConcurrentHashMap<>();
    private final AtomicLong pingSequence = new AtomicLong();
    private final int maxDeltaMillis;
    private final int maxRttMillis;
    private final int reportTimeoutMillis;

    public WolfTimingService(FormulaRacing plugin) {
        this.plugin = plugin;
        this.maxDeltaMillis = Math.max(
            0,
            plugin.getConfig().getInt("time-trial.wolf-timing.max-client-server-delta-ms", 100)
        );
        this.maxRttMillis = Math.max(
            this.maxDeltaMillis,
            plugin.getConfig().getInt("time-trial.wolf-timing.max-rtt-ms", 750)
        );
        this.reportTimeoutMillis = Math.max(
            0,
            plugin.getConfig().getInt("time-trial.wolf-timing.report-timeout-ms", 500)
        );
    }

    public boolean isEnabled() {
        return this.plugin.getConfig().getBoolean("time-trial.wolf-timing.enabled", true);
    }

    public void onPlayerJoin(Player player) {
        if (!this.isEnabled() || player == null || !player.isOnline()) {
            return;
        }
        ClientState state = this.clients.computeIfAbsent(
            player.getUniqueId(),
            ignored -> new ClientState()
        );
        this.plugin.getWolfMod().sendConfig(
            player,
            WolfTimingProtocol.HELLO,
            WolfTimingProtocol.hello(this.maxDeltaMillis, this.maxRttMillis)
        );
        if (state.requestedTrack != null && state.capabilitiesAccepted) {
            this.armForTrack(player, state.requestedTrack);
        }
    }

    public void prepareTrack(Player player, String trackName) {
        if (!this.isEnabled() || player == null || trackName == null) {
            return;
        }
        String normalizedTrack = normalizeTrackName(trackName);
        ClientState state = this.clients.computeIfAbsent(
            player.getUniqueId(),
            ignored -> new ClientState()
        );
        state.requestedTrack = normalizedTrack;
        SoloTimingAttempt attempt = this.getAttempt(player.getUniqueId());
        if (attempt == null || !normalizedTrack.equalsIgnoreCase(attempt.getTrackName())) {
            this.armForTrack(player, normalizedTrack);
        } else if (state.capabilitiesAccepted) {
            this.sendGeometry(player, attempt.getRunId());
        }
    }

    public void handleIncoming(Player player, String key, String value) {
        if (
            !this.isEnabled()
                || player == null
                || !player.isOnline()
                || key == null
                || !key.startsWith("tt_v1_")
        ) {
            return;
        }
        JsonObject json = WolfTimingProtocol.parseObject(value);
        if (json == null) {
            return;
        }
        SchedulerHelper.runTaskFor(
            this.plugin,
            player,
            () -> this.handleIncomingOnPlayer(player, key, json)
        );
    }

    public SoloTimingAttempt armForTrack(Player player, String trackName) {
        if (!this.isEnabled() || player == null || trackName == null) {
            return null;
        }
        String normalizedTrack = normalizeTrackName(trackName);
        ClientState client = this.clients.computeIfAbsent(
            player.getUniqueId(),
            ignored -> new ClientState()
        );
        this.abort(player.getUniqueId(), false);
        SoloTimingAttempt attempt = new SoloTimingAttempt(
            UUID.randomUUID(),
            normalizedTrack
        );
        this.attempts.put(player.getUniqueId(), attempt);
        client.activeRunId = attempt.getRunId();
        this.sendGeometry(player, attempt.getRunId());
        return attempt;
    }

    public SoloTimingAttempt getAttempt(UUID uuid) {
        return uuid == null ? null : this.attempts.get(uuid);
    }

    public SoloTimingAttempt onServerStart(UUID uuid, long startNanos) {
        SoloTimingAttempt attempt = this.getAttempt(uuid);
        if (attempt == null) {
            return null;
        }
        if (attempt.getState() == SoloTimingAttempt.State.ARMED) {
            attempt.tryStart(startNanos);
        } else if (attempt.getState() == SoloTimingAttempt.State.RUNNING) {
            // A fresh timer started while the previous attempt was still marked RUNNING: its
            // startNanos belongs to the abandoned run, and trusting it would add the dead time
            // to the next official result. Re-base on the new start instead.
            attempt.tryRestart(startNanos);
        }
        return attempt.getState() == SoloTimingAttempt.State.RUNNING ? attempt : null;
    }

    public CompletableFuture<OfficialTime> onServerFinish(
        Player player,
        long startNanos,
        long finishNanos
    ) {
        if (player == null) {
            return CompletableFuture.completedFuture(
                OfficialTime.fromServerMillis(0L, null)
            );
        }
        SoloTimingAttempt attempt = this.getAttempt(player.getUniqueId());
        // The start handed in by the caller is authoritative: it comes from the live
        // PlayerTimerData of this run. Only fall back to the attempt when the caller has none,
        // never the other way round — an attempt left over from an abandoned run must not
        // extend the measured window backwards.
        long effectiveStart = startNanos > 0L
            ? startNanos
            : (attempt != null && attempt.getStartNanos() > 0L
                ? attempt.getStartNanos()
                : finishNanos);
        long observedMillis = Math.max(0L, finishNanos - effectiveStart) / 1_000_000L;
        if (attempt == null || attempt.getState() != SoloTimingAttempt.State.RUNNING) {
            return CompletableFuture.completedFuture(
                OfficialTime.fromServerMillis(observedMillis, attempt == null ? null : attempt.getRunId())
            );
        }

        if (!attempt.tryFinishPending(finishNanos)) {
            return attempt.getResolution();
        }

        ClientState client = this.clients.get(player.getUniqueId());
        if (client == null || !client.capabilitiesAccepted || !client.clientReady) {
            return this.completeResolution(player, attempt, observedMillis);
        }

        if (attempt.getClientFinishNanos() != null && client.rttValidated) {
            return this.completeResolution(player, attempt, observedMillis);
        }

        if (this.reportTimeoutMillis <= 0) {
            return this.completeResolution(player, attempt, observedMillis);
        }
        int delayTicks = Math.max(1, (this.reportTimeoutMillis + 49) / 50);
        SchedulerHelper.runTaskFor(
            this.plugin,
            player,
            () -> this.completeResolution(player, attempt, observedMillis),
            delayTicks
        );
        return attempt.getResolution();
    }

    public void markAttemptFailed(Player player, SoloTimingAttempt expectedAttempt) {
        if (player == null || expectedAttempt == null) {
            return;
        }
        SoloTimingAttempt removed = this.attempts.remove(player.getUniqueId(), expectedAttempt)
            ? expectedAttempt
            : null;
        if (removed == null) {
            return;
        }
        removed.abort();
        removed.getResolution().completeExceptionally(
            new CancellationException("Timing attempt aborted")
        );
        ClientState client = this.clients.get(player.getUniqueId());
        if (client != null && expectedAttempt.getRunId().equals(client.activeRunId)) {
            this.sendDisarm(player, expectedAttempt.getRunId());
            client.activeRunId = null;
        }
    }

    public void abort(UUID uuid, boolean notifyClient) {
        if (uuid == null) {
            return;
        }
        SoloTimingAttempt attempt = this.attempts.remove(uuid);
        ClientState client = this.clients.get(uuid);
        if (client != null) {
            if (notifyClient && client.activeRunId != null) {
                Player player = this.plugin.getServer().getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    this.sendDisarm(player, client.activeRunId);
                }
            }
            client.activeRunId = null;
            client.pingSentNanos = 0L;
            client.pingSequence = 0L;
        }
        if (attempt != null) {
            attempt.abort();
            attempt.getResolution().completeExceptionally(
                new CancellationException("Timing attempt aborted")
            );
        }
    }

    public void cleanupPlayer(UUID uuid) {
        this.abort(uuid, false);
        this.clients.remove(uuid);
    }

    public void shutdown() {
        for (UUID uuid : this.attempts.keySet()) {
            this.abort(uuid, false);
        }
        this.clients.clear();
    }

    public int getMaxDeltaMillis() {
        return this.maxDeltaMillis;
    }

    public int getMaxRttMillis() {
        return this.maxRttMillis;
    }

    public int getReportTimeoutMillis() {
        return this.reportTimeoutMillis;
    }

    private void handleIncomingOnPlayer(Player player, String key, JsonObject json) {
        if (!player.isOnline()) {
            return;
        }
        switch (key) {
            case WolfTimingProtocol.CAPABILITIES -> this.handleCapabilities(player, json);
            case WolfTimingProtocol.PING -> this.handlePing(player, json);
            case WolfTimingProtocol.PONG -> this.handlePong(player, json);
            case WolfTimingProtocol.REPORT -> this.handleReport(player, json);
            case WolfTimingProtocol.READY -> this.handleReady(player, json);
            default -> {
            }
        }
    }

    private void handleCapabilities(Player player, JsonObject json) {
        if (
            !hasCapability(json, "TT_CLIENT_REPORT_V1")
                || !hasCapability(json, "TT_SWEPT_DETECT_V1")
        ) {
            return;
        }
        ClientState state = this.clients.computeIfAbsent(
            player.getUniqueId(),
            ignored -> new ClientState()
        );
        state.capabilitiesAccepted = true;
        state.protocol = json.get("protocol").getAsInt();
        this.plugin.getWolfMod().sendConfig(
            player,
            WolfTimingProtocol.READY,
            WolfTimingProtocol.ready(this.maxDeltaMillis)
        );
        this.sendPing(player, state);
        if (state.requestedTrack != null) {
            SoloTimingAttempt existingAttempt = this.getAttempt(player.getUniqueId());
            if (existingAttempt == null) {
                this.armForTrack(player, state.requestedTrack);
            } else {
                this.sendGeometry(player, existingAttempt.getRunId());
            }
        }
    }

    private void handleReady(Player player, JsonObject json) {
        ClientState state = this.clients.computeIfAbsent(
            player.getUniqueId(),
            ignored -> new ClientState()
        );
        if (!state.capabilitiesAccepted || json.get("protocol").getAsInt() != WolfTimingProtocol.VERSION) {
            return;
        }
        state.clientReady = true;
        if (state.requestedTrack != null && this.getAttempt(player.getUniqueId()) == null) {
            this.armForTrack(player, state.requestedTrack);
        }
    }

    private void handlePing(Player player, JsonObject json) {
        if (!json.has("sequence")) {
            return;
        }
        this.plugin.getWolfMod().sendConfig(
            player,
            WolfTimingProtocol.PONG,
            WolfTimingProtocol.pong(json.get("sequence").getAsLong())
        );
    }

    private void handlePong(Player player, JsonObject json) {
        if (!json.has("sequence")) {
            return;
        }
        ClientState state = this.clients.get(player.getUniqueId());
        if (
            state == null
                || state.pingSentNanos == 0L
                || state.pingSequence != json.get("sequence").getAsLong()
        ) {
            return;
        }
        long sampleMillis = Math.max(0L, System.nanoTime() - state.pingSentNanos) / 1_000_000L;
        state.pingSentNanos = 0L;
        state.pingSequence = 0L;
        if (sampleMillis > this.maxRttMillis) {
            state.rttValidated = false;
            return;
        }
        state.rttMillis = state.rttMillis == 0L
            ? sampleMillis
            : Math.round((state.rttMillis * 3.0 + sampleMillis) / 4.0);
        state.rttValidated = true;
    }

    private void handleReport(Player player, JsonObject json) {
        if (
            !json.has("runId")
                || !json.has("role")
                || !json.has("sequence")
                || !json.has("atNanos")
                || !json.has("startNanos")
        ) {
            return;
        }
        UUID runId;
        try {
            runId = UUID.fromString(json.get("runId").getAsString());
        } catch (IllegalArgumentException exception) {
            return;
        }
        SoloTimingAttempt attempt = this.getAttempt(player.getUniqueId());
        ClientState client = this.clients.get(player.getUniqueId());
        if (
            attempt == null
                || client == null
                || !client.capabilitiesAccepted
                || !client.clientReady
                || !attempt.getRunId().equals(runId)
        ) {
            return;
        }
        String role = json.get("role").getAsString();
        long sequence = json.get("sequence").getAsLong();
        long startNanos = json.get("startNanos").getAsLong();
        long atNanos = json.get("atNanos").getAsLong();
        if ("START".equalsIgnoreCase(role)) {
            attempt.acceptClientStart(sequence, atNanos);
        } else if ("END".equalsIgnoreCase(role)) {
            if (attempt.acceptClientFinish(sequence, startNanos, atNanos)
                && attempt.getState() == SoloTimingAttempt.State.FINISH_PENDING) {
                this.completeResolution(player, attempt, attempt.getServerElapsedMillis());
            }
        }
    }

    private CompletableFuture<OfficialTime> completeResolution(
        Player player,
        SoloTimingAttempt attempt,
        long observedMillis
    ) {
        if (!attempt.tryFinalize()) {
            return attempt.getResolution();
        }
        ClientState client = this.clients.get(player.getUniqueId());
        boolean clientRttValid = client != null
            && client.capabilitiesAccepted
            && client.clientReady
            && client.rttValidated;
        Long proposedDisplayMillis = !clientRttValid
            || attempt.getClientStartNanos() == null
            || attempt.getClientFinishNanos() == null
            ? null
            : Math.max(
                0L,
                (attempt.getClientFinishNanos() - attempt.getClientStartNanos()) / 1_000_000L
            );
        OfficialTime result = OfficialTime.resolve(
            observedMillis,
            proposedDisplayMillis,
            this.maxDeltaMillis,
            attempt.getRunId()
        );
        this.attempts.remove(player.getUniqueId(), attempt);
        if (client != null && attempt.getRunId().equals(client.activeRunId)) {
            client.activeRunId = null;
        }
        if (player.isOnline()) {
            this.plugin.getWolfMod().sendConfig(
                player,
                WolfTimingProtocol.RESULT,
                WolfTimingProtocol.result(attempt.getRunId(), result, "WOLF".equals(result.source()))
            );
            this.sendDisarm(player, attempt.getRunId());
        }
        attempt.getResolution().complete(result);
        return attempt.getResolution();
    }

    private void sendGeometry(Player player, UUID runId) {
        SoloTimingAttempt attempt = this.getAttempt(player.getUniqueId());
        ClientState client = this.clients.get(player.getUniqueId());
        if (attempt == null || client == null || !client.capabilitiesAccepted || !attempt.getRunId().equals(runId)) {
            return;
        }
        SchedulerHelper.runAsync(this.plugin, () -> {
            List<DatabaseManager.RegionData> startRegions = this.plugin.getTrackIntegrationManager()
                .getTrackRegionsByType(attempt.getTrackName(), "START");
            List<DatabaseManager.RegionData> endRegions = this.plugin.getTrackIntegrationManager()
                .getTrackRegionsByType(attempt.getTrackName(), "END");
            SchedulerHelper.runTaskFor(this.plugin, player, () -> {
                SoloTimingAttempt current = this.getAttempt(player.getUniqueId());
                if (
                    current == null
                        || !current.getRunId().equals(runId)
                        || !player.isOnline()
                ) {
                    return;
                }
                this.plugin.getWolfMod().sendConfig(
                    player,
                    WolfTimingProtocol.GEOMETRY,
                    WolfTimingProtocol.geometry(
                        runId,
                        startRegions.isEmpty() ? null : startRegions.get(0).getWorld(),
                        startRegions,
                        endRegions
                    )
                );
            });
        });
    }

    private void sendPing(Player player, ClientState state) {
        if (state.pingSentNanos != 0L || state.rttValidated) {
            return;
        }
        long sequence = this.pingSequence.incrementAndGet();
        state.pingSequence = sequence;
        state.pingSentNanos = System.nanoTime();
        JsonObject ping = new JsonObject();
        ping.addProperty("protocol", WolfTimingProtocol.VERSION);
        ping.addProperty("sequence", sequence);
        this.plugin.getWolfMod().sendConfig(player, WolfTimingProtocol.PING, ping.toString());
    }

    private void sendDisarm(Player player, UUID runId) {
        if (player.isOnline()) {
            this.plugin.getWolfMod().sendConfig(
                player,
                WolfTimingProtocol.DISARM,
                WolfTimingProtocol.disarm(runId)
            );
        }
    }

    private boolean isClientTimingReady(ClientState state) {
        return state != null && state.capabilitiesAccepted && state.clientReady && state.rttValidated;
    }

    private boolean hasCapability(JsonObject json, String capability) {
        if (!json.has("clientCapabilities") || !json.get("clientCapabilities").isJsonArray()) {
            return false;
        }
        JsonElement capabilities = json.get("clientCapabilities");
        for (JsonElement element : capabilities.getAsJsonArray()) {
            if (capability.equals(element.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private String normalizeTrackName(String trackName) {
        return trackName.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    private static final class ClientState {
        private volatile boolean capabilitiesAccepted;
        private volatile boolean clientReady;
        private volatile int protocol;
        private volatile long pingSentNanos;
        private volatile long pingSequence;
        private volatile long rttMillis;
        private volatile boolean rttValidated;
        private volatile String requestedTrack;
        private volatile UUID activeRunId;
    }
}
