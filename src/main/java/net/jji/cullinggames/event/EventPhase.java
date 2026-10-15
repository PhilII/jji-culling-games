package net.jji.cullinggames.event;

public enum EventPhase {
    IDLE,
    RUNNING,
    PAUSED;

    public boolean isActive() {
        return this != IDLE;
    }
}
