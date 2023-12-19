package com.github.pointbre.asyncer.core;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.pointbre.asyncer.core.Asyncer.Event;
import com.github.pointbre.asyncer.core.Asyncer.State;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.RetryPolicy;
import lombok.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitResult;
import reactor.core.publisher.Sinks.Many;
import reactor.core.publisher.Sinks.One;
import reactor.util.annotation.Nullable;
import reactor.util.concurrent.Queues;

public class DefaultAsyncerImpl<S extends State<T>, T, E extends Event<F>, F> implements Asyncer<S, T, E, F, Boolean> {

	public record Request<S extends State<T>, T, E extends Event<F>, F>(E event,
			One<TransitionResult<S, T, E, F, Boolean>> resultSink) {
	}

	private final UUID asyncerUuid = Asyncer.generateType1UUID();

	@NonNull
	private final S initialState;

	@NonNull
	private final Set<Transition<S, T, E, F, Boolean>> transitions;

	@NonNull
	private final TransitionExecutor<S, T, E, F, Boolean> transitionExecutor;

	private final BlockingQueue<Request<S, T, E, F>> requests = new LinkedBlockingQueue<>();
	private final Request<S, T, E, F> poisonPill = new Request<>(null, null);
	private final AtomicBoolean isBeingClosed = new AtomicBoolean(false);

	private final Many<Change<S>> stateSink = Sinks.many().multicast()
			.<Change<S>>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
	private S currentState;

	private final Many<TransitionResult<S, T, E, F, Boolean>> transitionResultSink = Sinks.many().multicast()
			.<TransitionResult<S, T, E, F, Boolean>>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE,
					false);

	private FailsafeExecutor<EmitResult> failSafeExecutor = Failsafe.with(RetryPolicy.<EmitResult>builder()
			.handleResultIf(emitResult -> !emitResult.equals(EmitResult.OK))
			.withDelay(Duration.ofMillis(200))
			.withMaxRetries(5)
			.build());

	public DefaultAsyncerImpl(@Nullable S initialState, @Nullable Set<Transition<S, T, E, F, Boolean>> transitions,
			@Nullable TransitionExecutor<S, T, E, F, Boolean> transitionExecutor) throws AsyncerException {

		if (initialState == null) {
			throw new AsyncerException("The initialState shouldn't be null");
		}
		if (transitions == null) {
			throw new AsyncerException("The transitions shouldn't be null");
		}
		if (transitionExecutor == null) {
			throw new AsyncerException("The transitionExecutor shouldn't be null");
		}

		this.initialState = initialState;
		this.transitions = transitions;
		this.transitionExecutor = transitionExecutor;

		Thread.ofVirtual().name("asyncer-transition-handler").start(() -> {

			while (true) {
				Request<S, T, E, F> request = null;

				try {
					request = requests.take();
				} catch (InterruptedException e) {
					break;
				}

				if (request.equals(poisonPill)) {
					break;
				}

				final E eventOfRequest = request.event();
				One<TransitionResult<S, T, E, F, Boolean>> resultSinkOfRequest = request.resultSink();

				final Optional<Transition<S, T, E, F, Boolean>> matchingTransition = transitions.stream()
						.filter(t -> t.getFrom().getType().equals(currentState.getType())
								&& t.getEvent().getType().equals(eventOfRequest.getType()))
						.findFirst();

				final TransitionResult<S, T, E, F, Boolean> transitionResult;
				if (!matchingTransition.isPresent()) {
					transitionResult = new TransitionResult<>(
							Asyncer.generateType1UUID(), Boolean.FALSE, TransitionExecutor.TRANSITION_NOT_FOUND
									+ ": from " + currentState + " triggered by " + eventOfRequest,
							eventOfRequest, null, null, null);
				} else {
					final Transition<S, T, E, F, Boolean> transition = matchingTransition.get();
					transitionResult = transitionExecutor.run(currentState, eventOfRequest, transition, stateSink);
				}

				if (transitionResult.getStates() != null) {
					transitionResult.getStates().forEach(s -> currentState = s);
				}

				failSafeExecutor.get(() -> transitionResultSink.tryEmitNext(transitionResult));
				failSafeExecutor.get(() -> resultSinkOfRequest.tryEmitValue(transitionResult));
			}
		});

		this.currentState = initialState;
		stateSink.tryEmitNext(new Change<>(Asyncer.generateType1UUID(), initialState));

	}

	@Override
	public Mono<TransitionResult<S, T, E, F, Boolean>> fire(E event) {

		final One<TransitionResult<S, T, E, F, Boolean>> resultSink = Sinks.one();

		if (isBeingClosed.get()) {
			TransitionResult<S, T, E, F, Boolean> transitionResult = new TransitionResult<>(Asyncer.generateType1UUID(),
					Boolean.FALSE, Asyncer.ASYNCER_BEING_CLOSED + ": " + event, event, null, null, null);
			failSafeExecutor.get(() -> transitionResultSink.tryEmitNext(transitionResult));
			failSafeExecutor.get(() -> resultSink.tryEmitValue(transitionResult));
		} else {
			try {
				requests.put(new Request<>(event, resultSink));
			} catch (InterruptedException e) {
				TransitionResult<S, T, E, F, Boolean> transitionResult = new TransitionResult<>(
						Asyncer.generateType1UUID(), Boolean.FALSE,
						ASYNCER_EVENT_REGISTRATION_INTERRUPTED + ": " + event, event, null, null, null);
				failSafeExecutor.get(() -> transitionResultSink.tryEmitNext(transitionResult));
				failSafeExecutor.get(() -> resultSink.tryEmitValue(transitionResult));
			}
		}

		return resultSink.asMono();

	}

	@Override
	public Flux<Change<S>> state() {
		return stateSink.asFlux();
	}

	@Override
	public Flux<TransitionResult<S, T, E, F, Boolean>> transition() {
		return transitionResultSink.asFlux();
	}

	@Override
	public UUID uuid() {
		return asyncerUuid;
	}

	@Override
	public void close() throws Exception {

		if (isBeingClosed.get()) {
			return;
		}

		isBeingClosed.set(true);

		stateSink.tryEmitComplete();
		transitionResultSink.tryEmitComplete();

		try {
			requests.put(poisonPill);

			while (!requests.isEmpty()) {
				Thread.sleep(Duration.ofMillis(100));
			}
		} catch (InterruptedException e) {
			//
		}
	}
}
