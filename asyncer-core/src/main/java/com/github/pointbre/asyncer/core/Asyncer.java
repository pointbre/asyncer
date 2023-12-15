package com.github.pointbre.asyncer.core;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiFunction;

import com.github.pointbre.asyncer.core.Asyncer.Event;
import com.github.pointbre.asyncer.core.Asyncer.State;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.NonFinal;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks.Many;
import reactor.util.annotation.Nullable;

public interface Asyncer<S extends State<T>, T, E extends Event<F>, F, R> extends AutoCloseable {

	UUID uuid();

	Flux<Change<S>> state();

	Flux<TransitionResult<S, T, E, F, R>> transition();

	Mono<TransitionResult<S, T, E, F, R>> fire(@NonNull E event);

	@Value
	@NonFinal
	public class Typed<T> {

		@NonNull
		T type;

		public Typed(@NonNull T type) {
			this.type = type;
		}

	}

	@Value
	@NonFinal
	public class Unique {

		@NonNull
		UUID uuid;

		public Unique(@NonNull UUID uuid) {
			this.uuid = uuid;
		}
	}

	@Value
	@NonFinal
	@EqualsAndHashCode(callSuper = true)
	public class Change<C> extends Unique {

		@NonNull
		C value;

		public Change(@NonNull UUID uuid, @NonNull C value) {
			super(uuid);
			this.value = value;
		}

	}

	@Value
	@NonFinal
	@EqualsAndHashCode(callSuper = true)
	public class State<T> extends Typed<T> {

		public State(@NonNull T type) {
			super(type);
		}

	}

	@Value
	@NonFinal
	@EqualsAndHashCode(callSuper = true)
	public class Event<T> extends Typed<T> {

		protected Event(@NonNull T type) {
			super(type);
		}

	}

	@Value
	public class Transition<S extends State<T>, T, E extends Event<F>, F, R> {

		@NonNull
		String name;

		@NonNull
		S from;

		@NonNull
		E event;

		@Nullable
		S to;

		@Nullable
		List<BiFunction<S, E, Result<R>>> tasks;

		@Nullable
		TaskExecutorType taskExecutorType;

		@Nullable
		Duration timeout;

		@Nullable
		S toWhenProcessed;

		@Nullable
		S toWhenFailed;
	}

	@Value
	@NonFinal
	@EqualsAndHashCode(callSuper = true)
	public class Result<R> extends Unique {

		@NonNull
		R value;

		@NonNull
		String description;

		public Result(@NonNull UUID uuid, @NonNull R value, @NonNull String description) {
			super(uuid);
			this.value = value;
			this.description = description;
		}

	}

	@Value
	@NonFinal
	@EqualsAndHashCode(callSuper = true)
	public class TransitionResult<S extends State<T>, T, E extends Event<F>, F, R> extends Result<R> {

		@Nullable
		E event;

		@Nullable
		List<S> states;

		@Nullable
		Transition<S, T, E, F, R> transition;

		@Nullable
		List<Result<R>> taskResults;

		public TransitionResult(@NonNull UUID uuid, @NonNull R value, @NonNull String description, @Nullable E event,
				@Nullable List<S> states, @Nullable Transition<S, T, E, F, R> transition,
				@Nullable List<Result<R>> taskResults) {
			super(uuid, value, description);
			this.event = event;
			this.states = states;
			this.transition = transition;
			this.taskResults = taskResults;
		}
	}

	public sealed interface TransitionExecutor<S extends State<T>, T, E extends Event<F>, F, R>
			permits DefaultTransitionExecutorImpl {

		public static final String TRANSITION_NULL_PARAMETER = "The provided parameters shouldn't be null";
		public static final String TRANSITION_CURRENT_STATE_MISMATCH = "The current state and the transition doesn't match";
		public static final String TRANSITION_CURRENT_EVENT_MISMATCH = "The current event and the transition doesn't match";
		public static final String TRANSITION_SUCCESSFULLY_DONE = "Successfully executed the transition";

		public TransitionResult<S, T, E, F, R> run(@Nullable S state, @Nullable E event,
				@Nullable Transition<S, T, E, F, R> transition, @Nullable Many<Change<S>> stateSink);
	}

	public enum TaskExecutorType {
		PARALLEL_FAE, SEQUENTIAL_FAE;
	}

	public sealed interface TaskExecutor<S extends State<T>, T, E extends Event<F>, F, R>
			extends AutoCloseable
			permits ParallelFAETaskExecutorImpl, SequentialFAETaskExecutorImpl {

		public static final String TASK_NULL_PARAMETER = "The provided parameters shouldn't be null";
		public static final String TASK_TIMEDOUT = "Timed out";
		public static final String TASK_EXCEPTION = "Exception occurred";
		public static final String TASK_NOT_COMPLETED = "Not completed after forked";
		public static final String TASK_NULL_RESULT = "Null result is returned";

		public List<Result<R>> run(@Nullable S state, @Nullable E event,
				@Nullable List<BiFunction<S, E, Result<R>>> tasks,
				@Nullable Duration timeout);
	}

	// The below code is from https://www.baeldung.com/java-uuid
	public static UUID generateType1UUID() {
		long most64SigBits = Asyncer.get64MostSignificantBitsForVersion1();
		long least64SigBits = Asyncer.get64LeastSignificantBitsForVersion1();
		return new UUID(most64SigBits, least64SigBits);
	}

	private static long get64MostSignificantBitsForVersion1() {
		final long currentTimeMillis = System.currentTimeMillis();
		final long time_low = (currentTimeMillis & 0x0000_0000_FFFF_FFFFL) << 32;
		final long time_mid = ((currentTimeMillis >> 32) & 0xFFFF) << 16;
		final long version = 1 << 12;
		final long time_hi = ((currentTimeMillis >> 48) & 0x0FFF);
		return time_low | time_mid | version | time_hi;
	}

	private static long get64LeastSignificantBitsForVersion1() {
		long random63BitLong = new Random().nextLong() & 0x3FFFFFFFFFFFFFFFL;
		long variant3BitFlag = 0x8000000000000000L;
		return random63BitLong | variant3BitFlag;
	}
}
