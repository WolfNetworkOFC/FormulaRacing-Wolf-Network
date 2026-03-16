//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Participant;

import java.util.UUID;

public class Subscriber {
    private final UUID uuid;
    private final int eventId;
    private final long subscriptionTime;
    private boolean confirmed;

    public Subscriber(UUID uuid, int eventId) {
        this.uuid = uuid;
        this.eventId = eventId;
        this.subscriptionTime = System.currentTimeMillis();
        this.confirmed = false;
    }

    public UUID getUuid() {
        return this.uuid;
    }

    public int getEventId() {
        return this.eventId;
    }

    public long getSubscriptionTime() {
        return this.subscriptionTime;
    }

    public boolean isConfirmed() {
        return this.confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public String toString() {
        String var10000 = String.valueOf(this.uuid);
        return "Subscriber{uuid=" + var10000 + ", eventId=" + this.eventId + ", confirmed=" + this.confirmed + "}";
    }
}
