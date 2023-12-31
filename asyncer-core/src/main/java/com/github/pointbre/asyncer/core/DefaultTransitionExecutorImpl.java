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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.github.pointbre.asyncer.core.Asyncer.Change;
import com.github.pointbre.asyncer.core.Asyncer.Event;
import com.github.pointbre.asyncer.core.Asyncer.Result;
import com.github.pointbre.asyncer.core.Asyncer.State;
import com.github.pointbre.asyncer.core.Asyncer.TaskExecutor;
import com.github.pointbre.asyncer.core.Asyncer.Transition;
import com.github.pointbre.asyncer.core.Asyncer.TransitionExecutor;
import com.github.pointbre.asyncer.core.Asyncer.TransitionResult;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.RetryPolicy;
import reactor.core.publisher.Sinks.EmitResult;
import reactor.core.publisher.Sinks.Many;
import reactor.util.annotation.Nullable;

public non-sealed class DefaultTransitionExecutorImpl<S extends State<T>, T, E extends Event<F>, F>
		implements TransitionExecutor<S, T, E, F, Boolean> {

	private FailsafeExecutor<EmitResult> failSafeExecutor = Failsafe.with(RetryPolicy.<EmitResult>builder()
			.handleResultIf(emitResult -> !emitResult.equals(EmitResult.OK))
			.withDelay(Duration.ofMillis(200))
			.withMaxRetries(5)
			.build());

	@Override
	public TransitionResult<S, T, E, F, Boolean> run(@Nullable S state, @Nullable E event,
			@Nullable Transition<S, T, E, F, Boolean> transition,
			@Nullable Many<Change<S>> stateSink) {

		List<S> states = new ArrayList<>();
		List<Result<Boolean>> taskResults = null;

		if (state == null || event == null || transition == null || stateSink == null) {
			return new TransitionResult<>(Asyncer.generateType1UUID(), Boolean.FALSE,
					TRANSITION_NULL_PARAMETER + ": state=" + state + ", event=" + event + ", transition=" + transition
							+ ", stateSink=" + stateSink,
					event, states, transition, taskResults);
		}

		if (transition.getTo() != null) {
			S firstState = transition.getTo();
			states.add(firstState);
			failSafeExecutor.get(() -> stateSink.tryEmitNext(new Change<>(Asyncer.generateType1UUID(), firstState)));
		}

		if (transition.getTasks() != null && !transition.getTasks().isEmpty()
				&& transition.getTaskExecutorType() != null) {

			final var tasksWithoutNullElement = transition.getTasks().stream().filter(Objects::nonNull)
					.collect(Collectors.toList());
			final TaskExecutor<S, T, E, F, Boolean> taskExecutor = getTaskExecutor(transition);

			try {
				taskResults = taskExecutor.run(state, event, tasksWithoutNullElement,
						transition.getTimeout());
				if (transition.getToWhenProcessed() != null && transition.getToWhenFailed() != null) {
					final S secondState;
					if (tasksWithoutNullElement.size() == taskResults.size()
							&& taskResults.stream().allMatch(r -> r.getValue().booleanValue())) {
						secondState = transition.getToWhenProcessed();
					} else {
						secondState = transition.getToWhenFailed();
					}
					states.add(secondState);
					failSafeExecutor
							.get(() -> stateSink.tryEmitNext(new Change<>(Asyncer.generateType1UUID(), secondState)));
				}
			} finally {
				try {
					taskExecutor.close();
				} catch (Exception e) {
					//
				}
			}
		}

		return new TransitionResult<>(Asyncer.generateType1UUID(), Boolean.TRUE, TRANSITION_SUCCESSFULLY_DONE,
				event, states, transition, taskResults);
	}

	private TaskExecutor<S, T, E, F, Boolean> getTaskExecutor(Transition<S, T, E, F, Boolean> transition) {

		final TaskExecutor<S, T, E, F, Boolean> taskExecutor;
		if (transition.getTaskExecutorType().equals(TaskExecutor.Type.PARALLEL_FAE)) {
			taskExecutor = new ParallelFAETaskExecutorImpl<>();
		} else {
			taskExecutor = new SequentialFAETaskExecutorImpl<>();
		}

		return taskExecutor;
	}
}
