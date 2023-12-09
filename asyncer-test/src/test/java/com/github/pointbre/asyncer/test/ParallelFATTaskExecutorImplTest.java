package com.github.pointbre.asyncer.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.pointbre.asyncer.core.Asyncer.Result;
import com.github.pointbre.asyncer.core.Asyncer.TaskExecutor;
import com.github.pointbre.asyncer.core.AsyncerUtil;
import com.github.pointbre.asyncer.core.ParallelFAETaskExecutorImpl;

@ExtendWith(MockitoExtension.class)
class ParallelFATTaskExecutorImplTest {

    // Prepare the list of invalid message for the following unit test method
    // private static Stream<Arguments> taskExecutorTestCases() {
    // return Stream.of(
    // Arguments.of("Invalid message type", INVALID_MSG_WITH_INVALID_MSG_TYPE),
    // Arguments.of("No STX", INVALID_MSG_WITHOUT_STX),
    // Arguments.of("No ETX", INVALID_MSG_WITHOUT_ETX));
    // }

    // Make this parameterized
    // - timeout
    // - expected result
    // @ParameterizedTest(name = "{index}: {0} - {1}")
    // @MethodSource("taskExecutorTestCases") // Provider method name
    @Test
    void test() throws Exception {

        List<BiFunction<TestState, TestEvent, Result<Boolean>>> task1 = new ArrayList<>(
                Arrays.asList(
                        (state, event) -> {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                fail(e.getLocalizedMessage());
                            }
                            return new Result<>(AsyncerUtil.generateType1UUID(), Boolean.TRUE, "done");
                        },
                        (state, event) -> {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                fail(e.getLocalizedMessage());
                            }
                            return new Result<>(AsyncerUtil.generateType1UUID(), Boolean.TRUE, "done");
                        }));

        TaskExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> taskExecutor = new ParallelFAETaskExecutorImpl<>();
        List<Result<Boolean>> results = taskExecutor.run(new TestState(TestState.Type.STOPPED),
                new TestEvent(TestEvent.Type.START), task1,
                null);
        assertEquals(2, results.size());
        assertEquals(true, results.get(0).getValue());
        assertEquals(true, results.get(1).getValue());

    }
}
