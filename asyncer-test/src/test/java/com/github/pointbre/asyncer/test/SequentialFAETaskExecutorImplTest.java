package com.github.pointbre.asyncer.test;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.pointbre.asyncer.core.Asyncer;
import com.github.pointbre.asyncer.core.Asyncer.Result;
import com.github.pointbre.asyncer.core.Asyncer.TaskExecutor;
import com.github.pointbre.asyncer.core.SequentialFAETaskExecutorImpl;
import com.github.pointbre.asyncer.test.TestAsyncer.TestEvent;
import com.github.pointbre.asyncer.test.TestAsyncer.TestState;

import lombok.Cleanup;

@ExtendWith(MockitoExtension.class)
class SequentialFAETaskExecutorImplTest {

    private static final String DONE_1 = "Done 1";
    private static final String DONE_2 = "Done 2";
    private static final String DONE_3 = "Done 3";

    private static Stream<Arguments> timeoutTestCases() {
        return Stream.of(
                Arguments.of("When the timeout is null, both tasks should be completed successfully(task 1 --> task 2)",
                        null, true, DONE_1, true, DONE_2),
                Arguments.of(
                        "When the timeout is 3 seconds, both tasks should be completed successfully(task 1 --> task 2)",
                        Duration.ofSeconds(3), true, DONE_1, true, DONE_2),
                Arguments.of("When the timeout is 1.5 seconds, the first task should be timed out",
                        Duration.ofMillis(1500), false, TaskExecutor.TASK_TIMEDOUT, true, DONE_2));
    }

    @ParameterizedTest(name = "{index}: {0} - {1}")
    @MethodSource("timeoutTestCases")
    void shouldRunWithinTimeout(String description, Duration timeout, boolean firstTaskResult,
            String firstTaskDescription,
            boolean secondTaskResult, String secondTaskDescription)
            throws Exception {

        List<BiFunction<TestState, TestEvent, Result<Boolean>>> task = new ArrayList<>(
                Arrays.asList(
                        (state, event) -> {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                fail(e.getLocalizedMessage());
                            }
                            return new Result<>(Asyncer.generateType1UUID(), Boolean.TRUE, DONE_1);
                        },
                        (state, event) -> {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                fail(e.getLocalizedMessage());
                            }
                            return new Result<>(Asyncer.generateType1UUID(), Boolean.TRUE, DONE_2);
                        }));

        @Cleanup
        TaskExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> taskExecutor = new SequentialFAETaskExecutorImpl<>();
        List<Result<Boolean>> results = taskExecutor.run(new TestState(TestState.Type.STOPPED),
                new TestEvent(TestEvent.Type.START), task, timeout);

        assertEquals(2, results.size());
        assertEquals(firstTaskResult, results.get(0).getValue());
        assertTrue(results.get(0).getDescription().startsWith(firstTaskDescription));
        assertEquals(secondTaskResult, results.get(1).getValue());
        assertTrue(results.get(1).getDescription().startsWith(secondTaskDescription));
    }

    @Test
    void shouldContinueToExecuteTasksWhenExceptionOccurs() throws Exception {
        List<BiFunction<TestState, TestEvent, Result<Boolean>>> task1 = new ArrayList<>(
                Arrays.asList(
                        (state, event) -> {
                            throw new RuntimeException();
                        },
                        (state, event) -> {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                fail(e.getLocalizedMessage());
                            }
                            return new Result<>(Asyncer.generateType1UUID(), Boolean.TRUE, DONE_1);
                        }));

        @Cleanup
        TaskExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> taskExecutor1 = new SequentialFAETaskExecutorImpl<>();
        List<Result<Boolean>> results1 = taskExecutor1.run(new TestState(TestState.Type.STOPPED),
                new TestEvent(TestEvent.Type.START), task1, null);

        assertEquals(2, results1.size());
        assertFalse(results1.get(0).getValue());
        assertTrue(results1.get(0).getDescription().startsWith(TaskExecutor.TASK_EXCEPTION));
        assertTrue(results1.get(1).getValue());
        assertTrue(results1.get(1).getDescription().startsWith(DONE_1));
    }

    @Test
    void shouldExecuteAllOfTasksSequentially() throws Exception {
        List<BiFunction<TestState, TestEvent, Result<Boolean>>> task = new ArrayList<>(
                Arrays.asList(
                        (state, event) -> {
                            try {
                                Thread.sleep(1500);
                            } catch (InterruptedException e) {
                                fail(e.getLocalizedMessage());
                            }
                            return new Result<>(Asyncer.generateType1UUID(), Boolean.TRUE, DONE_1);
                        },
                        (state, event) -> {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                fail(e.getLocalizedMessage());
                            }
                            return new Result<>(Asyncer.generateType1UUID(), Boolean.TRUE, DONE_2);
                        },
                        (state, event) -> {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                fail(e.getLocalizedMessage());
                            }
                            return new Result<>(Asyncer.generateType1UUID(), Boolean.TRUE, DONE_3);
                        }));

        @Cleanup
        TaskExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> taskExecutor = new SequentialFAETaskExecutorImpl<>();
        List<Result<Boolean>> results = taskExecutor.run(new TestState(TestState.Type.STOPPED),
                new TestEvent(TestEvent.Type.START), task, null);

        assertEquals(3, results.size());
        assertTrue(results.get(0).getValue());
        assertTrue(results.get(0).getDescription().startsWith(DONE_1));
        assertTrue(results.get(1).getValue());
        assertTrue(results.get(1).getDescription().startsWith(DONE_2));
        assertTrue(results.get(2).getValue());
        assertTrue(results.get(2).getDescription().startsWith(DONE_3));
    }

    @Test
    void shouldThrowAnNPEWhenEitherStateOrEventIsNull() throws Exception {
        List<BiFunction<TestState, TestEvent, Result<Boolean>>> task = new ArrayList<>(
                Arrays.asList(
                        (state, event) -> {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                fail(e.getLocalizedMessage());
                            }
                            return new Result<>(Asyncer.generateType1UUID(), Boolean.TRUE, DONE_1);
                        }));

        @Cleanup
        TaskExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> taskExecutor = new SequentialFAETaskExecutorImpl<>();
        try {
            taskExecutor.run(null, null, task, null);
            fail("Should throw an NPE");
        } catch (Exception e) {
            //
        }
    }
}
