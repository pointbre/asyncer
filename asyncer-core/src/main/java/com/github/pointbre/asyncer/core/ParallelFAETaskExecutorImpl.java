package com.github.pointbre.asyncer.core;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import com.github.pointbre.asyncer.core.Asyncer.Event;
import com.github.pointbre.asyncer.core.Asyncer.Result;
import com.github.pointbre.asyncer.core.Asyncer.State;
import com.github.pointbre.asyncer.core.Asyncer.TaskExecutor;

import lombok.NonNull;
import reactor.util.annotation.Nullable;

// FIXME don't extend StructuredTaskScope. Include task scope and use it just like SequentialFAETaskExecutor
public non-sealed class ParallelFAETaskExecutorImpl<S extends State<T>, T, E extends Event<F>, F>
		extends StructuredTaskScope<Result<Boolean>>
		implements TaskExecutor<S, T, E, F, Boolean> {

	private final Queue<Result<Boolean>> results = new LinkedTransferQueue<>();

	@Override
	public List<Result<Boolean>> run(@NonNull S state, @NonNull E event,
			@NonNull List<BiFunction<S, E, Result<Boolean>>> tasks,
			@Nullable Duration timeout) {

		tasks.stream().forEach(task -> fork(() -> task.apply(state, event)));

		if (timeout == null) {
			try {
				join();
			} catch (InterruptedException e) {
				//
			}

		} else {
			try {
				joinUntil(Instant.now().plus(timeout));
			} catch (InterruptedException e) {
				//
			} catch (TimeoutException e) {
				//
			}
		}

		// If any timed out task is found, add the result
		for (int i = 1; i <= tasks.size() - results.size(); i++) {
			results.add(new Result<>(Asyncer.generateType1UUID(), Boolean.FALSE, TASK_TIMEDOUT));
		}

		return results.stream().collect(Collectors.toUnmodifiableList());
	}

	@Override
	protected void handleComplete(Subtask<? extends Result<Boolean>> task) {
		if (task.state() == Subtask.State.FAILED) {
			results.add(new Result<>(Asyncer.generateType1UUID(), Boolean.FALSE,
					TASK_EXCEPTION + ": " + task.exception().getLocalizedMessage()));
		} else if (task.state() == Subtask.State.UNAVAILABLE) {
			results.add(new Result<>(Asyncer.generateType1UUID(), Boolean.FALSE, TASK_NOT_COMPLETED));
		} else if (task.get() == null) {
			results.add(new Result<>(Asyncer.generateType1UUID(), Boolean.FALSE, TASK_NULL_RESULT));
		} else {
			results.add(task.get());
		}
	}

	public Class<?> getClassFile() {
		Type type = getClass().getGenericSuperclass();
		ParameterizedType paramType = (ParameterizedType) type;
		return (Class<?>) paramType.getActualTypeArguments()[0];
	}
}