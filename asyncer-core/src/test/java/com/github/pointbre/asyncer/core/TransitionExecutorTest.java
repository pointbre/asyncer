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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import com.github.pointbre.asyncer.core.Asyncer.TaskExecutor;
import com.github.pointbre.asyncer.core.Asyncer.Transition;
import com.github.pointbre.asyncer.core.Asyncer.TransitionExecutor;
import com.github.pointbre.asyncer.core.TestCommon.TestEvent;
import com.github.pointbre.asyncer.core.TestCommon.TestState;

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
	void shouldReturnFailedResultWhenAnyOfParameterIsNull(String name,
			TransitionExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transitionExecutor)
			throws Exception {

		List<BiFunction<TestState, TestEvent, Result<Boolean>>> tasks = new ArrayList<>(
				Arrays.asList(
						(state, event) -> {
							return new Result<>(Asyncer.generateType1UUID(), Boolean.TRUE, TestCommon.DONE_1);
						}));

		Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transition = new Transition<>(
				"Stopped --(Start)--> Starting + Tasks ? Started : Stopped", TestCommon.STOPPED, TestCommon.START,
				TestCommon.STARTING,
				tasks, TaskExecutor.Type.SEQUENTIAL_FAE, null, TestCommon.STARTED, TestCommon.STOPPED);

		Set<Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean>> transitions = new HashSet<>();
		transitions.add(transition);

		var stateSink = Sinks.many().multicast()
				.<Change<TestState>>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

		assertFalse(transitionExecutor.run(null, TestCommon.START, transition, stateSink).getValue());
		assertFalse(transitionExecutor.run(TestCommon.STOPPED, null, transition, stateSink).getValue());
		assertFalse(transitionExecutor.run(TestCommon.STOPPED, TestCommon.START, null, stateSink).getValue());
		assertFalse(transitionExecutor.run(TestCommon.STOPPED, TestCommon.START, transition, null).getValue());
	}

	@ParameterizedTest(name = "{index}: {0}")
	@MethodSource("transitionExecutors")
	void shouldChangeToTheNextStateWhenNoTaskIsSpecified(String name,
			TransitionExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transitionExecutor)
			throws Exception {

		Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transition = new Transition<>(
				"Stopped --(Start)--> Started", TestCommon.STOPPED, TestCommon.START,
				TestCommon.STARTED, null, null, null, null, null);

		Set<Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean>> transitions = new HashSet<>();
		transitions.add(transition);

		var stateSink = Sinks.many().multicast()
				.<Change<TestState>>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

		final List<Change<TestState>> list = new ArrayList<>();
		stateSink.asFlux().subscribe(c -> list.add(c));

		var result = transitionExecutor.run(TestCommon.STOPPED, TestCommon.START, transition, stateSink);

		assertTrue(result.getValue());
		assertEquals(1, result.getStates().size());
		assertEquals(TestCommon.STARTED, result.getStates().get(0));

		Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
			assertEquals(1, list.size());
			assertEquals(TestCommon.STARTED, list.get(0).getValue());
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
									TestCommon.DONE_1);
						}));

		Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transition = new Transition<>(
				"Stopped --(Start)--> Started + Tasks", TestCommon.STOPPED, TestCommon.START,
				TestCommon.STARTED, tasks, TaskExecutor.Type.SEQUENTIAL_FAE, null, null, null);

		Set<Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean>> transitions = new HashSet<>();
		transitions.add(transition);

		var stateSink = Sinks.many().multicast()
				.<Change<TestState>>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

		final List<Change<TestState>> publishedStates = new ArrayList<>();
		stateSink.asFlux().subscribe(c -> publishedStates.add(c));

		var result = transitionExecutor.run(TestCommon.STOPPED, TestCommon.START, transition, stateSink);

		assertTrue(result.getValue());
		assertEquals(1, result.getStates().size());
		assertEquals(TestCommon.STARTED, result.getStates().get(0));

		Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
			assertEquals(1, publishedStates.size());
			assertEquals(TestCommon.STARTED, publishedStates.get(0).getValue());
		});

		Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
			assertEquals(1, executedTasks.size());
		});
	}

	@ParameterizedTest(name = "{index}: {0}")
	@MethodSource("transitionExecutors")
	void shouldRunTasksWithoutStateTransitinoWhenNoStateTransitionIsSpecified(
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
									TestCommon.DONE_1);
						}));

		Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transition = new Transition<>(
				"Stopped --(Start)--> Tasks", TestCommon.STOPPED, TestCommon.START,
				null, tasks, TaskExecutor.Type.SEQUENTIAL_FAE, null, null, null);

		Set<Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean>> transitions = new HashSet<>();
		transitions.add(transition);

		var stateSink = Sinks.many().multicast()
				.<Change<TestState>>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

		final List<Change<TestState>> publishedStates = new ArrayList<>();
		stateSink.asFlux().subscribe(c -> publishedStates.add(c));

		var result = transitionExecutor.run(TestCommon.STOPPED, TestCommon.START, transition, stateSink);

		assertTrue(result.getValue());
		assertEquals(0, result.getStates().size());

		Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
			assertEquals(0, publishedStates.size());
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
									TestCommon.DONE_1);
						}));

		Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transition = new Transition<>(
				"Stopped --(Start)--> Starting + Tasks ? Started : Stopped", TestCommon.STOPPED, TestCommon.START,
				TestCommon.STARTING, tasks, TaskExecutor.Type.SEQUENTIAL_FAE, null, TestCommon.STARTED,
				TestCommon.STOPPED);

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
		var result1 = transitionExecutor.run(TestCommon.STOPPED, TestCommon.START, transition, stateSink1);

		controlResult.set(false);
		var result2 = transitionExecutor.run(TestCommon.STOPPED, TestCommon.START, transition, stateSink2);

		assertTrue(result1.getValue());
		assertEquals(2, result1.getStates().size());
		assertEquals(TestCommon.STARTING, result1.getStates().get(0));
		assertEquals(TestCommon.STARTED, result1.getStates().get(1));

		Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
			assertEquals(2, publishedStates1.size());
			assertEquals(TestCommon.STARTING, publishedStates1.get(0).getValue());
			assertEquals(TestCommon.STARTED, publishedStates1.get(1).getValue());
		});

		assertTrue(result2.getValue());
		assertEquals(2, result2.getStates().size());
		assertEquals(TestCommon.STARTING, result2.getStates().get(0));
		assertEquals(TestCommon.STOPPED, result2.getStates().get(1));

		Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
			assertEquals(2, publishedStates2.size());
			assertEquals(TestCommon.STARTING, publishedStates2.get(0).getValue());
			assertEquals(TestCommon.STOPPED, publishedStates2.get(1).getValue());
		});

		Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
			assertEquals(2, executedTasks.size());
		});
	}
}
