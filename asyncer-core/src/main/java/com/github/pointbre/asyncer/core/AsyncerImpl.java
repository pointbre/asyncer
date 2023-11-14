package com.github.pointbre.asyncer.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.github.pointbre.asyncer.util.AsyncerUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.core.publisher.Sinks.One;
import reactor.util.concurrent.Queues;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

public class AsyncerImpl implements Asyncer {

    private final HashSet<State> states;
    private final State initialState;
    private final State finalState;
    private final HashSet<Event> events;
    private final HashSet<Transition> transitions;

    private final List<Tuple3<UUID, Event, One<Result>>> requests = new ArrayList<>();
    private final Thread eventHandler;

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

	this.eventHandler = Thread.ofVirtual().name("Asyncer-EventHandler").start(() -> {

	    while (!Thread.currentThread().isInterrupted()) {

		Iterator<Tuple3<UUID, Event, One<Result>>> iterator = requests.iterator();

		while (iterator.hasNext()) {

		    // UUID has an information about date/time
		    Tuple3<UUID, Event, One<Result>> request = iterator.next();

		    UUID requestUUID = request.getT1(); // FIXME what to do with this?
		    Event requestEvent = request.getT2();
		    One<Result> requestResultSink = request.getT3();

		    // FIXME if event is changed to enum, I don't need to worry about this???
		    if (!events.contains(requestEvent)) {
			requestResultSink.tryEmitValue(new Result(Result.Type.FAILED, requestEvent + " is not included in the allowed event list"));
			iterator.remove();
		    }

		    if (currentState.equals(finalState)) {
			requestResultSink.tryEmitValue(new Result(Result.Type.FAILED, currentState + " is a final state"));
			iterator.remove();
		    }

		    Optional<Transition> matchingTransition = transitions.stream()
			    .filter(t -> t.getFrom().equals(currentState) && t.getEvent().equals(requestEvent))
			    .findFirst();
		    if (!matchingTransition.isPresent()) {
			requestResultSink.tryEmitValue(new Result(Result.Type.FAILED, "No matching transition found from " + currentState + " triggered by " + requestEvent));
			iterator.remove();
		    }
		    Transition transition = matchingTransition.get();

		    Action action = null;
		    Executor executor = null;
		    Tuple3<State, Result, List<Result>> transitionResult = null;
		    State updatedState = null;
		    Result result = null;
		    if (transition instanceof StaticTransition t) {
			action = t.getAction();
			if (action != null) { // could be null
			    executor = Executor.of(t.getAction().getExecutor());
			    transitionResult = executor.runUntil(transition, Instant.now().plusSeconds(10)); // FIXME 10 should be from Transition
			}
			updatedState = t.getTo();
			result = new Result(Result.Type.PROCESSED, null);
		    } else if (transition instanceof DynamicTransition t) {
			action = t.getAction();
			if (action == null) {
			    // Shouldn't be null
			}
			executor = Executor.of(t.getAction().getExecutor());
			transitionResult = executor.runUntil(transition, Instant.now().plusSeconds(10)); // FIXME 10 should be from Transition
			updatedState = transitionResult.getT1();
			result = transitionResult.getT2();
		    }

		    if (transitionResult != null) {
			transitionResult.getT3().forEach(r -> System.out.println("executor result=" + r));
		    }

		    if (updatedState != null && !currentState.equals(updatedState)) {
			currentState = updatedState;
			stateSink.tryEmitNext(updatedState);
		    }

		    requestResultSink.tryEmitValue(result);
		    iterator.remove();
		}

		try {
		    Thread.sleep(Duration.ofMillis(100));
		} catch (InterruptedException e) {
		    System.out.println("Interrupted!");
		    break;
		}
	    }
	});
	this.currentState = initialState;

	stateSink.tryEmitNext(initialState);
    }

    @Override
    public Mono<Result> fire(Event event) {

	final One<Result> resultSink = Sinks.one();

	requests.add(Tuples.of(AsyncerUtil.generateType1UUID(), event, resultSink));

	return resultSink.asMono();
    }

    @Override
    public Flux<State> state() {
	return stateSink.asFlux();
    }

    @Override
    public void close() throws Exception {
	eventHandler.interrupt();
    }
}
