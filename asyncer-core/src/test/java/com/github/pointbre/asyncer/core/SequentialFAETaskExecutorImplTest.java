package com.github.pointbre.asyncer.core;

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
class SequentialFAETaskExecutorImplTest {

        @Test
        void shouldExecuteAllOfTasksSequentially() throws Exception {
                @Cleanup
                TaskExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> taskExecutor = new SequentialFAETaskExecutorImpl<>();

                long before = System.currentTimeMillis();
                List<Result<Boolean>> results = taskExecutor.run(new TestState(TestState.Type.STOPPED),
                                new TestEvent(TestEvent.Type.START), TestCommon.task, null);
                long after = System.currentTimeMillis();

                // The 1st task should complete first as 2 tasks are executed sequentially.
                assertEquals(2, results.size());
                assertTrue(results.get(0).getValue());
                assertTrue(results.get(0).getDescription().startsWith(TestCommon.DONE_1));
                assertTrue(results.get(1).getValue());
                assertTrue(results.get(1).getDescription().startsWith(TestCommon.DONE_2));

                // Total run time should be >= the sum of the sleep duration of both tasks.
                assertTrue((after - before) >= (TestCommon.SLEEP_1 + TestCommon.SLEEP_2));
        }

        private static Stream<Arguments> timeoutTestCases() {
                return Stream.of(
                                Arguments.of("The 1st task should complete first", null, true, TestCommon.DONE_1, true,
                                                TestCommon.DONE_2),
                                Arguments.of("The 1st task should complete first",
                                                Duration.ofMillis(TestCommon.SLEEP_1 + TestCommon.SLEEP_2), true,
                                                TestCommon.DONE_1, true,
                                                TestCommon.DONE_2),
                                Arguments.of("The 1st task with the sleep longer than timeout should be timed out",
                                                Duration.ofMillis((TestCommon.SLEEP_1 + TestCommon.SLEEP_2) / 2), false,
                                                TaskExecutor.TASK_TIMEDOUT, true, TestCommon.DONE_2));
        }

        @ParameterizedTest(name = "{index}: {0} - timeout {1}: {2}/{3} --> {4}/{5})")
        @MethodSource("timeoutTestCases")
        void shouldRunWithinTimeout(String description, Duration timeout, boolean firstResult,
                        String firstResultDescription, boolean secondResult, String secondResultDescription)
                        throws Exception {

                @Cleanup
                TaskExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> taskExecutor = new SequentialFAETaskExecutorImpl<>();

                List<Result<Boolean>> results = taskExecutor.run(new TestCommon.TestState(TestState.Type.STOPPED),
                                new TestEvent(TestEvent.Type.START), TestCommon.task, timeout);

                assertEquals(2, results.size());
                assertEquals(firstResult, results.get(0).getValue());
                assertTrue(results.get(0).getDescription().startsWith(firstResultDescription));
                assertEquals(secondResult, results.get(1).getValue());
                assertTrue(results.get(1).getDescription().startsWith(secondResultDescription));
        }
}
