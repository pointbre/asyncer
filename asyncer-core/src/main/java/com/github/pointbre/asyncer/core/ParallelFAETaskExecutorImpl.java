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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
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

import reactor.util.annotation.Nullable;

public non-sealed class ParallelFAETaskExecutorImpl<S extends State<T>, T, E extends Event<F>, F>
		extends StructuredTaskScope<Result<Boolean>>
		implements TaskExecutor<S, T, E, F, Boolean> {

	private final Queue<Result<Boolean>> results = new LinkedTransferQueue<>();

	@Override
	public List<Result<Boolean>> run(@Nullable S state, @Nullable E event,
			@Nullable List<BiFunction<S, E, Result<Boolean>>> tasks,
			@Nullable Duration timeout) {

		if (state == null || event == null || tasks == null) {
			return List.of(new Result<>(Asyncer.generateType1UUID(), Boolean.FALSE,
					TASK_NULL_PARAMETER + ": state=" + state + ", event=" + event + ", tasks=" + tasks));
		}

		// Ignore null elements if found
		final var tasksWithoutNullElement = tasks.stream().filter(Objects::nonNull).collect(Collectors.toList());

		tasksWithoutNullElement.forEach(task -> fork(() -> task.apply(state, event)));

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
		for (int i = 1; i <= tasksWithoutNullElement.size() - results.size(); i++) {
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
}
