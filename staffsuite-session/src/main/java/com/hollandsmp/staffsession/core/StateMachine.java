package com.hollandsmp.staffsession.core;

public final class StateMachine {
    public enum State {
        ACTIVE,
        ENDED,
        CRASHED_RECOVERED,
        CORRUPTED
    }

    public boolean canTransition(State from, State to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true;
        }
        if (from == State.ACTIVE && (to == State.ENDED || to == State.CRASHED_RECOVERED || to == State.CORRUPTED)) {
            return true;
        }
        return false;
    }

    public State transition(State from, State to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Invalid state transition: " + from + " -> " + to);
        }
        return to;
    }
}
