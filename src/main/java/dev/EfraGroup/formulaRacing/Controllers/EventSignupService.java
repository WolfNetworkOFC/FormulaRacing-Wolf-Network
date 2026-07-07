package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Event.EventState;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.BoatUtils.OpenBoatUtilsVersion;
import java.util.UUID;
import org.bukkit.entity.Player;

public class EventSignupService {
    private static final UUID DAILY_CREATOR_UUID = new UUID(0L, 0L);
    private final FormulaRacing plugin;

    public EventSignupService(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    public SignupResult signPlayer(Player player, Events event, boolean allowAdminBypassWhenClosed) {
        if (event == null) {
            return SignupResult.noEvent();
        }

        UUID playerUUID = player.getUniqueId();

        if (this.plugin.getDatabaseManager().trackHaveBoatUtils(event.getTrackNameWS())) {
            if (!FormulaRacing.hasOpenBoatUtilsMod(player)) {
                this.plugin.sendMessage(player, "obu_mandatory_warning", new String[]{"{track}", event.getTrackNameWS()});
                return SignupResult.obuRequired();
            }
            int minVersion = this.plugin.getDatabaseManager().getTrackMinObuVersion(event.getTrackNameWS());
            if (!OpenBoatUtilsVersion.hasMinVersion(playerUUID, minVersion)) {
                this.plugin.sendMessage(player, "obu_version_warning", new String[]{"{track}", event.getTrackNameWS(), "{required}", String.valueOf(minVersion), "{current}", String.valueOf(OpenBoatUtilsVersion.getPlayerVersion(playerUUID))});
                return SignupResult.obuVersionMismatch();
            }
        }

        if (event.isSubscriber(playerUUID)) {
            if (DAILY_CREATOR_UUID.equals(event.getCreatorUUID())) {
                return SignupResult.alreadySubscribedDaily();
            }

            return SignupResult.alreadySubscribed();
        }

        boolean canBypassClosedSign = allowAdminBypassWhenClosed && player.hasPermission("formularacing.event.admin");
        if (!event.isOpenSign() && !canBypassClosedSign) {
            return SignupResult.signClosed();
        }

        if (event.getState() == EventState.FINISHED) {
            return SignupResult.finished();
        }

        boolean movedFromReserve = false;
        if (event.isReserve(playerUUID)) {
            event.removeReserve(playerUUID);
            movedFromReserve = true;
        }

        if (!event.addSubscriber(playerUUID)) {
            return SignupResult.error();
        }

        return SignupResult.signed(movedFromReserve);
    }

    public enum Status {
        NO_EVENT,
        ALREADY_SUBSCRIBED,
        ALREADY_SUBSCRIBED_DAILY,
        SIGN_CLOSED,
        FINISHED,
        SIGNED,
        ERROR,
        OBU_REQUIRED,
        OBU_VERSION_MISMATCH
    }

    public static final class SignupResult {
        private final Status status;
        private final boolean movedFromReserve;

        private SignupResult(Status status, boolean movedFromReserve) {
            this.status = status;
            this.movedFromReserve = movedFromReserve;
        }

        public static SignupResult noEvent() {
            return new SignupResult(Status.NO_EVENT, false);
        }

        public static SignupResult alreadySubscribed() {
            return new SignupResult(Status.ALREADY_SUBSCRIBED, false);
        }

        public static SignupResult alreadySubscribedDaily() {
            return new SignupResult(Status.ALREADY_SUBSCRIBED_DAILY, false);
        }

        public static SignupResult signClosed() {
            return new SignupResult(Status.SIGN_CLOSED, false);
        }

        public static SignupResult finished() {
            return new SignupResult(Status.FINISHED, false);
        }

        public static SignupResult signed(boolean movedFromReserve) {
            return new SignupResult(Status.SIGNED, movedFromReserve);
        }

        public static SignupResult error() {
            return new SignupResult(Status.ERROR, false);
        }

        public static SignupResult obuRequired() {
            return new SignupResult(Status.OBU_REQUIRED, false);
        }

        public static SignupResult obuVersionMismatch() {
            return new SignupResult(Status.OBU_VERSION_MISMATCH, false);
        }

        public Status getStatus() {
            return this.status;
        }

        public boolean isMovedFromReserve() {
            return this.movedFromReserve;
        }
    }
}
