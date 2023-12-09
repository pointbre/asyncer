package com.github.pointbre.asyncer.test;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.pointbre.asyncer.core.Asyncer.Result;
import com.github.pointbre.asyncer.core.Asyncer.TaskExecutor;
import com.github.pointbre.asyncer.core.AsyncerUtil;
import com.github.pointbre.asyncer.core.ParallelFAETaskExecutorImpl;

@ExtendWith(MockitoExtension.class)
class ParallelFATTaskExecutorImplTest {

    private static final String DONE_1 = "Done 1";
    private static final String DONE_2 = "Done 2";

    private static Stream<Arguments> timeoutTestCases() {
        return Stream.of(
                Arguments.of("Timeout is null, so both tasks should be finished ok", null, true, DONE_1, true, DONE_2),
                Arguments.of("3 seconds timeout, so both tasks should be finished ok", Duration.ofSeconds(3), true,
                        DONE_1, true, DONE_2),
                Arguments.of("1 second timeout, so the second task should be timed out", Duration.ofSeconds(1), true,
                        DONE_1, false,
                        TaskExecutor.TASK_TIMEDOUT));
    }

    @ParameterizedTest(name = "{index}: {0} - {1}")
    @MethodSource("timeoutTestCases")
    void shouldRunWithinTimeout(String description, Duration timeout, boolean firstTaskResult,
            String firstTaskDescription,
            boolean secondTaskResult, String secondTaskDescription)
            throws Exception {

        List<BiFunction<TestState, TestEvent, Result<Boolean>>> task1 = new ArrayList<>(
                Arrays.asList(
                        (state, event) -> {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                fail(e.getLocalizedMessage());
                            }
                            return new Result<>(AsyncerUtil.generateType1UUID(), Boolean.TRUE, DONE_1);
                        },
                        (state, event) -> {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                fail(e.getLocalizedMessage());
                            }
                            return new Result<>(AsyncerUtil.generateType1UUID(), Boolean.TRUE, DONE_2);
                        }));

        TaskExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> taskExecutor = new ParallelFAETaskExecutorImpl<>();
        List<Result<Boolean>> results = taskExecutor.run(new TestState(TestState.Type.STOPPED),
                new TestEvent(TestEvent.Type.START), task1, timeout);

        assertEquals(2, results.size());
        assertEquals(firstTaskResult, results.get(0).getValue());
        assertEquals(true, results.get(0).getDescription().startsWith(firstTaskDescription));
        assertEquals(secondTaskResult, results.get(1).getValue());
        assertEquals(true, results.get(1).getDescription().startsWith(secondTaskDescription));
    }
}
