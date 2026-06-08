//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.Database.EventsDatabaseManager;
import dev.EfraGroup.formulaRacing.Event.EventAnnouncements;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.CollisionMode;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Round.RoundType;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class RaceEventManager {

    private final FormulaRacing plugin;
    private final EventsDatabaseManager dbManager;
    private final QualificationManager qualificationManager;
    private final Map<Integer, Events> activeEvents;
    private final Map<String, Events> eventsByName;
    private final Map<UUID, Events> playerActiveEvent;

    public RaceEventManager(FormulaRacing plugin) {
        this.plugin = plugin;
        this.dbManager = new EventsDatabaseManager(plugin.dm, plugin);
        this.qualificationManager = new QualificationManager(plugin);
        this.activeEvents = new ConcurrentHashMap();
        this.eventsByName = new ConcurrentHashMap();
        this.playerActiveEvent = new ConcurrentHashMap();
    }

    public void loadActiveEventsFromDatabase() {
        List<Events> events = this.dbManager.loadActiveEvents();
        this.plugin.getDebugManager().logRaceSystem(
            "§6========== CARREGAMENTO DE EVENTOS =========="
        );
        this.plugin.getDebugManager().logRaceSystem(
            "§eCarregados " +
                events.size() +
                " eventos ativos do banco de dados"
        );

        for (Events event : events) {
            this.activeEvents.put(event.getId(), event);
            this.eventsByName.put(event.getDisplayName().toLowerCase(), event);
            int roundCount = event.getEventSchedule().getRounds().size();
            DebugManager var10000 = this.plugin.getDebugManager();
            String var10001 = event.getDisplayName();
            var10000.logRaceSystem(
                "§a✓ Evento: " +
                    var10001 +
                    " (ID=" +
                    event.getId() +
                    ", Estado=" +
                    String.valueOf(event.getState()) +
                    ", Rounds=" +
                    roundCount +
                    ")"
            );

            for (Rounds round : event.getEventSchedule().getRounds().values()) {
                int heatCount = round.getHeats().size();
                var10000 = this.plugin.getDebugManager();
                int var9 = round.getRoundNumber();
                var10000.logRaceSystem(
                    "  §7- Round " +
                        var9 +
                        " (" +
                        String.valueOf(round.getType()) +
                        ", Heats=" +
                        heatCount +
                        ")"
                );
            }
        }

        this.plugin.getDebugManager().logRaceSystem(
            "§6============================================="
        );
    }

    public CompletableFuture<Object> createQuickRace(
        UUID creatorUUID,
        String eventName,
        String trackNameWS,
        int laps,
        int pits
    ) {
        TrackIntegrationManager trackManager =
            this.plugin.getTrackIntegrationManager();
        TrackIntegrationManager.TrackValidationResult validation =
            trackManager.validateTrack(trackNameWS);
        if (!validation.isValid()) {
            this.plugin.getDebugManager().logRaceSystem(
                "Falha ao criar QuickRace: " + validation.getMessage()
            );
            return CompletableFuture.completedFuture(null);
        } else {
            this.plugin.getDebugManager().logRaceSystem(
                "Pista validada: " +
                    trackNameWS +
                    " (" +
                    validation.getTrackData().getTotalCheckpoints() +
                    " checkpoints)"
            );
            return this.dbManager.createEvent(
                creatorUUID,
                eventName,
                trackNameWS
            ).thenCompose(eventId -> {
                if (eventId == -1) {
                    this.plugin.getDebugManager().logRaceSystem(
                        "Falha ao criar evento no banco de dados: " + eventName
                    );
                    return CompletableFuture.completedFuture((Object) null);
                } else {
                    Events event = new Events(
                        this.plugin,
                        this,
                        eventId,
                        creatorUUID,
                        eventName
                    );
                    event.setTrackNameWS(trackNameWS);
                    return this.dbManager.createRound(
                        eventId,
                        1,
                        RoundType.FINAL
                    ).thenCompose(roundId -> {
                        if (roundId == -1) {
                            this.plugin.getDebugManager().logRaceSystem(
                                "Falha ao criar round no banco de dados"
                            );
                            return CompletableFuture.completedFuture(event);
                        } else {
                            Rounds round = event
                                .getEventSchedule()
                                .createRound(1, RoundType.FINAL);
                            round.setId(roundId);
                            round.setEventId(eventId);
                            return this.dbManager.createHeat(
                                roundId,
                                1,
                                laps,
                                pits,
                                0,
                                5,
                                1000,
                                false,
                                true,
                                true,
                                false,
                                false,
                                "HIGH",
                                (double) 0.0F,
                                0.052,
                                false,
                                (double) 0.0F,
                                false,
                                (double) 0.0F,
                                false
                            ).thenApply(heatId -> {
                                if (heatId != -1) {
                                    Heats heat = round.createHeat(1);
                                    heat.setId(heatId);
                                    heat.setRoundId(roundId);
                                    heat.setTotalLaps(laps);
                                    heat.setTotalPits(pits);
                                    heat.setTrackNameWS(trackNameWS);
                                    heat.setDrsEnabled(true);
                                    heat.setPushtopass(false);
                                } else {
                                    this.plugin.getDebugManager().logRaceSystem(
                                        "Falha ao criar heat no banco de dados"
                                    );
                                }

                                this.activeEvents.put(eventId, event);
                                this.eventsByName.put(
                                    eventName.toLowerCase(),
                                    event
                                );
                                this.plugin.getDebugManager().logRaceSystem(
                                    "QuickRace criado e salvo: " +
                                        eventName +
                                        " (ID=" +
                                        eventId +
                                        ") na pista " +
                                        trackNameWS
                                );
                                return event;
                            });
                        }
                    });
                }
            });
        }
    }

    public CompletableFuture<Object> createDailyEvent(
        UUID creatorUUID,
        String eventName,
        String trackNameWS,
        int practiceTimeLimit,
        int qualLaps,
        int qualTimeLimit,
        int finalLaps,
        int pits
    ) {
        TrackIntegrationManager trackManager =
            this.plugin.getTrackIntegrationManager();
        TrackIntegrationManager.TrackValidationResult validation =
            trackManager.validateTrack(trackNameWS);
        if (!validation.isValid()) {
            this.plugin.getDebugManager().logRaceSystem(
                "Falha ao criar DailyEvent: " + validation.getMessage()
            );
            return CompletableFuture.completedFuture(null);
        } else {
            return this.dbManager.createEvent(
                creatorUUID,
                eventName,
                trackNameWS
            ).thenCompose(eventId -> {
                if (eventId == -1) {
                    return CompletableFuture.completedFuture((Object) null);
                } else {
                    Events event = new Events(
                        this.plugin,
                        this,
                        eventId,
                        creatorUUID,
                        eventName
                    );
                    event.setTrackNameWS(trackNameWS);
                    return this.dbManager.createRound(
                        eventId,
                        1,
                        RoundType.PRACTICE
                    ).thenCompose(practiceRoundId -> {
                        if (practiceRoundId == -1) {
                            return CompletableFuture.completedFuture(event);
                        } else {
                            Rounds practiceRound = event
                                .getEventSchedule()
                                .createRound(1, RoundType.PRACTICE);
                            practiceRound.setId(practiceRoundId);
                            practiceRound.setEventId(eventId);
                            return this.dbManager.createHeat(
                                practiceRoundId,
                                1,
                                0,
                                0,
                                practiceTimeLimit,
                                0,
                                -1,
                                true,
                                true,
                                false,
                                false,
                                false,
                                "DISABLED",
                                (double) 0.0F,
                                0.052,
                                false,
                                (double) 0.0F,
                                false,
                                (double) 0.0F,
                                false
                            ).thenCompose(practiceHeatId -> {
                                if (practiceHeatId != -1) {
                                    Heats practiceHeat =
                                        practiceRound.createHeat(1);
                                    practiceHeat.setId(practiceHeatId);
                                    practiceHeat.setRoundId(practiceRoundId);
                                    practiceHeat.setTotalLaps(0);
                                    practiceHeat.setTimeLimit(
                                        practiceTimeLimit
                                    );
                                    practiceHeat.setLonely(true);
                                    practiceHeat.setTrackNameWS(trackNameWS);
                                    practiceHeat.setCollisionMode(
                                        CollisionMode.DISABLED
                                    );
                                }

                                return this.dbManager.createRound(
                                    eventId,
                                    2,
                                    RoundType.QUALIFICATION
                                ).thenCompose(qualRoundId -> {
                                    if (qualRoundId == -1) {
                                        return CompletableFuture.completedFuture(
                                            event
                                        );
                                    } else {
                                        Rounds qualRound = event
                                            .getEventSchedule()
                                            .createRound(
                                                2,
                                                RoundType.QUALIFICATION
                                            );
                                        qualRound.setId(qualRoundId);
                                        qualRound.setEventId(eventId);
                                        return this.dbManager.createHeat(
                                            qualRoundId,
                                            1,
                                            0,
                                            0,
                                            qualTimeLimit,
                                            0,
                                            -1,
                                            true,
                                            false,
                                            true,
                                            false,
                                            false,
                                            "disabled",
                                            (double) 0.0F,
                                            0.052,
                                            false,
                                            (double) 0.0F,
                                            false,
                                            (double) 0.0F,
                                            false
                                        ).thenCompose(qualHeatId -> {
                                            if (qualHeatId != -1) {
                                                Heats qualHeat =
                                                    qualRound.createHeat(1);
                                                qualHeat.setId(qualHeatId);
                                                qualHeat.setRoundId(
                                                    qualRoundId
                                                );
                                                qualHeat.setTotalLaps(qualLaps);
                                                qualHeat.setTimeLimit(
                                                    qualTimeLimit
                                                );
                                                qualHeat.setLonely(true);
                                                qualHeat.setTrackNameWS(
                                                    trackNameWS
                                                );
                                            }

                                            return this.dbManager.createRound(
                                                eventId,
                                                3,
                                                RoundType.FINAL
                                            ).thenCompose(finalRoundId -> {
                                                if (finalRoundId == -1) {
                                                    return CompletableFuture.completedFuture(
                                                        event
                                                    );
                                                } else {
                                                    Rounds finalRound = event
                                                        .getEventSchedule()
                                                        .createRound(
                                                            3,
                                                            RoundType.FINAL
                                                        );
                                                    finalRound.setId(
                                                        finalRoundId
                                                    );
                                                    finalRound.setEventId(
                                                        eventId
                                                    );
                                                    return this.dbManager.createHeat(
                                                        finalRoundId,
                                                        1,
                                                        finalLaps,
                                                        pits,
                                                        0,
                                                        5,
                                                        1000,
                                                        false,
                                                        true,
                                                        true,
                                                        true,
                                                        false,
                                                        "HIGH",
                                                        (double) 0.0F,
                                                        0.052,
                                                        false,
                                                        (double) 0.0F,
                                                        false,
                                                        (double) 0.0F,
                                                        false
                                                    ).thenApply(finalHeatId -> {
                                                        if (finalHeatId != -1) {
                                                            Heats finalHeat =
                                                                finalRound.createHeat(
                                                                    1
                                                                );
                                                            finalHeat.setId(
                                                                finalHeatId
                                                            );
                                                            finalHeat.setRoundId(
                                                                finalRoundId
                                                            );
                                                            finalHeat.setTotalLaps(
                                                                finalLaps
                                                            );
                                                            finalHeat.setTotalPits(
                                                                pits
                                                            );
                                                            finalHeat.setTrackNameWS(
                                                                trackNameWS
                                                            );
                                                            finalHeat.setDrsEnabled(
                                                                true
                                                            );
                                                            finalHeat.setCollisionMode(
                                                                CollisionMode.HIGH
                                                            );
                                                        } else {
                                                            this.plugin.getDebugManager().logRaceSystem(
                                                                "Erro: Falha ao gerar HeatID no banco."
                                                            );
                                                        }

                                                        this.activeEvents.put(
                                                            eventId,
                                                            event
                                                        );
                                                        this.eventsByName.put(
                                                            eventName.toLowerCase(),
                                                            event
                                                        );
                                                        this.plugin.getDebugManager().logRaceSystem(
                                                            "QuickRace '" +
                                                                eventName +
                                                                "' (ID=" +
                                                                eventId +
                                                                ") criado com sucesso!"
                                                        );
                                                        return event;
                                                    });
                                                }
                                            });
                                        });
                                    }
                                });
                            });
                        }
                    });
                }
            });
        }
    }

    public CompletableFuture<Events> createFullEvent(
        UUID creatorUUID,
        String eventName,
        String trackNameWS,
        int practiceTimeLimit,
        int qualLaps,
        int qualTimeLimit,
        int finalLaps,
        int pits
    ) {
        TrackIntegrationManager trackManager =
            this.plugin.getTrackIntegrationManager();
        TrackIntegrationManager.TrackValidationResult validation =
            trackManager.validateTrack(trackNameWS);

        if (!validation.isValid()) {
            this.plugin.getDebugManager().logRaceSystem(
                "Falha ao criar FullEvent: " + validation.getMessage()
            );
            // Forçamos o tipo genérico <Events> para evitar conflito de inferência
            return CompletableFuture.completedFuture(null);
        }

        this.plugin.getDebugManager().logRaceSystem(
            "Pista validada: " + trackNameWS
        );

        return this.dbManager.createEvent(
            creatorUUID,
            eventName,
            trackNameWS
        ).thenCompose(eventId -> {
            if (eventId == -1) {
                this.plugin.getDebugManager().logDatabaseOperation(
                    "Falha ao criar evento no banco: " + eventName
                );
                return CompletableFuture.completedFuture(null);
            }

            final Events event = new Events(
                this.plugin,
                this,
                eventId,
                creatorUUID,
                eventName
            );
            event.setTrackNameWS(trackNameWS);
            AtomicInteger nextRoundIndex = new AtomicInteger(1);

            // --- CADEIA DE PRACTICE ---
            CompletableFuture<Void> practiceFuture =
                CompletableFuture.completedFuture(null);
            if (practiceTimeLimit > 0) {
                int pIndex = nextRoundIndex.getAndIncrement();
                practiceFuture = this.dbManager.createRound(
                    eventId,
                    pIndex,
                    RoundType.PRACTICE
                ).thenCompose(pRoundId -> {
                    if (
                        pRoundId == -1
                    ) return CompletableFuture.completedFuture(null);

                    Rounds pRound = event
                        .getEventSchedule()
                        .createRound(pIndex, RoundType.PRACTICE);
                    pRound.setId(pRoundId);
                    pRound.setEventId(eventId);

                    return this.dbManager.createHeat(
                        pRoundId,
                        1,
                        0,
                        0,
                        0,
                        5,
                        1000,
                        false,
                        true,
                        true,
                        true,
                        false,
                        "HIGH",
                        0.0,
                        0.052,
                        false,
                        0.0,
                        false,
                        0.0,
                        false
                    ).thenAccept(pHeatId -> {
                        if (pHeatId != -1) {
                            Heats heat = pRound.createHeat(1);
                            heat.setId(pHeatId);
                            heat.setRoundId(pRoundId);
                            heat.setTimeLimit(practiceTimeLimit);
                            heat.setLonely(true);
                            heat.setTrackNameWS(trackNameWS);
                        }
                    });
                });
            }

            // --- CADEIA DE QUALY E FINAL ---
            return practiceFuture.thenCompose(v -> {
                int qIndex = nextRoundIndex.getAndIncrement();
                return this.dbManager.createRound(
                    eventId,
                    qIndex,
                    RoundType.QUALIFICATION
                ).thenCompose(qRoundId -> {
                    if (
                        qRoundId == -1
                    ) return CompletableFuture.completedFuture(event);

                    Rounds qRound = event
                        .getEventSchedule()
                        .createRound(qIndex, RoundType.QUALIFICATION);
                    qRound.setId(qRoundId);

                    return this.dbManager.createHeat(
                        qRoundId,
                        1,
                        qualLaps,
                        0,
                        0,
                        5,
                        1000,
                        false,
                        true,
                        true,
                        true,
                        false,
                        "HIGH",
                        0.0,
                        0.052,
                        false,
                        0.0,
                        false,
                        0.0,
                        false
                    ).thenCompose(qHeatId -> {
                        if (qHeatId != -1) {
                            Heats qHeat = qRound.createHeat(1);
                            qHeat.setId(qHeatId);
                            qHeat.setTotalLaps(qualLaps);
                            qHeat.setTimeLimit(qualTimeLimit);
                            qHeat.setLonely(true);
                            qHeat.setTrackNameWS(trackNameWS);
                        }

                        int fIndex = nextRoundIndex.getAndIncrement();
                        return this.dbManager.createRound(
                            eventId,
                            fIndex,
                            RoundType.FINAL
                        ).thenCompose(fRoundId -> {
                            if (
                                fRoundId == -1
                            ) return CompletableFuture.completedFuture(event);

                            Rounds fRound = event
                                .getEventSchedule()
                                .createRound(fIndex, RoundType.FINAL);
                            fRound.setId(fRoundId);

                            return this.dbManager.createHeat(
                                fRoundId,
                                1,
                                finalLaps,
                                pits,
                                0,
                                5,
                                -1,
                                false,
                                false,
                                false,
                                false,
                                false,
                                "HIGH",
                                0.0,
                                0.052,
                                false,
                                0.0,
                                false,
                                0.0,
                                false
                            ).thenApply(fHeatId -> {
                                if (fHeatId != -1) {
                                    Heats fHeat = fRound.createHeat(1);
                                    fHeat.setId(fHeatId);
                                    fHeat.setTotalLaps(finalLaps);
                                    fHeat.setTotalPits(pits);
                                    fHeat.setTrackNameWS(trackNameWS);
                                }

                                this.activeEvents.put(eventId, event);
                                this.eventsByName.put(
                                    eventName.toLowerCase(),
                                    event
                                );
                                return event;
                            });
                        });
                    });
                });
            });
        });
    }

    public Events loadEvent(int eventId) {
        if (this.activeEvents.containsKey(eventId)) {
            return (Events) this.activeEvents.get(eventId);
        } else {
            Events event = this.dbManager.loadEvent(eventId);
            if (event != null) {
                this.activeEvents.put(eventId, event);
                this.eventsByName.put(
                    event.getDisplayName().toLowerCase(),
                    event
                );
                DebugManager var10000 = this.plugin.getDebugManager();
                String var10001 = event.getDisplayName();
                var10000.logRaceSystem(
                    "Evento carregado do banco: " +
                        var10001 +
                        " (ID=" +
                        eventId +
                        ")"
                );
            }

            return event;
        }
    }

    public Optional<Events> getEventByName(String eventName) {
        return Optional.ofNullable(
            (Events) this.eventsByName.get(eventName.toLowerCase())
        );
    }

    public Optional<Heats> getHeat(int heatId) {
        for (Events event : this.activeEvents.values()) {
            for (Rounds round : event.getEventSchedule().getRounds().values()) {
                for (Heats heat : round.getHeats().values()) {
                    if (heat.getId() == heatId) {
                        return Optional.of(heat);
                    }
                }
            }
        }

        return Optional.empty();
    }

    public Optional<Events> getPlayerEvent(UUID playerUUID) {
        Events cached = (Events) this.playerActiveEvent.get(playerUUID);
        if (cached != null) {
            return Optional.of(cached);
        } else {
            for (Events event : this.activeEvents.values()) {
                if (event.isSubscriber(playerUUID)) {
                    this.playerActiveEvent.put(playerUUID, event);
                    return Optional.of(event);
                }
            }

            return Optional.empty();
        }
    }

    public Optional<Heats> getPlayerActiveHeat(UUID playerUUID) {
        Optional<Events> eventOpt = this.getPlayerEvent(playerUUID);
        if (eventOpt.isPresent()) {
            Events event = (Events) eventOpt.get();

            for (Rounds round : event.getEventSchedule().getRounds().values()) {
                for (Heats heat : round.getHeats().values()) {
                    if (heat.isPlayerInActiveHeat(playerUUID)) {
                        return Optional.of(heat);
                    }
                }
            }
        }

        for (Events e : this.activeEvents.values()) {
            for (Rounds round : e.getEventSchedule().getRounds().values()) {
                for (Heats heat : round.getHeats().values()) {
                    if (heat.isPlayerInActiveHeat(playerUUID)) {
                        this.playerActiveEvent.put(playerUUID, e);
                        return Optional.of(heat);
                    }
                }
            }
        }

        return Optional.empty();
    }

    public boolean addPlayerToEvent(UUID playerUUID, int eventId) {
        Events event = (Events) this.activeEvents.get(eventId);
        if (event == null) {
            return false;
        } else if (event.addSubscriber(playerUUID)) {
            this.playerActiveEvent.put(playerUUID, event);
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                this.plugin.checkAndWarnOBU(player, event.getTrackNameWS());
            }

            return true;
        } else {
            return false;
        }
    }

    public boolean removePlayerFromEvent(UUID playerUUID) {
        Events event = (Events) this.playerActiveEvent.remove(playerUUID);
        if (event != null) {
            event.removeSubscriber(playerUUID);
            return true;
        } else {
            return false;
        }
    }

    public boolean leaveEvent(Player player) {
        UUID uuid = player.getUniqueId();
        this.plugin.getLonelyController().clearGhost(uuid);
        if (
            this.plugin.getQuickRaceManager() != null &&
            this.plugin.getQuickRaceManager().isPlayerInActiveRace(uuid)
        ) {
            return this.plugin.getQuickRaceManager().removePlayer(player);
        } else {
            Optional<Events> eventOpt = this.getPlayerEvent(uuid);
            if (!eventOpt.isPresent()) {
                return false;
            } else {
                Events event = (Events) eventOpt.get();
                Optional<Heats> heatOpt = this.getPlayerActiveHeat(uuid);
                if (heatOpt.isPresent()) {
                    Heats heat = (Heats) heatOpt.get();
                    Driver driver = heat.getDriver(uuid);
                    if (driver != null) {
                        boolean isRacing =
                            heat.getHeatState() == HeatState.RACING ||
                            heat.getHeatState() == HeatState.STARTING;
                        if (
                            isRacing && !driver.isFinished() && !driver.isDnf()
                        ) {
                            driver.setDnf(true);
                            if (this.plugin.getPTP() != null) {
                                this.plugin.getPTP().disablePTP(player, driver);
                            }
                            EventAnnouncements announcements =
                                heat.getRound() != null &&
                                heat.getRound().getEvent() != null
                                    ? heat
                                          .getRound()
                                          .getEvent()
                                          .getAnnouncements()
                                    : this.plugin.getEventAnnouncements();
                            announcements.broadcastDNF(
                                heat,
                                driver,
                                "Left event"
                            );
                            heat.removeDriver(uuid);
                            player.sendMessage(
                                "§e⚠ Você saiu da corrida e foi desqualificado."
                            );
                        } else if (!isRacing) {
                            heat.removeDriver(uuid);
                            player.sendMessage("§e⚠ Você saiu da corrida.");
                        }
                    }
                } else {
                    for (Rounds round : event
                        .getEventSchedule()
                        .getRounds()
                        .values()) {
                        for (Heats h : round.getHeats().values()) {
                            if (h.getDriver(uuid) != null) {
                                DebugManager var10000 =
                                    this.plugin.getDebugManager();
                                String var10001 = player.getName();
                                var10000.logRaceSystem(
                                    "Fallback: Removendo ghost driver " +
                                        var10001 +
                                        " do Heat " +
                                        h.getId()
                                );
                                h.removeDriver(uuid);
                            }
                        }
                    }
                }

                if (this.plugin.getRaceActionBarManager() != null) {
                    this.plugin.getRaceActionBarManager().removePlayer(player);
                }

                if (this.plugin.getRaceScoreboardManager() != null) {
                    this.plugin.getRaceScoreboardManager().removePlayer(player);
                }

                if (this.plugin.getTimerUtils() != null) {
                    this.plugin.getTimerUtils().stopTimer(player);
                }

                if (this.plugin.getPacketSender() != null) {
                    this.plugin.getPacketSender().resetBoatUtilsToVanilla(
                        player
                    );
                    boolean dbLonely =
                        this.plugin.getDatabaseManager().getLonelyModePlayer(
                            uuid
                        );
                    this.plugin.getLonelyController().setLonelyMode(
                        player,
                        dbLonely
                    );
                }

                this.plugin.getAPI().recoverPlayerBoatState(player);
                if (player.getVehicle() != null) {
                    player.getVehicle().remove();
                }

                Location respawn = player.getRespawnLocation();
                if (respawn == null) {
                    respawn = player.getWorld().getSpawnLocation();
                }

                player.teleport(respawn);
                event.removeSubscriber(uuid);
                this.playerActiveEvent.remove(uuid);
                player.sendMessage(
                    "§e⚠ Você saiu do evento: §f" + event.getDisplayName()
                );
                return true;
            }
        }
    }

    public boolean removeEvent(int eventId) {
        Events event = (Events) this.activeEvents.remove(eventId);
        if (event != null) {
            this.eventsByName.remove(event.getDisplayName().toLowerCase());
            this.playerActiveEvent.entrySet().removeIf(
                entry -> ((Events) entry.getValue()).getId() == eventId
            );
            this.dbManager.deleteEvent(eventId);
            DebugManager var10000 = this.plugin.getDebugManager();
            String var10001 = event.getDisplayName();
            var10000.logRaceSystem(
                "Evento removido: " + var10001 + " (ID=" + eventId + ")"
            );
            return true;
        } else {
            return false;
        }
    }

    public boolean unloadEvent(int eventId) {
        Events event = (Events) this.activeEvents.remove(eventId);
        if (event == null) {
            return false;
        } else {
            this.eventsByName.remove(event.getDisplayName().toLowerCase());
            this.playerActiveEvent.entrySet().removeIf(
                entry -> ((Events) entry.getValue()).getId() == eventId
            );
            DebugManager var10000 = this.plugin.getDebugManager();
            String var10001 = event.getDisplayName();
            var10000.logRaceSystem(
                "Evento descarregado da memória: " +
                    var10001 +
                    " (ID=" +
                    eventId +
                    ")"
            );
            return true;
        }
    }

    public EventsDatabaseManager getDatabaseManager() {
        return this.dbManager;
    }

    public Optional<Events> getEventById(int eventId) {
        return Optional.ofNullable((Events) this.activeEvents.get(eventId));
    }

    public QualificationManager getQualificationManager() {
        return this.qualificationManager;
    }

    public Collection<Events> getActiveEvents() {
        return this.activeEvents.values();
    }

    public Collection<Events> getAllEvents() {
        return this.getActiveEvents();
    }

    public CompletableFuture<Events> createEvent(
        UUID creatorUUID,
        String eventName,
        String track
    ) {
        return this.dbManager.createEvent(
            creatorUUID,
            eventName,
            track
        ).thenApply(eventId -> {
            if (eventId == -1) {
                this.plugin.getDebugManager().logDatabaseOperation(
                    "Falha ao criar evento no banco de dados: " + eventName
                );
                return null;
            } else {
                Events event = new Events(
                    this.plugin,
                    this,
                    eventId,
                    creatorUUID,
                    eventName
                );
                event.setTrackNameWS(track);
                this.activeEvents.put(eventId, event);
                this.eventsByName.put(eventName.toLowerCase(), event);
                this.plugin.getDebugManager().logRaceSystem(
                    "Evento criado: " + eventName + " (ID=" + eventId + ")"
                );
                return event;
            }
        });
    }

    public CompletableFuture<Rounds> createRound(
        Events event,
        RoundType roundType,
        int roundNumber
    ) {
        return this.dbManager.createRound(
            event.getId(),
            roundNumber,
            roundType
        ).thenApply(roundId -> {
            if (roundId == -1) {
                this.plugin.getDebugManager().logDatabaseOperation(
                    "Falha ao criar round no banco de dados"
                );
                return null;
            } else {
                Rounds round = event
                    .getEventSchedule()
                    .createRound(roundNumber, roundType);
                round.setId(roundId);
                round.setEventId(event.getId());
                this.plugin.getDebugManager().logRaceSystem(
                    "Rodada criada: R" +
                        roundNumber +
                        " (" +
                        String.valueOf(roundType) +
                        ") no evento " +
                        event.getDisplayName()
                );
                return round;
            }
        });
    }

    public CompletableFuture<Heats> createHeat(Rounds round, int heatNumber) {
        int totalLaps;
        int totalPitstops = 0;
        int timeLimit;
        int startDelay;
        int maxDrivers = 1000;
        boolean lonely;
        boolean canReset = true;
        if (round.getType() == RoundType.PRACTICE) {
            totalLaps = 0;
            timeLimit = 15;
            lonely = true;
            startDelay = 0;
        } else if (round.getType() == RoundType.QUALIFICATION) {
            totalLaps = 0;
            timeLimit = 10;
            lonely = true;
            startDelay = 5;
        } else {
            lonely = false;
            startDelay = 5;
            totalLaps = 5;
            timeLimit = 0;
        }

        return this.dbManager.createHeat(
            round.getId(),
            1,
            totalLaps,
            totalPitstops,
            0,
            0,
            -1,
            false,
            false,
            false,
            false,
            false,
            "HIGH",
            (double) 0.0F,
            0.052,
            false,
            (double) 0.0F,
            false,
            (double) 0.0F,
            false
        ).thenApply(heatId -> {
            if (heatId == -1) {
                this.plugin.getDebugManager().logDatabaseOperation(
                    "Falha ao criar heat no banco de dados"
                );
                return null;
            } else {
                Heats heat = round.createHeat(heatNumber);
                heat.setId(heatId);
                heat.setRoundId(round.getId());
                heat.setTotalLaps(totalLaps);
                heat.setTotalPits(totalPitstops);
                heat.setTimeLimit(timeLimit);
                heat.setStartDelay(startDelay);
                heat.setMaxDrivers(maxDrivers);
                heat.setLonely(lonely);
                heat.setCanReset(canReset);
                if (
                    round.getEvent() != null &&
                    round.getEvent().getTrackNameWS() != null
                ) {
                    heat.setTrackNameWS(round.getEvent().getTrackNameWS());
                }

                this.plugin.getDebugManager().logRaceSystem(
                    "Heat criado no round R" + round.getRoundNumber()
                );
                return heat;
            }
        });
    }

    public boolean removeRound(Rounds round) {
        Events event = round.getEvent();
        if (event == null) {
            return false;
        } else {
            if (round.getId() > 0) {
                this.dbManager.deleteRound(round.getId());
            }

            event.getEventSchedule().removeRound(round.getRoundNumber());
            this.plugin.getDebugManager().logRaceSystem(
                "Rodada removida: R" + round.getRoundNumber()
            );
            return true;
        }
    }

    public boolean removeHeat(Heats heat) {
        Rounds round = heat.getRound();
        if (round == null) {
            return false;
        } else {
            if (heat.getId() > 0) {
                this.dbManager.deleteHeat(heat.getId());
            }

            round.removeHeat(heat.getHeatNumber());
            this.plugin.getDebugManager().logRaceSystem(
                "Heat removido do round R" + round.getRoundNumber()
            );
            return true;
        }
    }

    public boolean deleteEvent(int eventId) {
        return this.removeEvent(eventId);
    }

    public void processQualification(Events event, Rounds qualificationRound) {
        this.qualificationManager.processQualificationResults(
            event,
            qualificationRound
        );
    }

    public void shutdown() {
        this.activeEvents.clear();
        this.eventsByName.clear();
        this.playerActiveEvent.clear();
        this.plugin.getDebugManager().logRaceSystem(
            "RaceEventManager desligado."
        );
    }
}
