package com.github.pointbre.asyncer.core;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

		IntStream.range(1, tasks.size() - results.size())
				.forEach(n -> {
					results.add(new Result<>(AsyncerUtil.generateType1UUID(), Boolean.FALSE, TASK_TIMEDOUT));
				});

		return results.stream().collect(Collectors.toUnmodifiableList());
	}

	@Override
	protected void handleComplete(Subtask<? extends Result<Boolean>> task) {
		if (task.state() == Subtask.State.FAILED) {
			results.add(new Result<>(AsyncerUtil.generateType1UUID(),
					Boolean.FALSE, TASK_EXCEPTION + ": " + task.exception()));
		} else if (task.state() == Subtask.State.UNAVAILABLE) {
			results.add(new Result<>(AsyncerUtil.generateType1UUID(), Boolean.FALSE, "Not completed after forked"));
		} else {
			results.add(task.get());
		}
	}
}