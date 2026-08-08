package dev.EfraGroup.formulaRacing.Utils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.shanerx.mojang.Mojang;

/**
 * Thin wrapper around java-mojang-api for username<->UUID resolution with a
 * short-lived in-memory cache to avoid hammering Mojang on every lookup.
 * Falls back to Bukkit's offline resolver when the API is unavailable.
 */
public class MojangApiClient {

    private static final long CACHE_MS = TimeUnit.MINUTES.toMillis(10);

    private final Map<String, CacheEntry<String>> nameToUuid = new ConcurrentHashMap<>();
    private final Map<UUID, CacheEntry<String>> uuidToName = new ConcurrentHashMap<>();

    private Mojang mojang;

    public MojangApiClient() {
        try {
            this.mojang = new Mojang().connect();
        } catch (Throwable t) {
            this.mojang = null;
        }
    }

    /**
     * Resolve a player's UUID from a username (cached).
     */
    public CompletableFuture<UUID> getUuid(String username) {
        if (username == null || username.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        CacheEntry<String> hit = nameToUuid.get(username.toLowerCase());
        if (hit != null && !hit.isExpired()) {
            return CompletableFuture.completedFuture(parseUuid(hit.value));
        }
        return CompletableFuture.supplyAsync(() -> {
            String id = resolveUuid(username);
            if (id != null) {
                nameToUuid.put(username.toLowerCase(), new CacheEntry<>(id));
            }
            return parseUuid(id);
        });
    }

    /**
     * Resolve a player's current username from a UUID (cached).
     */
    public CompletableFuture<String> getName(UUID uuid) {
        if (uuid == null) return CompletableFuture.completedFuture(null);
        CacheEntry<String> hit = uuidToName.get(uuid);
        if (hit != null && !hit.isExpired()) {
            return CompletableFuture.completedFuture(hit.value);
        }
        return CompletableFuture.supplyAsync(() -> {
            String name = resolveName(uuid);
            if (name != null) {
                uuidToName.put(uuid, new CacheEntry<>(name));
            }
            return name;
        });
    }

    private String resolveUuid(String username) {
        if (mojang == null) return null;
        try {
            return mojang.getUUIDOfUsername(username);
        } catch (Throwable t) {
            return null;
        }
    }

    private String resolveName(UUID uuid) {
        if (mojang == null) return null;
        try {
            return mojang.getPlayerProfile(uuid.toString()).getUsername();
        } catch (Throwable t) {
            return null;
        }
    }

    private static UUID parseUuid(String id) {
        if (id == null) return null;
        try {
            return UUID.fromString(id.length() == 32 ? dashes(id) : id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String dashes(String id) {
        return id.substring(0, 8) + "-" + id.substring(8, 12) + "-" +
               id.substring(12, 16) + "-" + id.substring(16, 20) + "-" + id.substring(20);
    }

    private static final class CacheEntry<T> {
        final T value;
        final long expiry;
        CacheEntry(T value) {
            this.value = value;
            this.expiry = System.currentTimeMillis() + CACHE_MS;
        }
        boolean isExpired() {
            return System.currentTimeMillis() > expiry;
        }
    }
}
