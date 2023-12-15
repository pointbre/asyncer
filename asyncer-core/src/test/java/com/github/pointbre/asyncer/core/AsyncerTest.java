package com.github.pointbre.asyncer.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.pointbre.asyncer.core.Asyncer.Result;
import com.github.pointbre.asyncer.core.Asyncer.TaskExecutorType;
import com.github.pointbre.asyncer.core.Asyncer.Transition;
import com.github.pointbre.asyncer.core.TestAsyncer.TestEvent;
import com.github.pointbre.asyncer.core.TestAsyncer.TestState;

@ExtendWith(MockitoExtension.class)
class AsyncerTest {

	void test() throws Exception {

		final List<Boolean> executedTasks = new ArrayList<>();
		List<BiFunction<TestState, TestEvent, Result<Boolean>>> tasks = new ArrayList<>(
				Arrays.asList(
						(state, event) -> {
							executedTasks.add(Boolean.TRUE);
							return new Result<>(Asyncer.generateType1UUID(), Boolean.TRUE, TestAsyncer.DONE_1);
						}));

		Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transition = new Transition<>(
				"Stopped --(START)--> Started + Tasks ? Started : Stopped", TestAsyncer.STOPPED, TestAsyncer.START,
				TestAsyncer.STARTING, tasks, TaskExecutorType.SEQUENTIAL_FAE, null, TestAsyncer.STARTED,
				TestAsyncer.STOPPED);

		Set<Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean>> transitions = new HashSet<>();
		transitions.add(transition);

		// DefaultAsyncerImpl(@NonNull S initialState, @Nullable S finalState,
		// @NonNull Set<Transition<S, T, E, F, Boolean>> transitions,
		// @NonNull TransitionExecutor<S, T, E, F, Boolean> transitionExecutor)
	}
}
