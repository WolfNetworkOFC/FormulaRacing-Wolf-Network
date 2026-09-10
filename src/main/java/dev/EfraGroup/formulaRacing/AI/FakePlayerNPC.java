package dev.EfraGroup.formulaRacing.AI;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client-side fake player NPC that rides an AI boat as a passenger.
 *
 * <p>The NPC is not a real server entity: it is created purely with packets
 * (tab-list entry + player spawn + set passengers) so vanilla clients render a
 * player-shaped driver sitting inside the AI boat. Because it is mounted as a
 * passenger, the client keeps it attached to the boat automatically while the
 * boat moves — no per-tick position updates are needed.
 *
 * <p>Implementation notes:
 * <ul>
 *   <li>All NMS classes are accessed via reflection (Mojang mappings) with
 *       fallbacks across 1.19.3 - 1.21.8 constructor signatures.</li>
 *   <li>1.21.5+ removed {@code ClientboundAddPlayerPacket} (players now spawn
 *       via {@code ClientboundAddEntityPacket}) and 1.21.8 moved the chat
 *       session to {@code net.minecraft.network.chat.RemoteChatSession$Data}
 *       ({@code net.minecraft.server.network.ChatSession} no longer exists),
 *       which changed the {@code Entry} constructor to 9 parameters.</li>
 *   <li>On Folia, every packet send is dispatched to the viewer's / boat's
 *       region thread via {@link SchedulerHelper#runTaskFor}.</li>
 *   <li>If reflection fails (future server version), the NPC is skipped and the
 *       AI boat still works normally.</li>
 * </ul>
 */
public class FakePlayerNPC {

    /** Synthetic entity IDs for fake players, allocated from a high range to avoid clashing with real server entity IDs. */
    private static final AtomicInteger ENTITY_ID_COUNTER = new AtomicInteger(Integer.MAX_VALUE - 100_000);

    private final FormulaRacing plugin;
    private final String name;
    private final String worldName;
    private final UUID profileUuid;
    private final int entityId;
    private final int boatEntityId;
    private final UUID boatUuid;
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();

    private boolean warned = false;

    public FakePlayerNPC(FormulaRacing plugin, String name, World world, int boatEntityId, UUID boatUuid) {
        this.plugin = plugin;
        this.name = name;
        this.worldName = world != null ? world.getName() : "";
        this.profileUuid = UUID.randomUUID();
        this.entityId = ENTITY_ID_COUNTER.decrementAndGet();
        this.boatEntityId = boatEntityId;
        this.boatUuid = boatUuid;
    }

    public int getEntityId() {
        return entityId;
    }

    public int getBoatEntityId() {
        return boatEntityId;
    }

    public UUID getProfileUuid() {
        return profileUuid;
    }

    public boolean isInWorld(World world) {
        return world != null && world.getName().equals(worldName);
    }

    public void removeViewer(UUID uuid) {
        viewers.remove(uuid);
    }

    /**
     * Sends the spawn packets (tab-list + player + passengers) to a single
     * viewer. Safe to call from any thread; the packets are sent on the
     * viewer's / boat's region thread.
     */
    public void showTo(Player viewer) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        if (!viewers.add(viewer.getUniqueId())) {
            return; // already shown to this player
        }
        SchedulerHelper.runTaskFor(plugin, viewer, () -> {
            try {
                Object connection = getConnection(viewer);
                Entity boat = Bukkit.getEntity(boatUuid);
                if (boat == null || !boat.isValid()) {
                    viewers.remove(viewer.getUniqueId()); // allow a retry later
                    return;
                }
                // The set-passengers packet references the real boat entity id, so build
                // and send everything on the boat's region thread (Folia).
                SchedulerHelper.runTaskFor(plugin, boat, () -> {
                    // The boat may have been removed between the two scheduled tasks.
                    if (!boat.isValid()) {
                        return;
                    }
                    try {
                        Location loc = boat.getLocation();
                        // Build ALL packets before sending ANY: a failure between sends
                        // would leave the client with a half-spawned NPC (tab entry
                        // without an entity), which corrupts subsequent player_info
                        // payloads and can kick the viewer.
                        Object infoAdd = buildPlayerInfoAdd();
                        Object spawn = buildSpawnPlayer(loc);
                        Object passengers = buildSetPassengers();
                        send(connection, infoAdd);
                        send(connection, spawn);
                        send(connection, passengers);
                    } catch (Exception e) {
                        // Best-effort rollback: drop the tab entry / entity we may
                        // already have sent, and allow a retry later.
                        try {
                            Object conn = getConnection(viewer);
                            send(conn, buildPlayerInfoRemove());
                            send(conn, buildRemoveEntity());
                        } catch (Exception ignored) {
                        }
                        viewers.remove(viewer.getUniqueId());
                        warn(e);
                    }
                });
            } catch (Exception e) {
                warn(e);
            }
        });
    }

    /**
     * Shows this NPC to every online player in the given world.
     */
    public void broadcastShow(World world) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (world != null && !p.getWorld().equals(world)) {
                continue;
            }
            showTo(p);
        }
    }

    /**
     * Sends the removal packets to every online player (optionally restricted
     * to one world) and forgets the tracked viewers.
     */
    public void broadcastHide(World world) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (world != null && !p.getWorld().equals(world)) {
                continue;
            }
            SchedulerHelper.runTaskFor(plugin, p, () -> {
                try {
                    Object connection = getConnection(p);
                    send(connection, buildPlayerInfoRemove());
                    send(connection, buildRemoveEntity());
                } catch (Exception e) {
                    warn(e);
                }
            });
        }
        viewers.clear();
    }

    // ------------------------------------------------------------------
    // Packet construction (reflection, Mojang mappings)
    // ------------------------------------------------------------------

    /** Tab-list entry + ADD_PLAYER action (required since 1.19.3 for skins/names). */
    private Object buildPlayerInfoAdd() throws Exception {
        Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
        Class<?> actionClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");

        Object gameProfile = gameProfile();
        Object gameMode = gameMode();
        Object entry = buildEntry(gameProfile, gameMode);
        Object addAction = Enum.valueOf(asEnum(actionClass), "ADD_PLAYER");

        // Variant A (1.21.8+): (EnumSet<Action>, List<Entry>)
        try {
            Constructor<?> ctor = packetClass.getConstructor(EnumSet.class, List.class);
            List<Object> entries = new ArrayList<>();
            entries.add(entry);
            return ctor.newInstance(EnumSet.of(asEnum(actionClass).cast(addAction)), entries);
        } catch (NoSuchMethodException ignored) {
        }
        // Variant B (1.21.8+): (EnumSet<Action>, Entry)
        try {
            Constructor<?> ctor = packetClass.getConstructor(EnumSet.class, entry.getClass());
            return ctor.newInstance(EnumSet.of(asEnum(actionClass).cast(addAction)), entry);
        } catch (NoSuchMethodException ignored) {
        }
        // Variant C (1.19.3+): (Action, Entry...)
        try {
            Object entries = Array.newInstance(entry.getClass(), 1);
            Array.set(entries, 0, entry);
            Constructor<?> ctor = packetClass.getConstructor(actionClass, entries.getClass());
            return ctor.newInstance(addAction, entries);
        } catch (NoSuchMethodException ignored) {
        }
        // Variant D (1.19.3+): (EnumSet<Action>, Entry...)
        try {
            Object entries = Array.newInstance(entry.getClass(), 1);
            Array.set(entries, 0, entry);
            Constructor<?> ctor = packetClass.getConstructor(EnumSet.class, entries.getClass());
            return ctor.newInstance(EnumSet.of(asEnum(actionClass).cast(addAction)), entries);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("ClientboundPlayerInfoUpdatePacket constructor not found", e);
        }
    }

    private Object buildEntry(Object gameProfile, Object gameMode) throws Exception {
        Class<?> entryClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry");
        Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");

        // Chat session class. 1.21.8+: moved to net.minecraft.network.chat.RemoteChatSession$Data
        // (net.minecraft.server.network.ChatSession no longer exists).
        Class<?> chatSessionClass = firstExisting(
                "net.minecraft.network.chat.RemoteChatSession$Data",
                "net.minecraft.server.network.ChatSession");
        // Remote chat session (pre-1.21.8 Entry variants): RemoteChatSessionData (1.20.5+) /
        // RemoteChatSession (1.19.3-1.20.4). Null on 1.21.8 where it moved to RemoteChatSession$Data.
        Class<?> remoteDataClass = firstExisting(
                "net.minecraft.server.network.RemoteChatSessionData",
                "net.minecraft.server.network.RemoteChatSession");

        // Variant A (1.21.8): (UUID, GameProfile, boolean, int, GameMode, Component, boolean showHat, int listOrder, chatSession)
        if (chatSessionClass != null) {
            try {
                Constructor<?> ctor = entryClass.getConstructor(
                        UUID.class, gameProfile.getClass(), boolean.class, int.class,
                        gameMode.getClass(), componentClass, boolean.class, int.class, chatSessionClass);
                return ctor.newInstance(profileUuid, gameProfile, true, 0, gameMode, null, false, 0, null);
            } catch (Exception ignored) {
                // Variant B (1.21.2+): (int, GameProfile, boolean, int, GameMode, Component, chatSession, remoteData)
            }
        }
        if (chatSessionClass != null && remoteDataClass != null) {
            try {
                Constructor<?> ctor = entryClass.getConstructor(
                        int.class, gameProfile.getClass(), boolean.class, int.class,
                        gameMode.getClass(), componentClass, chatSessionClass, remoteDataClass);
                return ctor.newInstance(0, gameProfile, true, 0, gameMode, null, null, null);
            } catch (Exception ignored) {
                // Variant C (1.20.x): (GameProfile, int, GameMode, Component, chatSession, remoteData)
            }
        }
        if (chatSessionClass != null && remoteDataClass != null) {
            try {
                Constructor<?> ctor = entryClass.getConstructor(
                        gameProfile.getClass(), int.class, gameMode.getClass(),
                        componentClass, chatSessionClass, remoteDataClass);
                return ctor.newInstance(gameProfile, 0, gameMode, null, null, null);
            } catch (Exception ignored) {
                // Variant D (1.19.3): (GameProfile, int, GameMode, Component, chatSession)
            }
        }
        if (chatSessionClass != null) {
            try {
                Constructor<?> ctor = entryClass.getConstructor(
                        gameProfile.getClass(), int.class, gameMode.getClass(),
                        componentClass, chatSessionClass);
                return ctor.newInstance(gameProfile, 0, gameMode, null, null);
            } catch (Exception e) {
                throw new IllegalStateException("ClientboundPlayerInfoUpdatePacket$Entry constructor not found", e);
            }
        }
        throw new IllegalStateException("Chat session class not found on this server version");
    }

    /**
     * Player entity spawn packet.
     *
     * <p>1.21.5+ removed {@code ClientboundAddPlayerPacket}: players are now spawned
     * with {@code ClientboundAddEntityPacket} using {@code EntityType.PLAYER} plus the
     * profile UUID (the client resolves the skin from the tab-list ADD_PLAYER entry).
     */
    private Object buildSpawnPlayer(Location loc) throws Exception {
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        float yaw = loc.getYaw();
        float pitch = loc.getPitch();

        // Variant A (1.21.5+): ClientboundAddEntityPacket(int, UUID, double, double, double,
        // float, float, EntityType<?>, int, Vec3, double)
        //
        // Scan candidate constructor signatures instead of pinning ONE exact
        // signature: the (int,UUID,3×double,2×float,EntityType,int,Vec3,double)
        // form assumed here did NOT exist on 1.21.9+ builds (26.2), so the NPC
        // was silently skipped ("Player spawn packet constructor not found").
        try {
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundAddEntityPacket");
            Class<?> entityTypeClass = Class.forName("net.minecraft.world.entity.EntityType");
            Object playerType = entityTypeClass.getField("PLAYER").get(null);
            Class<?> vec3Class = Class.forName("net.minecraft.world.phys.Vec3");
            Constructor<?> vecCtor = vec3Class.getConstructor(double.class, double.class, double.class);
            Object zeroVec = vecCtor.newInstance(0.0, 0.0, 0.0);
            for (Constructor<?> ctor : packetClass.getConstructors()) {
                Class<?>[] p = ctor.getParameterTypes();
                // Must contain (int, UUID) first-ish and at least one double group;
                // any signature that mixes id+uuid+coords matches this shape.
                boolean hasInt = false, hasUuid = false, hasDouble = false, hasFloat = false, hasEntityType = false;
                for (Class<?> c : p) {
                    if (c == int.class) hasInt = true;
                    else if (c == UUID.class) hasUuid = true;
                    else if (c == double.class) hasDouble = true;
                    else if (c == float.class) hasFloat = true;
                    else if (c == entityTypeClass) hasEntityType = true;
                }
                if (!hasInt || !hasUuid || !hasDouble || !hasEntityType) {
                    continue;
                }
                Object[] args = new Object[p.length];
                boolean idUsed = false, uuidUsed = false, typeUsed = false;
                int yawIdx = -1, pitchIdx = -1, velIdx = -1, headYawIdx = -1;
                for (int i = 0; i < p.length; i++) {
                    Class<?> c = p[i];
                    if (c == int.class && !idUsed) { args[i] = entityId; idUsed = true; }
                    else if (c == UUID.class && !uuidUsed) { args[i] = profileUuid; uuidUsed = true; }
                    else if (c == entityTypeClass && !typeUsed) { args[i] = playerType; typeUsed = true; }
                    else if (c == double.class && yawIdx == -1) { args[i] = x; yawIdx = i; }
                    else if (c == double.class && pitchIdx == -1) { args[i] = y; pitchIdx = i; }
                    else if (c == double.class && velIdx == -1) { args[i] = z; velIdx = i; }
                    else if (c == double.class && headYawIdx == -1) { args[i] = 0.0D; headYawIdx = i; }
                    else if (c == float.class && yawIdx != -1 && pitchIdx == -1) { args[i] = yaw; pitchIdx = i; }
                    else if (c == float.class) { args[i] = pitch; pitchIdx = i; }
                    else if (c == vec3Class && velIdx == -1) { args[i] = zeroVec; velIdx = i; }
                    else if (c == int.class) { args[i] = 0; }
                    else if (c == double.class) { args[i] = (double) yaw; }
                    else { args[i] = null; }
                }
                try {
                    return ctor.newInstance(args);
                } catch (Exception ignored) {
                    // Try the next candidate signature.
                }
            }
        } catch (Exception ignored) {
            // Variant B (1.21.4): ClientboundAddPlayerPacket(int, UUID, Vector3d, float, float)
        }
        try {
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundAddPlayerPacket");
            Class<?> vecClass = Class.forName("org.joml.Vector3d");
            Constructor<?> vecCtor = vecClass.getConstructor(double.class, double.class, double.class);
            Object vec = vecCtor.newInstance(x, y, z);
            Constructor<?> ctor = packetClass.getConstructor(int.class, UUID.class, vecClass, float.class, float.class);
            return ctor.newInstance(entityId, profileUuid, vec, yaw, pitch);
        } catch (Exception ignored) {
            // Variant C (≤1.21.3): ClientboundAddPlayerPacket(int, UUID, double, double, double, float, float)
        }
        try {
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundAddPlayerPacket");
            Constructor<?> ctor = packetClass.getConstructor(
                    int.class, UUID.class, double.class, double.class, double.class, float.class, float.class);
            return ctor.newInstance(entityId, profileUuid, x, y, z, yaw, pitch);
        } catch (Exception e) {
            throw new IllegalStateException("Player spawn packet constructor not found", e);
        }
    }

    /**
     * Mounts the NPC as a passenger of the AI boat.
     *
     * <p>1.21.8 removed the {@code (int, int[])} constructor: the only public constructor
     * is {@code (Entity)}, which serializes the boat's REAL passengers (empty for the AI
     * boat, which would unmount the fake player). The private {@code (FriendlyByteBuf)}
     * decode constructor still exists, so we hand-encode the byte layout
     * (vehicle VarInt + passengers VarInt array) and build the packet through it.
     */
    private Object buildSetPassengers() throws Exception {
        Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetPassengersPacket");
        try {
            Class<?> bufClass = Class.forName("net.minecraft.network.FriendlyByteBuf");
            Class<?> unpooledClass = Class.forName("io.netty.buffer.Unpooled");
            Class<?> byteBufClass = Class.forName("io.netty.buffer.ByteBuf");
            Object byteBuf = unpooledClass.getMethod("buffer").invoke(null);
            Constructor<?> bufCtor = bufClass.getConstructor(byteBufClass);
            Object buf = bufCtor.newInstance(byteBuf);
            bufClass.getMethod("writeVarInt", int.class).invoke(buf, boatEntityId);
            bufClass.getMethod("writeVarIntArray", int[].class).invoke(buf, new int[]{entityId});
            Constructor<?> ctor = packetClass.getDeclaredConstructor(bufClass);
            ctor.setAccessible(true);
            return ctor.newInstance(buf);
        } catch (Exception ignored) {
            // Older: (int, int[])
        }
        try {
            Constructor<?> ctor = packetClass.getConstructor(int.class, int[].class);
            return ctor.newInstance(boatEntityId, new int[]{entityId});
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("ClientboundSetPassengersPacket constructor not found", e);
        }
    }

    /** Removes the NPC from the viewer's tab list. */
    private Object buildPlayerInfoRemove() throws Exception {
        Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
        // 1.21.8+: (List<UUID>)
        try {
            List<UUID> ids = new ArrayList<>();
            ids.add(profileUuid);
            return packetClass.getConstructor(List.class).newInstance(ids);
        } catch (NoSuchMethodException ignored) {
            // Older: (Set<UUID>)
        }
        try {
            Set<UUID> ids = new HashSet<>();
            ids.add(profileUuid);
            return packetClass.getConstructor(Set.class).newInstance(ids);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("ClientboundPlayerInfoRemovePacket constructor not found", e);
        }
    }

    /** Despawns the NPC entity for the viewer. */
    private Object buildRemoveEntity() throws Exception {
        Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket");
        try {
            Constructor<?> ctor = packetClass.getConstructor(int[].class);
            return ctor.newInstance((Object) new int[]{entityId});
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("ClientboundRemoveEntitiesPacket constructor not found", e);
        }
    }

    private Object gameProfile() throws Exception {
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        // Vanilla caps tab-list usernames at 16 chars. A longer name fails to
        // ENCODE on the netty thread (uncatchable) and kicks the viewer client:
        // "String too big (was 18 characters, max 16)".
        String safeName = name.length() > 16 ? name.substring(0, 16) : name;
        Constructor<?> ctor = profileClass.getConstructor(UUID.class, String.class);
        return ctor.newInstance(profileUuid, safeName);
    }

    private Object gameMode() throws Exception {
        for (String candidate : new String[]{
                "net.minecraft.world.level.GameType",
                "net.minecraft.world.level.GameMode"}) {
            try {
                Class<?> clazz = Class.forName(candidate);
                return Enum.valueOf(asEnum(clazz), "SURVIVAL");
            } catch (Exception ignored) {
            }
        }
        throw new IllegalStateException("GameMode enum not found");
    }

    /** Returns the first class that exists on this server version, or null if none do. */
    private static Class<?> firstExisting(String... classNames) {
        for (String name : classNames) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Class<? extends Enum> asEnum(Class<?> clazz) {
        return (Class<? extends Enum>) clazz;
    }

    // ------------------------------------------------------------------
    // Packet sending (reflection on the player connection)
    // ------------------------------------------------------------------

    private static Object getConnection(Player player) throws Exception {
        Object handle = player.getClass().getMethod("getHandle").invoke(player);
        try {
            Field connectionField = handle.getClass().getField("connection");
            return connectionField.get(handle);
        } catch (NoSuchFieldException e) {
            return handle.getClass().getMethod("connection").invoke(handle);
        }
    }

    private static void send(Object connection, Object packet) throws Exception {
        Class<?> packetInterface = Class.forName("net.minecraft.network.protocol.Packet");
        Method send = connection.getClass().getMethod("send", packetInterface);
        send.invoke(connection, packet);
    }

    private void warn(Exception e) {
        if (!warned) {
            warned = true;
            // Name + length included so a stale-jar situation is obvious in the
            // log: names longer than 16 chars can only come from old code.
            plugin.getLogger().warning("[FormulaRacing] Falha ao criar NPC de IA (player falso no barco): "
                    + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + " (npcName=" + name + ", len=" + name.length() + ")");
            plugin.getLogger().warning("[FormulaRacing] O barco da IA continua funcionando sem o NPC visível.");
        }
    }
}
