package com.github.pointbre.asyncer.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.pointbre.asyncer.core.Asyncer.Change;
import com.github.pointbre.asyncer.core.Asyncer.Result;
import com.github.pointbre.asyncer.core.Asyncer.TaskExecutorType;
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

	@ParameterizedTest(name = "{index}: {0}")
	@MethodSource("transitionExecutors")
	void shouldThrowANPEWhenAnyMandatoryArgumentIsNull(String name,
			TransitionExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transitionExecutor)
			throws Exception {

		UUID uuid = Asyncer.generateType1UUID();

		List<BiFunction<TestState, TestEvent, Result<Boolean>>> tasks = new ArrayList<>(
				Arrays.asList(
						(state, event) -> {
							return new Result<>(Asyncer.generateType1UUID(), Boolean.TRUE, TestAsyncer.DONE_1);
						}));

		Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transition = new Transition<>(
				"Stopped --(START)--> Started | Stopped", TestAsyncer.STOPPED, TestAsyncer.START, TestAsyncer.STARTING,
				tasks, TaskExecutorType.SEQUENTIAL_FAE, null, TestAsyncer.STARTED, TestAsyncer.STOPPED);

		Set<Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean>> transitions = new HashSet<>();
		transitions.add(transition);

		var stateSink = Sinks.many().multicast()
				.<Change<TestState>>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

		try {
			transitionExecutor.run(null,
					transition, stateSink);
			fail("Should throw a NPE");
		} catch (NullPointerException e) {
			//
		} catch (Exception e) {
			fail("Should throw a NPE");
		}

		try {
			transitionExecutor.run(uuid,
					null, stateSink);
			fail("Should throw a NPE");
		} catch (NullPointerException e) {
			//
		} catch (Exception e) {
			fail("Should throw a NPE");
		}

		try {
			transitionExecutor.run(uuid,
					transition, null);
			fail("Should throw a NPE");
		} catch (NullPointerException e) {
			//
		} catch (Exception e) {
			fail("Should throw a NPE");
		}
	}

	@ParameterizedTest(name = "{index}: {0}")
	@MethodSource("transitionExecutors")
	void shouldChangeToTheNextStateWhenNoTaskIsSpecified(String name,
			TransitionExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transitionExecutor)
			throws Exception {

		Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transition = new Transition<>(
				"Stopped --(START)--> Started", TestAsyncer.STOPPED, TestAsyncer.START,
				TestAsyncer.STARTED, null, null, null, null, null);

		Set<Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean>> transitions = new HashSet<>();
		transitions.add(transition);

		var stateSink = Sinks.many().multicast()
				.<Change<TestState>>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

		final List<Change<TestState>> list = new ArrayList<>();
		stateSink.asFlux().subscribe(c -> list.add(c));

		var result = transitionExecutor.run(Asyncer.generateType1UUID(), transition,
				stateSink);

		assertTrue(result.getValue());
		assertEquals(1, result.getStates().size());
		assertEquals(TestAsyncer.STARTED, result.getStates().get(0));

		Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
			assertEquals(1, list.size());
			assertEquals(TestAsyncer.STARTED, list.get(0).getValue());
		});
	}

	@ParameterizedTest(name = "{index}: {0}")
	@MethodSource("transitionExecutors")
	void shouldChangeToTheNextStateAndRunTasksWithoutFurtherStateTransitinoWhenNoFurtherTransitionIsSpecified(
			String name,
			TransitionExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transitionExecutor)
			throws Exception {

		final List<Boolean> executedTasks = new ArrayList<>();
		List<BiFunction<TestState, TestEvent, Result<Boolean>>> tasks = new ArrayList<>(
				Arrays.asList(
						(state, event) -> {
							executedTasks.add(Boolean.TRUE);
							return new Result<>(Asyncer.generateType1UUID(),
									Boolean.TRUE,
									TestAsyncer.DONE_1);
						}));

		Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transition = new Transition<>(
				"Stopped --(START)--> Started + tasks", TestAsyncer.STOPPED, TestAsyncer.START,
				TestAsyncer.STARTED, tasks, TaskExecutorType.SEQUENTIAL_FAE, null, null, null);

		Set<Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean>> transitions = new HashSet<>();
		transitions.add(transition);

		var stateSink = Sinks.many().multicast()
				.<Change<TestState>>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

		final List<Change<TestState>> publishedStates = new ArrayList<>();
		stateSink.asFlux().subscribe(c -> publishedStates.add(c));

		var result = transitionExecutor.run(Asyncer.generateType1UUID(), transition,
				stateSink);

		assertTrue(result.getValue());
		assertEquals(1, result.getStates().size());
		assertEquals(TestAsyncer.STARTED, result.getStates().get(0));

		Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
			assertEquals(1, publishedStates.size());
			assertEquals(TestAsyncer.STARTED, publishedStates.get(0).getValue());
		});

		Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
			assertEquals(1, executedTasks.size());
		});
	}

	@ParameterizedTest(name = "{index}: {0}")
	@MethodSource("transitionExecutors")
	void shouldChangeToTheNextStateAndRunTasksWithFurtherStateTransitinoWhenFurtherTransitionIsSpecified(String name,
			TransitionExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transitionExecutor)
			throws Exception {

		AtomicBoolean controlResult = new AtomicBoolean(false);

		final List<Boolean> executedTasks = new ArrayList<>();
		List<BiFunction<TestState, TestEvent, Result<Boolean>>> tasks = new ArrayList<>(
				Arrays.asList(
						(state, event) -> {
							executedTasks.add(Boolean.TRUE);
							return new Result<>(Asyncer.generateType1UUID(),
									controlResult.get(),
									TestAsyncer.DONE_1);
						}));

		Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transition = new Transition<>(
				"Stopped --(START)--> Started + tasks ? Started : Stopped", TestAsyncer.STOPPED, TestAsyncer.START,
				TestAsyncer.STARTING, tasks, TaskExecutorType.SEQUENTIAL_FAE, null, TestAsyncer.STARTED,
				TestAsyncer.STOPPED);

		Set<Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean>> transitions = new HashSet<>();
		transitions.add(transition);

		var stateSink1 = Sinks.many().multicast()
				.<Change<TestState>>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

		var stateSink2 = Sinks.many().multicast()
				.<Change<TestState>>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

		final List<Change<TestState>> publishedStates1 = new ArrayList<>();
		stateSink1.asFlux().subscribe(c -> publishedStates1.add(c));

		final List<Change<TestState>> publishedStates2 = new ArrayList<>();
		stateSink2.asFlux().subscribe(c -> publishedStates2.add(c));

		controlResult.set(true);
		var result1 = transitionExecutor.run(Asyncer.generateType1UUID(), transition, stateSink1);

		controlResult.set(false);
		var result2 = transitionExecutor.run(Asyncer.generateType1UUID(), transition, stateSink2);

		assertTrue(result1.getValue());
		assertEquals(2, result1.getStates().size());
		assertEquals(TestAsyncer.STARTING, result1.getStates().get(0));
		assertEquals(TestAsyncer.STARTED, result1.getStates().get(1));

		Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
			assertEquals(2, publishedStates1.size());
			assertEquals(TestAsyncer.STARTING, publishedStates1.get(0).getValue());
			assertEquals(TestAsyncer.STARTED, publishedStates1.get(1).getValue());
		});

		assertTrue(result2.getValue());
		assertEquals(2, result2.getStates().size());
		assertEquals(TestAsyncer.STARTING, result2.getStates().get(0));
		assertEquals(TestAsyncer.STOPPED, result2.getStates().get(1));

		Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
			assertEquals(2, publishedStates2.size());
			assertEquals(TestAsyncer.STARTING, publishedStates2.get(0).getValue());
			assertEquals(TestAsyncer.STOPPED, publishedStates2.get(1).getValue());
		});

		Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
			assertEquals(2, executedTasks.size());
		});
	}

}
