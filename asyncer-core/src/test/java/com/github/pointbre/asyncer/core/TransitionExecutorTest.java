package com.github.pointbre.asyncer.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.pointbre.asyncer.core.Asyncer.Change;
import com.github.pointbre.asyncer.core.Asyncer.Result;
import com.github.pointbre.asyncer.core.Asyncer.Transition;
import com.github.pointbre.asyncer.core.Asyncer.TransitionExecutor;
import com.github.pointbre.asyncer.core.TestAsyncer.TestEvent;
import com.github.pointbre.asyncer.core.TestAsyncer.TestState;

import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

@ExtendWith(MockitoExtension.class)
class TransitionExecutorTest {

	private static Stream<Arguments> transitionExecutors() {
		return Stream.of(
				Arguments.of("DefaultTransitionExecutorImpl", new DefaultTransitionExecutorImpl<>()));
	}

	// NonNull check
	// state is null
	// event is null
	// transition is null
	// transition's from is null
	// transition's event is null

	// Current state & event should be same with the transition
	// state != transition.from
	// event != transition.event

	// state

	// state -> state

	// state -> state -> tasks

	// state -> state -> tasks -> state

	@ParameterizedTest(name = "{index}: {0}")
	@MethodSource("transitionExecutors")
	void shouldXXX(String name,
			TransitionExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transitionExecutor)
			throws Exception {

		var controlResult = new AtomicBoolean();

		List<BiFunction<TestState, TestEvent, Result<Boolean>>> tasks1 = new ArrayList<>(
				Arrays.asList(
						(state, event) -> {
							// Return the result based on controlResult value
							return new Result<>(Asyncer.generateType1UUID(),
									Boolean.valueOf(controlResult.get()),
									TestAsyncer.DONE_1);
						}));

		Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> stoppedToStartingAndThenStartedOrStopped = new Transition<>(
				"Stopped --(START)--> Started | Stopped", TestAsyncer.STOPPED, TestAsyncer.START,
				TestAsyncer.STARTING,
				tasks1, new SequentialFAETaskExecutorImpl<>(), null, TestAsyncer.STARTED,
				TestAsyncer.STOPPED);

		Set<Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean>> transitions = new HashSet<>();
		transitions.add(stoppedToStartingAndThenStartedOrStopped);

		var stateSink = Sinks.many().multicast()
				.<Change<TestState>>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

		controlResult.set(true);
		var result1 = transitionExecutor.run(Asyncer.generateType1UUID(), TestAsyncer.STOPPED,
				TestAsyncer.START,
				stoppedToStartingAndThenStartedOrStopped, stateSink);

		assertEquals(2, result1.getStates().size());
		assertEquals(TestAsyncer.STARTING, result1.getStates().get(0));
		assertEquals(TestAsyncer.STARTED, result1.getStates().get(1));

		controlResult.set(false);
		var result2 = transitionExecutor.run(Asyncer.generateType1UUID(), TestAsyncer.STOPPED,
				TestAsyncer.START,
				stoppedToStartingAndThenStartedOrStopped, stateSink);

		assertEquals(2, result2.getStates().size());
		assertEquals(TestAsyncer.STARTING, result2.getStates().get(0));
		assertEquals(TestAsyncer.STOPPED, result2.getStates().get(1));
	}
}
