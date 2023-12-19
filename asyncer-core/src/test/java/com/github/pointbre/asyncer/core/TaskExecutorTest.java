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

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.pointbre.asyncer.core.Asyncer.Result;
import com.github.pointbre.asyncer.core.Asyncer.TaskExecutor;
import com.github.pointbre.asyncer.core.TestCommon.TestEvent;
import com.github.pointbre.asyncer.core.TestCommon.TestState;

@ExtendWith(MockitoExtension.class)
class TaskExecutorTest {

    private static Stream<Arguments> taskExecutors() {
        return Stream.of(
                Arguments.of("ParallelFAETaskExecutorImpl", new ParallelFAETaskExecutorImpl<>()),
                Arguments.of("SequentialFAETaskExecutorImpl", new SequentialFAETaskExecutorImpl<>()));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("taskExecutors")
    void shouldContinueToExecuteTasksWhenExceptionOccurs(String name,
            TaskExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> taskExecutor) throws Exception {
        List<BiFunction<TestState, TestEvent, Result<Boolean>>> task1 = new ArrayList<>(
                Arrays.asList(
                        (state, event) -> {
                            throw new RuntimeException();
                        },
                        (state, event) -> {
                            try {
                                Awaitility.await().pollDelay(Duration.ofMillis(100)).until(() -> true);
                            } catch (ConditionTimeoutException e) {
                                fail(e.getLocalizedMessage());
                            }
                            return new Result<>(Asyncer.generateType1UUID(), Boolean.TRUE, TestCommon.DONE_1);
                        }));

        try (taskExecutor) {
            List<Result<Boolean>> results = taskExecutor.run(TestCommon.STOPPED, TestCommon.START, task1, null);

            assertEquals(2, results.size());
            assertFalse(results.get(0).getValue());
            assertTrue(results.get(0).getDescription().startsWith(TaskExecutor.TASK_EXCEPTION));
            assertTrue(results.get(1).getValue());
            assertTrue(results.get(1).getDescription().startsWith(TestCommon.DONE_1));
        }
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("taskExecutors")
    void shouldReturnFalseWhenTaskReturnsNull(String name,
            TaskExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> taskExecutor) throws Exception {
        List<BiFunction<TestState, TestEvent, Result<Boolean>>> task = new ArrayList<>(
                Arrays.asList(
                        (state, event) -> {
                            return null;
                        }));

        List<Result<Boolean>> results = taskExecutor.run(TestCommon.STOPPED, TestCommon.START, task, null);
        assertEquals(1, results.size());
        assertFalse(results.get(0).getValue());
        assertTrue(results.get(0).getDescription().startsWith(TaskExecutor.TASK_NULL_RESULT));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("taskExecutors")
    void shouldReturnNoResultWhenEmptyTaskListIsProvided(String name,
            TaskExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> taskExecutor) throws Exception {
        List<BiFunction<TestState, TestEvent, Result<Boolean>>> task = new ArrayList<>(
                Arrays.asList());

        List<Result<Boolean>> results = taskExecutor.run(TestCommon.STOPPED, TestCommon.START, task, null);
        assertEquals(0, results.size());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("taskExecutors")
    void shouldReturnNoResultWhenNullTaskIsProvided(String name,
            TaskExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> taskExecutor) throws Exception {
        List<BiFunction<TestState, TestEvent, Result<Boolean>>> task = new ArrayList<>(
                Arrays.asList((BiFunction<TestState, TestEvent, Result<Boolean>>) null));

        List<Result<Boolean>> results = taskExecutor.run(TestCommon.STOPPED, TestCommon.START, task, null);
        assertEquals(0, results.size());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("taskExecutors")
    void shouldReturnFalseWhenAnyOfParameterIsNull(String name,
            TaskExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> taskExecutor) throws Exception {
        List<BiFunction<TestState, TestEvent, Result<Boolean>>> task = new ArrayList<>(
                Arrays.asList(
                        (state, event) -> {
                            try {
                                Awaitility.await().pollDelay(Duration.ofMillis(100)).until(() -> true);
                            } catch (ConditionTimeoutException e) {
                                fail(e.getLocalizedMessage());
                            }
                            return new Result<>(Asyncer.generateType1UUID(), Boolean.TRUE, TestCommon.DONE_1);
                        }));

        assertFalse(taskExecutor.run(null, TestCommon.START, task, null).get(0).getValue());
        assertFalse(taskExecutor.run(TestCommon.STOPPED, null, task, null).get(0).getValue());
        assertFalse(taskExecutor.run(TestCommon.STOPPED, TestCommon.START, null, null).get(0).getValue());
    }
}
