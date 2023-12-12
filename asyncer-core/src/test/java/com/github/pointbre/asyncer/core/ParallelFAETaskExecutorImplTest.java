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
import com.github.pointbre.asyncer.core.TestAsyncer.TestEvent;
import com.github.pointbre.asyncer.core.TestAsyncer.TestState;
import com.github.pointbre.asyncer.core.ParallelFAETaskExecutorImpl;

import lombok.Cleanup;

@ExtendWith(MockitoExtension.class)
class ParallelFAETaskExecutorImplTest {

    @Test
    void shouldExecuteAllOfTasksAtTheSameTime() throws Exception {
        @Cleanup
        TaskExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> taskExecutor = new ParallelFAETaskExecutorImpl<>();

        long before = System.currentTimeMillis();
        List<Result<Boolean>> results = taskExecutor.run(new TestState(TestState.Type.STOPPED),
                new TestEvent(TestEvent.Type.START), TestAsyncer.task, null);
        long after = System.currentTimeMillis();

        // The 2nd task should complete first as 2 tasks are executed in parallel and
        // the 2nd one has shorter sleep.
        assertEquals(2, results.size());
        assertTrue(results.get(0).getValue());
        assertTrue(results.get(0).getDescription().startsWith(TestAsyncer.DONE_2));
        assertTrue(results.get(1).getValue());
        assertTrue(results.get(1).getDescription().startsWith(TestAsyncer.DONE_1));

        // Total run time should be slightly > the sleep duration of the 1st task
        assertTrue((after - before) <= (TestAsyncer.SLEEP_1 + (TestAsyncer.SLEEP_2 / 2)));
    }

    private static Stream<Arguments> timeoutTestCases() {
        return Stream.of(
                Arguments.of("The 2nd task with the shorter sleep should complete first",
                        null, true, TestAsyncer.DONE_2, true, TestAsyncer.DONE_1),
                Arguments.of(
                        "The 2nd task with the shorter sleep should complete first",
                        Duration.ofMillis(TestAsyncer.SLEEP_1 + TestAsyncer.SLEEP_2), true, TestAsyncer.DONE_2, true,
                        TestAsyncer.DONE_1),
                Arguments.of("The 1st task with the sleep longer than timeout should be timed out",
                        Duration.ofMillis((TestAsyncer.SLEEP_1 + TestAsyncer.SLEEP_2) / 2), true, TestAsyncer.DONE_2,
                        false, TaskExecutor.TASK_TIMEDOUT));
    }

    @ParameterizedTest(name = "{index}: {0} - timeout {1}: {2}/{3} --> {4}/{5})")
    @MethodSource("timeoutTestCases")
    void shouldRunWithinTimeout(String description, Duration timeout, boolean firstResult,
            String firstResultDescription, boolean secondResult, String secondResultDescription)
            throws Exception {

        @Cleanup
        TaskExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> taskExecutor = new ParallelFAETaskExecutorImpl<>();

        List<Result<Boolean>> results = taskExecutor.run(new TestAsyncer.TestState(TestState.Type.STOPPED),
                new TestEvent(TestEvent.Type.START), TestAsyncer.task, timeout);

        assertEquals(2, results.size());
        assertEquals(firstResult, results.get(0).getValue());
        assertTrue(results.get(0).getDescription().startsWith(firstResultDescription));
        assertEquals(secondResult, results.get(1).getValue());
        assertTrue(results.get(1).getDescription().startsWith(secondResultDescription));
    }
}
