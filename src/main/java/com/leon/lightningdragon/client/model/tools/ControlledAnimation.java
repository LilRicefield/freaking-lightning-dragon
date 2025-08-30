package com.leon.lightningdragon.client.model.tools;

public class ControlledAnimation {
    private int timer;
    private final int duration;

    public ControlledAnimation(int duration) {
        this.duration = duration;
        this.timer = 0;
    }

    public void increaseTimer() {
        if (timer < duration) {
            timer++;
        }
    }

    public void decreaseTimer() {
        if (timer > 0) {
            timer--;
        }
    }

    public int getTimer() {
        return timer;
    }

    public int getDuration() {
        return duration;
    }

    public void setTimer(int timer) {
        this.timer = Math.max(0, Math.min(duration, timer));
    }
}