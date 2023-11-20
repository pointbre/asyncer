package com.github.pointbre.asyncer.core;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.core.publisher.Sinks.One;
import reactor.util.concurrent.Queues;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

public class AsyncerImpl implements Asyncer {
    
//    private static final UUID uuid = AsyncerUtil.generateType1UUID();

    private final HashSet<State> states;
    private final State initialState;
    private final State finalState;
    private final HashSet<Event> events;
    private final HashSet<Transition> transitions;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
    private final BlockingQueue<Tuple3<UUID, Event, One<Result>>> requests = new LinkedBlockingQueue<>();
    private final Thread transitionHandler;

    private final Many<State> stateSink = Sinks.many().multicast().<State>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE,
	    false);
    private State currentState;

    public AsyncerImpl(HashSet<State> states, State initialState, State finalState, HashSet<Event> events,
	    HashSet<Transition> transitions) {
	this.states = states;
	this.initialState = initialState;
	this.finalState = finalState;
	this.events = events;
	this.transitions = transitions;

	this.transitionHandler = Thread.ofVirtual().name("asyncer-transition-handler").start(() -> {

	    while (true) {
		// UUID has an information about date/time
		Tuple3<UUID, Event, One<Result>> request = null;

		try {
		    request = Objects.requireNonNull(requests.take(), "request");
		} catch (InterruptedException e) {
		    System.out.println("Interrupted while waiting for request");
		    break;
		}

		System.out.println("Processing " + request);

		UUID requestUUID = request.getT1(); // FIXME what to do with this?
		Event requestEvent = request.getT2();
		One<Result> requestResultSink = request.getT3();

		// FIXME if event is changed to enum, I don't need to worry about this???
		if (!events.contains(requestEvent)) {
		    requestResultSink.tryEmitValue(new Result(Result.Type.FAILED,
			    requestEvent + " is not included in the allowed event list"));
		    continue;
		}

		if (currentState.equals(finalState)) {
		    requestResultSink
			    .tryEmitValue(new Result(Result.Type.FAILED, currentState + " is a final state"));
		    continue;
		}

		Optional<Transition> matchingTransition = transitions.stream()
			.filter(t -> t.getFrom().equals(currentState) && t.getEvent().equals(requestEvent))
			.findFirst();
		if (!matchingTransition.isPresent()) {
		    requestResultSink.tryEmitValue(new Result(Result.Type.FAILED,
			    "No matching transition found from " + currentState + " triggered by " + requestEvent));
		    continue;
		}
		Transition transition = matchingTransition.get();

		Action action = null;
		Tuple3<State, Result, List<Result>> transitionResult = null;
		State updatedState = null;
		Result result = null;
		if (transition instanceof StaticTransition t) {
		    action = t.getAction();
		    if (action != null) { // could be null
			try (var executor = Executor.of(t.getAction().getExecutor())) {
			    // 10 should be configured in Transition
			    transitionResult = executor.runUntil(transition, Instant.now().plusSeconds(20));
			} catch (InterruptedException e) {
			    // TODO Auto-generated catch block
			    e.printStackTrace();
			}
		    }
		    updatedState = t.getTo();
		    result = new Result(Result.Type.PROCESSED, null);
		} else if (transition instanceof DynamicTransition t) {
		    action = t.getAction();
		    if (action == null) {
			// Shouldn't be null
		    }
		    try (var executor = Executor.of(t.getAction().getExecutor())) {
			// 10 should be configured in Transition
			transitionResult = executor.runUntil(transition, Instant.now().plusSeconds(20));
			updatedState = transitionResult.getT1();
			result = transitionResult.getT2();
		    } catch (InterruptedException e) {
			// TODO Auto-generated catch block
//			e.printStackTrace();
			break;
		    }
		}

		if (transitionResult != null) {
		    transitionResult.getT3().forEach(r -> System.out.println("executor result=" + r));
		}

		if (updatedState != null && !currentState.equals(updatedState)) {
		    currentState = updatedState;
		    stateSink.tryEmitNext(updatedState);
		}

		requestResultSink.tryEmitValue(result);
		System.out.println("Processing done: " + result);
	    }
	});
	this.currentState = initialState;

	stateSink.tryEmitNext(initialState);
    }

    @Override
    public Mono<Result> fire(Event event) {

	final One<Result> resultSink = Sinks.one();
	// FIXME use uuid as thread name?
//	final UUID uuid = AsyncerUtil.generateType1UUID();
//	Thread.ofVirtual().name("asyncer-event-firer").start(() -> {
//	    try {
//		final UUID uuid = AsyncerUtil.generateType1UUID();
//		System.out.println("Registering " + event + ":" + uuid);
//		requests.put(Tuples.of(uuid, event, resultSink));
//	    } catch (InterruptedException e) {
//		resultSink.tryEmitValue(new Result(Result.Type.FAILED, "Failed to fire event: " + event));
//	    }
//	});
	    
//	    executor.submit(() -> {
		    try {
//			System.out.println("virtual thread? " + Thread.currentThread().isVirtual());
			final UUID uuid = AsyncerUtil.generateType1UUID();
//			System.out.println("Registering " + event + ":" + uuid);
			requests.put(Tuples.of(uuid, event, resultSink));
//			Thread.sleep(20);
//			System.out.println("Registered " + event + ":" + uuid);
		    } catch (InterruptedException e) {
			resultSink.tryEmitValue(new Result(Result.Type.FAILED, "Failed to fire event: " + event));
		    }
//	    });
	    
	    

	return resultSink.asMono();
    }

    @Override
    public Flux<State> state() {
	return stateSink.asFlux();
    }

    @Override
    public void close() throws Exception {
	System.out.println("asyncer's close() called");
	transitionHandler.interrupt();
    }
}
