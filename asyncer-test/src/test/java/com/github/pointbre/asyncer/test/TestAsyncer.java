package com.github.pointbre.asyncer.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

import com.github.pointbre.asyncer.core.Asyncer;
import com.github.pointbre.asyncer.core.Asyncer.Event;
import com.github.pointbre.asyncer.core.Asyncer.Result;
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

    public static final String DONE_1 = "Done 1";
    public static final long SLEEP_1 = 2000;
    public static final String DONE_2 = "Done 2";
    public static final long SLEEP_2 = 1000;

    public static final List<BiFunction<TestState, TestEvent, Result<Boolean>>> task = new ArrayList<>(
            Arrays.asList(
                    (state, event) -> {
                        try {
                            Thread.sleep(SLEEP_1);
                        } catch (InterruptedException e) {
                            //
                        }
                        return new Result<>(Asyncer.generateType1UUID(), Boolean.TRUE, DONE_1);
                    },
                    (state, event) -> {
                        try {
                            Thread.sleep(SLEEP_2);
                        } catch (InterruptedException e) {
                            //
                        }
                        return new Result<>(Asyncer.generateType1UUID(), Boolean.TRUE, DONE_2);
                    }));
}
