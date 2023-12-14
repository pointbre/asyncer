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

import lombok.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.core.publisher.Sinks.One;
import reactor.util.annotation.Nullable;
import reactor.util.concurrent.Queues;

public class DefaultAsyncerImpl<S extends State<T>, T, E extends Event<F>, F> implements Asyncer<S, T, E, F, Boolean> {

	public record Request<S extends State<T>, T, E extends Event<F>, F>(UUID uuid, E event,
			One<TransitionResult<S, T, E, F, Boolean>> resultSink) {
	}

	private final UUID uuid = Asyncer.generateType1UUID();

	@NonNull
	private final S initialState;

	@Nullable
	private final S finalState;

	@NonNull
	private final Set<Transition<S, T, E, F, Boolean>> transitions;

	@NonNull
	private final TransitionExecutor<S, T, E, F, Boolean> transitionExecutor;

	private final BlockingQueue<Request<S, T, E, F>> requests = new LinkedBlockingQueue<>();
	private final Thread transitionHandler;
	private final Request<S, T, E, F> POISON_PILL = new Request<>(null, null, null);
	private final AtomicBoolean isBeingClosed = new AtomicBoolean(false);

	private final Many<Change<S>> stateSink = Sinks.many().multicast()
			.<Change<S>>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
	private S currentState;

	private final Many<TransitionResult<S, T, E, F, Boolean>> transitionResultSink = Sinks.many().multicast()
			.<TransitionResult<S, T, E, F, Boolean>>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE,
					false);

	public DefaultAsyncerImpl(@NonNull S initialState, @Nullable S finalState,
			@NonNull Set<Transition<S, T, E, F, Boolean>> transitions,
			@NonNull TransitionExecutor<S, T, E, F, Boolean> transitionExecutor) {

		this.initialState = initialState;
		this.finalState = finalState;
		this.transitions = transitions;
		this.transitionExecutor = transitionExecutor;

		// FIXME Better put this into a separate class? Or injected?
		this.transitionHandler = Thread.ofVirtual().name("asyncer-transition-handler").start(() -> {

			while (true) {
				Request<S, T, E, F> request = null;

				try {
					request = requests.take();
				} catch (InterruptedException e) {
					System.out.println("While waiting for a new request, interrupted");
					break;
				}

				System.out.println("Request received " + request);

				if (request.equals(POISON_PILL)) {
					System.out.println("Poison pill arrived");
					break;
				}

				final UUID uuidOfRequest = request.uuid();
				final E eventOfRequest = request.event();
				One<TransitionResult<S, T, E, F, Boolean>> resultSinkOfRequest = request.resultSink();

				if (finalState != null && currentState.equals(finalState)) {
					resultSinkOfRequest.tryEmitValue(
							new TransitionResult<>(uuidOfRequest, Boolean.FALSE, eventOfRequest + " is a final state",
									eventOfRequest, null, null, null));
					continue;
				}

				final Optional<Transition<S, T, E, F, Boolean>> matchingTransition = transitions.stream()
						.filter(t -> t.getFrom().getType().equals(currentState.getType())
								&& t.getEvent().getType().equals(eventOfRequest.getType()))
						.findFirst();
				if (!matchingTransition.isPresent()) {
					resultSinkOfRequest.tryEmitValue(new TransitionResult<>(uuidOfRequest, Boolean.FALSE,
							"No matching transition found from " + currentState + " triggered by " + eventOfRequest,
							eventOfRequest, null, null, null));
					continue;
				}

				// If being closed now, just return the result now
				// if (isBeingClosed.get()) {
				// resultSinkOfRequest.tryEmitValue(
				// new TransitionResult<>(uuidOfRequest, Boolean.FALSE,
				// "Being closed now, so not possible to process the event: " + eventOfRequest,
				// eventOfRequest, null, null, null));
				// continue;
				// }

				System.out.println("Running");
				final Transition<S, T, E, F, Boolean> transition = matchingTransition.get();
				final TransitionResult<S, T, E, F, Boolean> transitionResult = transitionExecutor.run(uuidOfRequest,
						transition, stateSink);

				System.out.println("Returned transition result=" + transitionResult);
				if (transitionResult.getTaskResults() != null) {
					transitionResult.getTaskResults().forEach(r -> System.out.println("task result=" + r));
				}

				try {
					resultSinkOfRequest.tryEmitValue(transitionResult);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if (transitionResult.getStates() != null) {
					transitionResult.getStates().forEach(s -> {
						currentState = s;
					});
				}
				System.out.println("Processing done: " + transitionResult);

			}
		});

		this.currentState = initialState;
		stateSink.tryEmitNext(new Change<>(Asyncer.generateType1UUID(), initialState));

	}

	@Override
	public Mono<TransitionResult<S, T, E, F, Boolean>> fire(@NonNull UUID uuid, @NonNull E event) {

		final One<TransitionResult<S, T, E, F, Boolean>> resultSink = Sinks.one();

		if (isBeingClosed.get()) {
			TransitionResult<S, T, E, F, Boolean> transitionResult = new TransitionResult<>(uuid, Boolean.FALSE,
					"Failed to fire the event as Asyncer is being closed: " + event, event, null, null, null);
			resultSink.tryEmitValue(transitionResult);
		} else {
			try {
				requests.put(new Request<>(uuid, event, resultSink));
			} catch (InterruptedException e) {
				TransitionResult<S, T, E, F, Boolean> transitionResult = new TransitionResult<>(uuid, Boolean.FALSE,
						"Failed to fire the event: " + event, event, null, null, null);
				resultSink.tryEmitValue(transitionResult);
				transitionResultSink.tryEmitNext(transitionResult);
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
		return uuid;
	}

	@Override
	public void close() throws Exception {
		System.out.println("Asyncer's close() called");

		// Only the first execution is processed as we need to wait until all of
		// requests are processed
		if (!isBeingClosed.get()) {
			isBeingClosed.set(true);

			stateSink.tryEmitComplete();
			transitionResultSink.tryEmitComplete();

			// Add
			// transitionHandler.interrupt();
			try {
				System.out.println("Putting poison pill");
				requests.put(POISON_PILL);
				System.out.println("Waiting until the poison pill is taken");
				System.out.println("");
				while (!requests.isEmpty()) {
					System.out.print(".");
					Thread.sleep(Duration.ofMillis(100));
				}
				System.out.println("Poison pill is taken");
			} catch (InterruptedException e) {
			}
		}
	}

}
