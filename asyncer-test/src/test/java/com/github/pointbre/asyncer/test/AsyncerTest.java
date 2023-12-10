package com.github.pointbre.asyncer.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.pointbre.asyncer.core.Asyncer;
import com.github.pointbre.asyncer.core.Asyncer.Event;
import com.github.pointbre.asyncer.core.Asyncer.Result;
import com.github.pointbre.asyncer.core.Asyncer.State;
import com.github.pointbre.asyncer.core.Asyncer.Transition;
import com.github.pointbre.asyncer.core.Asyncer.TransitionExecutor;
import com.github.pointbre.asyncer.core.DefaultAsyncerImpl;
import com.github.pointbre.asyncer.core.DefaultTransitionExecutorImpl;
import com.github.pointbre.asyncer.core.SequentialFAETaskExecutorImpl;

import lombok.EqualsAndHashCode;
import lombok.Value;

@ExtendWith(MockitoExtension.class)
class AsyncerTest {

	@Value
	@EqualsAndHashCode(callSuper = true)
	public class TestState extends State<TestState.Type> {

		public enum Type {
			STARTING, STARTED, STOPPING, STOPPED;
		}

		public TestState(Type type) {
			super(type);
		}

		@Override
		public String toString() {
			return this.getType().name();
		}

	}

	@Value
	@EqualsAndHashCode(callSuper = true)
	public class TestEvent extends Event<TestEvent.Type> {

		public enum Type {
			START, STOP, SEND;
		}

		String message;

		public TestEvent(Type type) {
			this(type, null);
		}

		public TestEvent(Type type, String message) {
			super(type);
			this.message = message;
		}

		@Override
		public String toString() {
			return this.getType().name() + (message == null ? "" : ": " + message);
		}
	}

	@Test
	void test() throws Exception {

		var e1 = new TestEvent(TestEvent.Type.START);
		var e2 = new TestEvent(TestEvent.Type.START);
		System.out.println(e1.getType() == e2.getType());
		System.out.println(e1.getType().equals(e2.getType()));

		// var lockedToUnlocked = new Asyncer.StaticTransition(locked, coin, null,
		// unlocked);
		List<BiFunction<TestState, TestEvent, Result<Boolean>>> tasks1 = new ArrayList<>(
				Arrays.asList(
						(state, event) -> {
							System.out.println("inside of task: state=" + state + ", event=" + event);
							return new Result<>(Asyncer.generateType1UUID(), Boolean.TRUE, "done");
						}));

		Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> stoppedToStartingAndThenStartedOrStopped = new Transition<>(
				"",
				new TestState(TestState.Type.STOPPED),
				new TestEvent(TestEvent.Type.START),
				new TestState(TestState.Type.STARTING), tasks1, new SequentialFAETaskExecutorImpl<>(),
				null, new TestState(TestState.Type.STARTED), new TestState(TestState.Type.STOPPED));

		Set<Transition<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean>> transitions = new HashSet<>();
		transitions.add(stoppedToStartingAndThenStartedOrStopped);

		CountDownLatch latch = new CountDownLatch(1);
		TransitionExecutor<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> transitionExecutor = new DefaultTransitionExecutorImpl<>();
		long time1 = System.currentTimeMillis();
		try (Asyncer<TestState, TestState.Type, TestEvent, TestEvent.Type, Boolean> asyncer = new DefaultAsyncerImpl<>(
				new TestState(TestState.Type.STOPPED), null,
				transitions,
				transitionExecutor)) {

			System.out.println("1");

			asyncer.state().subscribe(s -> {
				System.out.println("state updated=" + s + " at " + new Date());
			}, e -> {
				System.out.println("state error=" + e);
			});

			System.out.println("2");

			asyncer.fire(Asyncer.generateType1UUID(), new TestEvent(TestEvent.Type.START, "additional message"))
					.subscribe(r -> {
						System.out.println("fire START finished=" + r);
						latch.countDown();
					}, e -> {
						System.out.println("fire START error=" + e);
						latch.countDown();
					});

			System.out.println("3");

			// asyncer.fire(AsyncerUtil.generateType1UUID(), push).subscribe(r -> {
			// System.out.println("fire push 1 finished=" + r);
			// latch.countDown();
			// }, e -> {
			// System.out.println("fire push 1 error=" + e);
			// latch.countDown();
			// });
			//
			// System.out.println("4");

			try {
				latch.await(10, TimeUnit.SECONDS);
				long time2 = System.currentTimeMillis();
				System.out.println("5 --> " + (time2 - time1));
				// latch.await();
			} catch (InterruptedException e) {

			} finally {

			}

			System.out.println("6");
		}

		// Thread.sleep(5);
		long time3 = System.currentTimeMillis();
		System.out.println("7 --> " + (time3 - time1));
	}
}
