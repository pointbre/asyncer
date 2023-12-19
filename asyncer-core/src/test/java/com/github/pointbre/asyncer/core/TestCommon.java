package com.github.pointbre.asyncer.core;

/*-
 * #%L
 * asyncer-core
 * %%
 * Copyright (C) 2023 Lucas Kim
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

import com.github.pointbre.asyncer.core.Asyncer.Event;
import com.github.pointbre.asyncer.core.Asyncer.Result;
import com.github.pointbre.asyncer.core.Asyncer.State;

import lombok.EqualsAndHashCode;
import lombok.Value;

public class TestCommon {

    public static final int MAX_WAIT = 2;

    public static final TestState STARTING = new TestState(TestState.Type.STARTING);
    public static final TestState STARTED = new TestState(TestState.Type.STARTED);
    public static final TestState STOPPING = new TestState(TestState.Type.STOPPING);
    public static final TestState STOPPED = new TestState(TestState.Type.STOPPED);

    public static final TestEvent START = new TestEvent(TestEvent.Type.START);
    public static final TestEvent STOP = new TestEvent(TestEvent.Type.STOP);
    public static final TestEvent SEND = new TestEvent(TestEvent.Type.SEND);

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
                            // Same with Thread.sleep(SLEEP_1);
                            Awaitility.await().pollDelay(Duration.ofMillis(SLEEP_1)).until(() -> true);
                        } catch (ConditionTimeoutException e) {
                            //
                        }
                        return new Result<>(Asyncer.generateType1UUID(), Boolean.TRUE, DONE_1);
                    },
                    (state, event) -> {
                        try {
                            // Same with Thread.sleep(SLEEP_2);
                            Awaitility.await().pollDelay(Duration.ofMillis(SLEEP_2)).until(() -> true);
                        } catch (ConditionTimeoutException e) {
                            //
                        }
                        return new Result<>(Asyncer.generateType1UUID(), Boolean.TRUE, DONE_2);
                    }));
}
