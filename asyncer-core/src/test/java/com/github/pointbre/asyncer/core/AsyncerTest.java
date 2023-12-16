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
import com.github.pointbre.asyncer.core.TestAsyncer.TestEvent;
import com.github.pointbre.asyncer.core.TestAsyncer.TestState;

import reactor.core.Disposable;

@ExtendWith(MockitoExtension.class)
class AsyncerTest {

	private final List<Boolean> executedTasks = new ArrayList<>();

	private AtomicBoolean controlResult1 = new AtomicBoolean(false);
	private List<BiFunction<TestState, TestEvent, Result<Boolean>>> tasks1 = new ArrayList<>(
			Arrays.asList(
					(state, event) -> {
						executedTasks.add(controlResult1.get());
						return new Result<>(Asyncer.generateType1UUID(), controlResult1.get(), TestAsyncer.DONE_1);
					}));
	private Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transition1 = new Transition<>(
			"Stopped --(Start)--> Starting + Tasks ? Started : Stopped", TestAsyncer.STOPPED, TestAsyncer.START,
			TestAsyncer.STARTING, tasks1, TaskExecutor.Type.SEQUENTIAL_FAE, null, TestAsyncer.STARTED,
			TestAsyncer.STOPPED);

	private AtomicBoolean controlResult2 = new AtomicBoolean(false);
	private List<BiFunction<TestState, TestEvent, Result<Boolean>>> tasks2 = new ArrayList<>(
			Arrays.asList(
					(state, event) -> {
						executedTasks.add(controlResult2.get());
						return new Result<>(Asyncer.generateType1UUID(), controlResult2.get(), TestAsyncer.DONE_2);
					}));
	private Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transition2 = new Transition<>(
			"Started --(Stop)--> Stopping + Tasks ? Stopped : Stopped", TestAsyncer.STARTED, TestAsyncer.STOP,
			TestAsyncer.STOPPING, tasks2, TaskExecutor.Type.SEQUENTIAL_FAE, null, TestAsyncer.STOPPED,
			TestAsyncer.STOPPED);

	private AtomicBoolean controlResult3 = new AtomicBoolean(false);
	private List<BiFunction<TestState, TestEvent, Result<Boolean>>> tasks3 = new ArrayList<>(
			Arrays.asList(
					(state, event) -> {
						// FIXME The message is not exposed to this task!!! Should use wildcard?
						System.out.println("event.getMessage()=" + event.getMessage());
						executedTasks.add(controlResult3.get());
						return new Result<>(Asyncer.generateType1UUID(), controlResult3.get(), event.getMessage());
					}));
	private Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transition3 = new Transition<>(
			"Started --(Send)--> Tasks", TestAsyncer.STARTED, TestAsyncer.SEND, null, tasks3,
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
			asyncer = new DefaultAsyncerImpl<>(TestAsyncer.STOPPED, null, null, new DefaultTransitionExecutorImpl<>());
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
			asyncer = new DefaultAsyncerImpl<>(TestAsyncer.STOPPED, null, transitions, null);
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
			asyncer = new DefaultAsyncerImpl<>(TestAsyncer.STOPPED, null, transitions,
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
			asyncer = new DefaultAsyncerImpl<>(TestAsyncer.STOPPED, null, transitions,
					new DefaultTransitionExecutorImpl<>());

			stateSubscription = asyncer.state().subscribe(c -> publishedStates.add(c));

			Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
				assertEquals(1, publishedStates.size());
				assertEquals(TestAsyncer.STOPPED, publishedStates.get(0).getValue());
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
			asyncer = new DefaultAsyncerImpl<>(TestAsyncer.STOPPED, null, transitions,
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
	void xxx() {
		Asyncer<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> asyncer = null;
		Disposable stateSubscription = null;
		final List<Change<TestState>> publishedStates = new ArrayList<>();

		try {
			asyncer = new DefaultAsyncerImpl<>(TestAsyncer.STOPPED, null, transitions,
					new DefaultTransitionExecutorImpl<>());

			stateSubscription = asyncer.state().subscribe(c -> publishedStates.add(c));

			controlResult1.set(true);
			var fireResult1 = asyncer.fire(TestAsyncer.START).block(Duration.ofSeconds(2));

			assertNotNull(fireResult1.getUuid());
			assertTrue(fireResult1.getValue());
			assertEquals(TransitionExecutor.TRANSITION_SUCCESSFULLY_DONE, fireResult1.getDescription());
			assertEquals(TestAsyncer.START.getType(), fireResult1.getEvent().getType());
			assertEquals(2, fireResult1.getStates().size());
			assertEquals(TestAsyncer.STARTING, fireResult1.getStates().get(0));
			assertEquals(TestAsyncer.STARTED, fireResult1.getStates().get(1));
			assertEquals(1, fireResult1.getTaskResults().size());
			assertNotNull(fireResult1.getTaskResults().get(0).getUuid());
			assertTrue(fireResult1.getTaskResults().get(0).getValue());
			assertEquals(TestAsyncer.DONE_1, fireResult1.getTaskResults().get(0).getDescription());

			controlResult3.set(true);
			var fireResult3 = asyncer.fire(new TestEvent(TestEvent.Type.SEND, TEST_MESSAGE))
					.block(Duration.ofSeconds(2));

			assertNotNull(fireResult3.getUuid());
			assertTrue(fireResult3.getValue());
			assertEquals(TransitionExecutor.TRANSITION_SUCCESSFULLY_DONE, fireResult3.getDescription());
			assertEquals(TestAsyncer.SEND.getType(), fireResult3.getEvent().getType());
			assertEquals(0, fireResult3.getStates().size());
			assertEquals(1, fireResult3.getTaskResults().size());
			assertNotNull(fireResult3.getTaskResults().get(0).getUuid());
			assertTrue(fireResult3.getTaskResults().get(0).getValue());
			assertEquals(TEST_MESSAGE, fireResult3.getTaskResults().get(0).getDescription());

			controlResult2.set(true);
			var fireResult2 = asyncer.fire(TestAsyncer.STOP).block(Duration.ofSeconds(2));
			assertNotNull(fireResult2.getUuid());
			assertTrue(fireResult2.getValue());
			assertEquals(TransitionExecutor.TRANSITION_SUCCESSFULLY_DONE, fireResult2.getDescription());
			assertEquals(TestAsyncer.STOP.getType(), fireResult2.getEvent().getType());
			assertEquals(2, fireResult2.getStates().size());
			assertEquals(TestAsyncer.STOPPING, fireResult2.getStates().get(0));
			assertEquals(TestAsyncer.STOPPED, fireResult2.getStates().get(1));
			assertEquals(1, fireResult2.getTaskResults().size());
			assertNotNull(fireResult2.getTaskResults().get(0).getUuid());
			assertTrue(fireResult2.getTaskResults().get(0).getValue());
			assertEquals(TestAsyncer.DONE_2, fireResult2.getTaskResults().get(0).getDescription());

			Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
				assertEquals(5, publishedStates.size());
				assertEquals(TestAsyncer.STOPPED, publishedStates.get(0).getValue());
				assertEquals(TestAsyncer.STARTING, publishedStates.get(1).getValue());
				assertEquals(TestAsyncer.STARTED, publishedStates.get(2).getValue());
				assertEquals(TestAsyncer.STOPPING, publishedStates.get(3).getValue());
				assertEquals(TestAsyncer.STOPPED, publishedStates.get(4).getValue());
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
}
