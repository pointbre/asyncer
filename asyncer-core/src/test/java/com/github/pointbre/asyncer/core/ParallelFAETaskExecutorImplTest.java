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
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.pointbre.asyncer.core.Asyncer.Result;
import com.github.pointbre.asyncer.core.Asyncer.TaskExecutor;
import com.github.pointbre.asyncer.core.TestCommon.TestEvent;
import com.github.pointbre.asyncer.core.TestCommon.TestState;

import lombok.Cleanup;

@ExtendWith(MockitoExtension.class)
class ParallelFAETaskExecutorImplTest {

        @Test
        void shouldExecuteAllOfTasksAtTheSameTime() throws Exception {
                @Cleanup
                TaskExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> taskExecutor = new ParallelFAETaskExecutorImpl<>();

                long before = System.currentTimeMillis();
                List<Result<Boolean>> results = taskExecutor.run(new TestState(TestState.Type.STOPPED),
                                new TestEvent(TestEvent.Type.START), TestCommon.task, null);
                long after = System.currentTimeMillis();

                // The 2nd task should complete first as 2 tasks are executed in parallel and
                // the 2nd one has shorter sleep.
                assertEquals(2, results.size());
                assertTrue(results.get(0).getValue());
                assertTrue(results.get(0).getDescription().startsWith(TestCommon.DONE_2));
                assertTrue(results.get(1).getValue());
                assertTrue(results.get(1).getDescription().startsWith(TestCommon.DONE_1));

                // Total run time should be slightly > the sleep duration of the 1st task
                assertTrue((after - before) <= (TestCommon.SLEEP_1 + (TestCommon.SLEEP_2 / 2)));
        }

        private static Stream<Arguments> timeoutTestCases() {
                return Stream.of(
                                Arguments.of("The 2nd task with the shorter sleep should complete first",
                                                null, true, TestCommon.DONE_2, true, TestCommon.DONE_1),
                                Arguments.of(
                                                "The 2nd task with the shorter sleep should complete first",
                                                Duration.ofMillis(TestCommon.SLEEP_1 + TestCommon.SLEEP_2), true,
                                                TestCommon.DONE_2, true,
                                                TestCommon.DONE_1),
                                Arguments.of("The 1st task with the sleep longer than timeout should be timed out",
                                                Duration.ofMillis((TestCommon.SLEEP_1 + TestCommon.SLEEP_2) / 2), true,
                                                TestCommon.DONE_2,
                                                false, TaskExecutor.TASK_TIMEDOUT));
        }

        @ParameterizedTest(name = "{index}: {0} - timeout {1}: {2}/{3} --> {4}/{5})")
        @MethodSource("timeoutTestCases")
        void shouldRunWithinTimeout(String description, Duration timeout, boolean firstResult,
                        String firstResultDescription, boolean secondResult, String secondResultDescription)
                        throws Exception {

                @Cleanup
                TaskExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> taskExecutor = new ParallelFAETaskExecutorImpl<>();

                List<Result<Boolean>> results = taskExecutor.run(new TestCommon.TestState(TestState.Type.STOPPED),
                                new TestEvent(TestEvent.Type.START), TestCommon.task, timeout);

                assertEquals(2, results.size());
                assertEquals(firstResult, results.get(0).getValue());
                assertTrue(results.get(0).getDescription().startsWith(firstResultDescription));
                assertEquals(secondResult, results.get(1).getValue());
                assertTrue(results.get(1).getDescription().startsWith(secondResultDescription));
        }
}
