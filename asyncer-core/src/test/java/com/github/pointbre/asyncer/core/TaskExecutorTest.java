package com.github.pointbre.asyncer.core;

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
import com.github.pointbre.asyncer.core.TestAsyncer.TestEvent;
import com.github.pointbre.asyncer.core.TestAsyncer.TestState;

@ExtendWith(MockitoExtension.class)
class TaskExecutorTest {

    private static Stream<Arguments> taskExecutors() {
        return Stream.of(
                Arguments.of("ParallelFAETaskExecutorImpl", new ParallelFAETaskExecutorImpl<>()),
                Arguments.of("SequentialFAETaskExecutorImpl", new SequentialFAETaskExecutorImpl<>()));
    }

    // TODO: Null test
    // TODO: Include a case that the task list is not empty but it has a null
    // element

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
                            return new Result<>(Asyncer.generateType1UUID(), Boolean.TRUE, TestAsyncer.DONE_1);
                        }));

        try (taskExecutor) {
            List<Result<Boolean>> results = taskExecutor.run(TestAsyncer.STOPPED, TestAsyncer.START, task1, null);

            assertEquals(2, results.size());
            assertFalse(results.get(0).getValue());
            assertTrue(results.get(0).getDescription().startsWith(TaskExecutor.TASK_EXCEPTION));
            assertTrue(results.get(1).getValue());
            assertTrue(results.get(1).getDescription().startsWith(TestAsyncer.DONE_1));
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

        List<Result<Boolean>> results = taskExecutor.run(TestAsyncer.STOPPED, TestAsyncer.START, task, null);
        assertEquals(1, results.size());
        assertFalse(results.get(0).getValue());
        assertTrue(results.get(0).getDescription().startsWith(TaskExecutor.TASK_NULL_RESULT));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("taskExecutors")
    void shouldThrowAnNPEWhenEitherStateOrEventIsNull(String name,
            TaskExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> taskExecutor) throws Exception {
        List<BiFunction<TestState, TestEvent, Result<Boolean>>> task = new ArrayList<>(
                Arrays.asList(
                        (state, event) -> {
                            try {
                                Awaitility.await().pollDelay(Duration.ofMillis(100)).until(() -> true);
                            } catch (ConditionTimeoutException e) {
                                fail(e.getLocalizedMessage());
                            }
                            return new Result<>(Asyncer.generateType1UUID(), Boolean.TRUE, TestAsyncer.DONE_1);
                        }));

        try (taskExecutor) {
            taskExecutor.run(null, null, task, null);
            fail("Should throw a NPE");
        } catch (NullPointerException e) {
            //
        } catch (Exception e) {
            fail("Should throw a NPE");
        }
    }
}
