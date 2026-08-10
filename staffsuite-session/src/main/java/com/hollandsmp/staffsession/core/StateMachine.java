package com.hollandsmp.staffsession.core;

public final class StateMachine {
    public enum State {
        ACTIVE,
        ENDED,
        CRASHED_RECOVERED,
        CORRUPTED
    }
}
