package dev.EfraGroup.formulaRacing;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Typed service registry replacing 50+ individual getters on FormulaRacing.
 *
 * Usage:
 *   // Registration (during bootstrap):
 *   registry.register(DatabaseManager.class, dm);
 *
 *   // Access (anywhere):
 *   DatabaseManager dm = registry.get(DatabaseManager.class);
 *
 *   // Lazy access (creates if missing):
 *   TimerUtils timer = registry.getOrCompute(TimerUtils.class, () -> new TimerUtils(plugin, dm));
 */
public class ServiceRegistry {

    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    /**
     * Register a service instance.
     */
    public <T> void register(Class<T> type, T instance) {
        services.put(type, instance);
    }

    /**
     * Get a service by type. Returns null if not registered.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        return (T) services.get(type);
    }

    /**
     * Get a service by type, or compute and register it if missing.
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrCompute(Class<T> type, Supplier<T> factory) {
        return (T) services.computeIfAbsent(type, k -> factory.get());
    }

    /**
     * Check if a service is registered.
     */
    public boolean has(Class<?> type) {
        return services.containsKey(type);
    }

    /**
     * Remove a service (for cleanup).
     */
    public <T> void unregister(Class<T> type) {
        services.remove(type);
    }

    /**
     * Clear all services.
     */
    public void clear() {
        services.clear();
    }

    /**
     * Get the number of registered services.
     */
    public int size() {
        return services.size();
    }
}
