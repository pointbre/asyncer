package com.github.pointbre.asyncer.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.StructuredTaskScope.ShutdownOnSuccess;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import com.github.pointbre.asyncer.core.Asyncer.Event;
import com.github.pointbre.asyncer.core.Asyncer.Result;
import com.github.pointbre.asyncer.core.Asyncer.State;
import com.github.pointbre.asyncer.core.Asyncer.TaskExecutor;

import reactor.util.annotation.Nullable;

public non-sealed class SequentialFAETaskExecutorImpl<S extends State<T>, T, E extends Event<F>, F>
		implements TaskExecutor<S, T, E, F, Boolean> {

	private final List<Result<Boolean>> taskResults = new ArrayList<>();
	private final List<ShutdownOnSuccess<Result<Boolean>>> scopes = new ArrayList<>();

	@Override
	public List<Result<Boolean>> run(@Nullable S state, @Nullable E event,
			@Nullable List<BiFunction<S, E, Result<Boolean>>> tasks,
			@Nullable Duration timeout) {

		if (state == null || event == null || tasks == null) {
			taskResults.add(new Result<>(Asyncer.generateType1UUID(), Boolean.FALSE,
					TASK_NULL_PARAMETER + ": state=" + state + ", event=" + event + ", tasks=" + tasks));
			return taskResults;
		}

		// Ignore null elements if found
		final var tasksWithoutNullElement = tasks.stream().filter(Objects::nonNull).collect(Collectors.toList());

		for (BiFunction<S, E, Result<Boolean>> task : tasksWithoutNullElement) {
			try (ShutdownOnSuccess<Result<Boolean>> scope = new ShutdownOnSuccess<>()) {

				scopes.add(scope);

				scope.fork(() -> task.apply(state, event));

				boolean isTimedOut = false;
				if (timeout == null) {
					try {
						scope.join();
					} catch (InterruptedException e) {
						return taskResults;
					}
				} else {
					try {
						scope.joinUntil(Instant.now().plus(timeout));
					} catch (InterruptedException e) {
						return taskResults;
					} catch (TimeoutException e) {
						isTimedOut = true;
					}
				}

				if (isTimedOut) {
					taskResults.add(new Result<>(Asyncer.generateType1UUID(), Boolean.FALSE, TASK_TIMEDOUT));
				} else {
					try {
						if (scope.result() == null) {
							taskResults.add(new Result<>(Asyncer.generateType1UUID(), Boolean.FALSE, TASK_NULL_RESULT));
						} else {
							taskResults.add(scope.result());
						}
					} catch (Exception e) {
						taskResults.add(new Result<>(Asyncer.generateType1UUID(), Boolean.FALSE,
								TASK_EXCEPTION + ": " + e.getLocalizedMessage()));
					}
				}
			}
		}

		return taskResults;
	}

	@Override
	public void close() throws Exception {

		scopes.forEach(scope -> {
			if (!scope.isShutdown()) {
				try {
					scope.close();
				} catch (Exception e) {
					//
				}
			}
		});
	}
}
