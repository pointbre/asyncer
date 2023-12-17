package com.github.pointbre.asyncer.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.pointbre.asyncer.core.Asyncer.AsyncerException;
import com.github.pointbre.asyncer.core.Asyncer.Change;
import com.github.pointbre.asyncer.core.Asyncer.Result;
import com.github.pointbre.asyncer.core.Asyncer.TaskExecutor;
import com.github.pointbre.asyncer.core.Asyncer.Transition;
import com.github.pointbre.asyncer.core.Asyncer.TransitionExecutor;
import com.github.pointbre.asyncer.core.Asyncer.TransitionResult;
import com.github.pointbre.asyncer.core.TestCommon.TestEvent;
import com.github.pointbre.asyncer.core.TestCommon.TestState;

import reactor.core.Disposable;

@ExtendWith(MockitoExtension.class)
class AsyncerTest {

	private final List<Boolean> executedTasks = new ArrayList<>();

	private AtomicBoolean controlResult1 = new AtomicBoolean(false);
	private List<BiFunction<TestState, TestEvent, Result<Boolean>>> tasks1 = new ArrayList<>(
			Arrays.asList(
					(state, event) -> {
						executedTasks.add(controlResult1.get());
						return new Result<>(Asyncer.generateType1UUID(), controlResult1.get(), TestCommon.DONE_1);
					}));
	private Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transition1 = new Transition<>(
			"Stopped --(Start)--> Starting + Tasks ? Started : Stopped", TestCommon.STOPPED, TestCommon.START,
			TestCommon.STARTING, tasks1, TaskExecutor.Type.SEQUENTIAL_FAE, null, TestCommon.STARTED,
			TestCommon.STOPPED);

	private AtomicBoolean controlResult2 = new AtomicBoolean(false);
	private List<BiFunction<TestState, TestEvent, Result<Boolean>>> tasks2 = new ArrayList<>(
			Arrays.asList(
					(state, event) -> {
						executedTasks.add(controlResult2.get());
						return new Result<>(Asyncer.generateType1UUID(), controlResult2.get(), TestCommon.DONE_2);
					}));
	private Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transition2 = new Transition<>(
			"Started --(Stop)--> Stopping + Tasks ? Stopped : Stopped", TestCommon.STARTED, TestCommon.STOP,
			TestCommon.STOPPING, tasks2, TaskExecutor.Type.SEQUENTIAL_FAE, null, TestCommon.STOPPED,
			TestCommon.STOPPED);

	private AtomicBoolean controlResult3 = new AtomicBoolean(false);
	private List<BiFunction<TestState, TestEvent, Result<Boolean>>> tasks3 = new ArrayList<>(
			Arrays.asList(
					(state, event) -> {
						executedTasks.add(controlResult3.get());
						return new Result<>(Asyncer.generateType1UUID(), controlResult3.get(), event.getMessage());
					}));
	private Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transition3 = new Transition<>(
			"Started --(Send)--> Tasks", TestCommon.STARTED, TestCommon.SEND, null, tasks3,
			TaskExecutor.Type.SEQUENTIAL_FAE, null, null, null);

	private Set<Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean>> transitions = new HashSet<>(
			Arrays.asList(transition1, transition2, transition3));

	private static final String TEST_MESSAGE = "Hello Asyncer";

	@Test
	void shouldThrowAsyncerExceptionWhenRequiredParameterIsNull() {

		Asyncer<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> asyncer = null;

		try {
			asyncer = new DefaultAsyncerImpl<>(null, null, transitions, new DefaultTransitionExecutorImpl<>());
			fail("Should throw AsyncerException");
		} catch (AsyncerException e) {
			//
		} finally {
			try {
				if (asyncer != null)
					asyncer.close();
			} catch (Exception e) {
				//
			}
		}

		try {
			asyncer = new DefaultAsyncerImpl<>(TestCommon.STOPPED, null, null, new DefaultTransitionExecutorImpl<>());
			fail("Should throw AsyncerException");
		} catch (AsyncerException e) {
			//
		} finally {
			try {
				if (asyncer != null)
					asyncer.close();
			} catch (Exception e) {
				//
			}
		}

		try {
			asyncer = new DefaultAsyncerImpl<>(TestCommon.STOPPED, null, transitions, null);
			fail("Should throw AsyncerException");
		} catch (AsyncerException e) {
			//
		} finally {
			try {
				if (asyncer != null)
					asyncer.close();
			} catch (Exception e) {
				//
			}
		}
	}

	@Test
	void shouldReturnUUIDWhenUuidIsCalled() {
		Asyncer<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> asyncer = null;

		try {
			asyncer = new DefaultAsyncerImpl<>(TestCommon.STOPPED, null, transitions,
					new DefaultTransitionExecutorImpl<>());

			assertNotNull(asyncer.uuid());
		} catch (AsyncerException e) {
			fail("Shouldn't throw AsyncerException");
		} finally {
			try {
				if (asyncer != null)
					asyncer.close();
			} catch (Exception e) {
				//
			}
		}
	}

	@Test
	void shouldPublishTheInitialStateWhenStarted() {
		Asyncer<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> asyncer = null;
		Disposable stateSubscription = null;
		final List<Change<TestState>> publishedStates = new ArrayList<>();

		try {
			asyncer = new DefaultAsyncerImpl<>(TestCommon.STOPPED, null, transitions,
					new DefaultTransitionExecutorImpl<>());

			stateSubscription = asyncer.state().subscribe(c -> publishedStates.add(c));

			Awaitility.await().atMost(TestCommon.MAX_WAIT, TimeUnit.SECONDS).untilAsserted(() -> {
				assertEquals(1, publishedStates.size());
				assertEquals(TestCommon.STOPPED.getType(), publishedStates.get(0).getValue().getType());
			});

		} catch (Exception e) {
			fail("Shouldn't throw Exception: " + e);
		} finally {
			if (asyncer != null) {
				try {
					asyncer.close();
				} catch (Exception e) {
					//
				}
			}
			if (stateSubscription != null && !stateSubscription.isDisposed()) {
				stateSubscription.dispose();
			}
		}
	}

	@Test
	void shouldPublishCompleteSignalWhenClosed() {
		Asyncer<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> asyncer = null;
		Disposable stateSubscription = null;
		final AtomicBoolean isCompleted = new AtomicBoolean(false);

		try {
			asyncer = new DefaultAsyncerImpl<>(TestCommon.STOPPED, null, transitions,
					new DefaultTransitionExecutorImpl<>());

			stateSubscription = asyncer.state().subscribe(c -> {
			}, e -> {
			}, () -> isCompleted.set(true));

			asyncer.close();
			assertTrue(isCompleted.get());
		} catch (Exception e) {
			fail("Shouldn't throw Exception: " + e);
		} finally {
			if (stateSubscription != null && !stateSubscription.isDisposed()) {
				stateSubscription.dispose();
			}
		}
	}

	@Test
	void shouldChangeStateAndPublishStateAndTransitionChangesWhenTasksExecutedSuccessfully() {
		Asyncer<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> asyncer = null;
		Disposable stateSubscription = null;
		Disposable transitionSubscription = null;

		final List<Change<TestState>> publishedStates = new ArrayList<>();
		final List<Change<TransitionResult<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean>>> publishedTransitions = new ArrayList<>();

		try {
			asyncer = new DefaultAsyncerImpl<>(TestCommon.STOPPED, null, transitions,
					new DefaultTransitionExecutorImpl<>());

			stateSubscription = asyncer.state().subscribe(c -> publishedStates.add(c));
			// FIXME transitionSubscription = asyncer.transition().subscribe(c -> publishedTransitions.add(c));

			// Stopped --(Start)--> Starting + Tasks --> Started : Stopped
			controlResult1.set(true); // Task execution done ok
			var fireResult1 = asyncer.fire(TestCommon.START).block(Duration.ofSeconds(TestCommon.MAX_WAIT));

			assertNotNull(fireResult1.getUuid());
			assertTrue(fireResult1.getValue());
			assertEquals(TransitionExecutor.TRANSITION_SUCCESSFULLY_DONE, fireResult1.getDescription());
			assertEquals(TestCommon.START.getType(), fireResult1.getEvent().getType());
			assertEquals(2, fireResult1.getStates().size());
			assertEquals(TestCommon.STARTING.getType(), fireResult1.getStates().get(0).getType());
			assertEquals(TestCommon.STARTED.getType(), fireResult1.getStates().get(1).getType());
			assertEquals(1, fireResult1.getTaskResults().size());
			assertNotNull(fireResult1.getTaskResults().get(0).getUuid());
			assertTrue(fireResult1.getTaskResults().get(0).getValue());
			assertEquals(TestCommon.DONE_1, fireResult1.getTaskResults().get(0).getDescription());

			Awaitility.await().atMost(TestCommon.MAX_WAIT, TimeUnit.SECONDS).untilAsserted(() -> {
				assertEquals(3, publishedStates.size());
				assertEquals(TestCommon.STOPPED.getType(), publishedStates.get(0).getValue().getType());
				assertEquals(TestCommon.STARTING.getType(), publishedStates.get(1).getValue().getType());
				assertEquals(TestCommon.STARTED.getType(), publishedStates.get(2).getValue().getType());
			});

			Awaitility.await().atMost(TestCommon.MAX_WAIT, TimeUnit.SECONDS).untilAsserted(() -> {
				assertEquals(3, publishedStates.size());
				assertEquals(TestCommon.STOPPED.getType(), publishedStates.get(0).getValue().getType());
				assertEquals(TestCommon.STARTING.getType(), publishedStates.get(1).getValue().getType());
				assertEquals(TestCommon.STARTED.getType(), publishedStates.get(2).getValue().getType());
			});
		} catch (Exception e) {
			fail("Shouldn't throw Exception: " + e);
		} finally {
			if (asyncer != null) {
				try {
					asyncer.close();
				} catch (Exception e) {
					//
				}
			}
			if (stateSubscription != null && !stateSubscription.isDisposed()) {
				stateSubscription.dispose();
			}
		}
	}

	@Test
	void shouldChangeStateAndPublishStateChangesWhenFailedToExecuteTasks() {
		Asyncer<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> asyncer = null;
		Disposable stateSubscription = null;
		final List<Change<TestState>> publishedStates = new ArrayList<>();

		try {
			asyncer = new DefaultAsyncerImpl<>(TestCommon.STOPPED, null, transitions,
					new DefaultTransitionExecutorImpl<>());

			stateSubscription = asyncer.state().subscribe(c -> publishedStates.add(c));

			// Stopped --(Start)--> Starting + Tasks --> Started : Stopped
			controlResult1.set(false); // Task execution failed
			var fireResult1 = asyncer.fire(TestCommon.START).block(Duration.ofSeconds(TestCommon.MAX_WAIT));

			assertNotNull(fireResult1.getUuid());
			assertTrue(fireResult1.getValue());
			assertEquals(TransitionExecutor.TRANSITION_SUCCESSFULLY_DONE, fireResult1.getDescription());
			assertEquals(TestCommon.START.getType(), fireResult1.getEvent().getType());
			assertEquals(2, fireResult1.getStates().size());
			assertEquals(TestCommon.STARTING.getType(), fireResult1.getStates().get(0).getType());
			assertEquals(TestCommon.STOPPED.getType(), fireResult1.getStates().get(1).getType());
			assertEquals(1, fireResult1.getTaskResults().size());
			assertNotNull(fireResult1.getTaskResults().get(0).getUuid());
			assertFalse(fireResult1.getTaskResults().get(0).getValue());
			assertEquals(TestCommon.DONE_1, fireResult1.getTaskResults().get(0).getDescription());

			Awaitility.await().atMost(TestCommon.MAX_WAIT, TimeUnit.SECONDS).untilAsserted(() -> {
				assertEquals(3, publishedStates.size());
				assertEquals(TestCommon.STOPPED.getType(), publishedStates.get(0).getValue().getType());
				assertEquals(TestCommon.STARTING.getType(), publishedStates.get(1).getValue().getType());
				assertEquals(TestCommon.STOPPED.getType(), publishedStates.get(2).getValue().getType());
			});
		} catch (Exception e) {
			fail("Shouldn't throw Exception: " + e);
		} finally {
			if (asyncer != null) {
				try {
					asyncer.close();
				} catch (Exception e) {
					//
				}
			}
			if (stateSubscription != null && !stateSubscription.isDisposed()) {
				stateSubscription.dispose();
			}
		}
	}

	@Test
	void shouldBeAbleToAccessCustomeDataWhenFiredEventIncludesCustomData() {
		Asyncer<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> asyncer = null;
		Disposable stateSubscription = null;
		final List<Change<TestState>> publishedStates = new ArrayList<>();

		try {
			asyncer = new DefaultAsyncerImpl<>(TestCommon.STOPPED, null, transitions,
					new DefaultTransitionExecutorImpl<>());

			stateSubscription = asyncer.state().subscribe(c -> publishedStates.add(c));

			// Stopped --(Start)--> Starting + Tasks ? Started : Stopped
			controlResult1.set(true);
			asyncer.fire(TestCommon.START).block(Duration.ofSeconds(TestCommon.MAX_WAIT));

			// Started --(Send)--> Tasks
			controlResult3.set(true);
			var fireResult3 = asyncer.fire(new TestEvent(TestEvent.Type.SEND, TEST_MESSAGE))
					.block(Duration.ofSeconds(TestCommon.MAX_WAIT));

			assertNotNull(fireResult3.getUuid());
			assertTrue(fireResult3.getValue());
			assertEquals(TransitionExecutor.TRANSITION_SUCCESSFULLY_DONE,
					fireResult3.getDescription());
			assertEquals(TestCommon.SEND.getType(), fireResult3.getEvent().getType());
			assertEquals(0, fireResult3.getStates().size());
			assertEquals(1, fireResult3.getTaskResults().size());
			assertNotNull(fireResult3.getTaskResults().get(0).getUuid());
			assertTrue(fireResult3.getTaskResults().get(0).getValue());
			assertEquals(TEST_MESSAGE, fireResult3.getTaskResults().get(0).getDescription());
		} catch (Exception e) {
			fail("Shouldn't throw Exception: " + e);
		} finally {
			if (asyncer != null) {
				try {
					asyncer.close();
				} catch (Exception e) {
					//
				}
			}
			if (stateSubscription != null && !stateSubscription.isDisposed()) {
				stateSubscription.dispose();
			}
		}
	}
}
