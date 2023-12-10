package com.github.pointbre.asyncer.test;

import com.github.pointbre.asyncer.core.Asyncer.Event;
import com.github.pointbre.asyncer.core.Asyncer.State;

import lombok.EqualsAndHashCode;
import lombok.Value;

public class TestAsyncer {
    @Value
    @EqualsAndHashCode(callSuper = true)
    public static class TestState extends State<TestState.Type> {

        public enum Type {
            STARTING, STARTED, STOPPING, STOPPED;
        }

        public TestState(Type type) {
            super(type);
        }

        @Override
        public String toString() {
            return this.getType().name();
        }

    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    public static class TestEvent extends Event<TestEvent.Type> {

        public enum Type {
            START, STOP, SEND;
        }

        String message;

        public TestEvent(Type type) {
            this(type, null);
        }

        public TestEvent(Type type, String message) {
            super(type);
            this.message = message;
        }

        @Override
        public String toString() {
            return this.getType().name() + (message == null ? "" : ": " + message);
        }
    }
}
