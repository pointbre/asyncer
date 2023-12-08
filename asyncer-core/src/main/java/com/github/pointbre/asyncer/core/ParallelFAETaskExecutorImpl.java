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

	private final Queue<Result<Boolean>> taskResults = new LinkedTransferQueue<>();

	@Override
	public List<Result<Boolean>> run(@NonNull S state, @NonNull E event,
			@NonNull List<BiFunction<S, E, Result<Boolean>>> tasks,
			@Nullable Duration timeout) {

		tasks.stream().forEach(task -> fork(() -> task.apply(state, event)));

		if (timeout == null) {
			try {
				join();
			} catch (InterruptedException e) {
				System.out.println("111 Interrupted called: " + taskResults);
			}

		} else {
			try {
				joinUntil(Instant.now().plus(timeout));
			} catch (InterruptedException e) {
				System.out.println("222 Interrupted called: " + taskResults);
			} catch (TimeoutException e) {
				System.out.println("222 Timed out");
			}
		}

		return taskResults.stream().collect(Collectors.toUnmodifiableList());
	}

	@Override
	protected void handleComplete(Subtask<? extends Result<Boolean>> task) {
		if (task.state() == Subtask.State.FAILED) {
			System.out.println("FAILED " + task);
			taskResults.add(new Result<>(AsyncerUtil.generateType1UUID(),
					Boolean.FALSE, "Exception occurred: " + task.exception()));
		} else if (task.state() == Subtask.State.UNAVAILABLE) {
			System.out.println("UNAVAILABLE " + task);
			taskResults.add(new Result<>(AsyncerUtil.generateType1UUID(), Boolean.FALSE, "Not completed after forked"));
		} else {
			System.out.println("SUCCESS " + task);
			taskResults.add(task.get());
		}
	}

	@Override
	public void close() {

		System.out.println("ParallelFailAtEndTaskExecutor's close() called");
		super.close();

	}

}