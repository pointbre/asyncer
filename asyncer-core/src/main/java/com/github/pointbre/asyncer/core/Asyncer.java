package com.github.pointbre.asyncer.core;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;
import reactor.util.function.Tuple3;

public interface Asyncer extends AutoCloseable {

    Mono<Result> fire(Event event);

    Flux<State> state();

    @Value
    public class State {

	@NonNull
	String name;

    }

    @Value
    public class Event {
	
	@NonNull
	String name;

    }
    
    @ToString
    @EqualsAndHashCode
    @AllArgsConstructor
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @Getter
    public sealed class Transition permits StaticTransition, DynamicTransition {
	
	@NonNull
	String name;

	@NonNull
	State from;

	@NonNull
	Event event;

    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    public class StaticTransition extends Transition {

	@Nullable
	Action action;

	@Nullable
	State to;

	public StaticTransition(@NonNull String name, @NonNull State from, @NonNull Event event, Action action, State to) {
	    super(name, from, event);
	    this.action = action;
	    this.to = to;
	}

    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    public class DynamicTransition extends Transition {
	@NonNull
	Action action;
	
	@NonNull
	State toWhenProcessed;

	@NonNull
	State toWhenFailed;

	public DynamicTransition(@NonNull String name, @NonNull State from, @NonNull Event event, @NonNull Action action,
		@NonNull State toWhenProcessed, @NonNull State toWhenFailed) {
	    super(name, from, event);
	    this.action = action;
	    this.toWhenProcessed = toWhenProcessed;
	    this.toWhenFailed = toWhenFailed;
	}

    }

    @Value
    public class Action {

	@NonNull
	String name;

	@NonNull
	List<Callable<Result>> tasks;
	
	@NonNull
	Class<? extends Executor> executor;
	
	@Nullable
	Instant deadline;

    }
    
    @Value
    public class Result {

	public enum Type {
	    PROCESSED, FAILED
	}
	
	@NonNull
	Type type;

	@NonNull
	String description;

	public Result(Type type, String description) {
	    this.type = type;
	    this.description = description;
	}

    }
    
    // FIXME FailNever, FailFast, FailAtEnd
    public abstract sealed class Executor extends StructuredTaskScope<Result> permits FailAtEndExecutor {

	public abstract Tuple3<State, Result, List<Result>> runUntil(Transition transition) throws InterruptedException;

	public static Executor of(Class<? extends Executor> executor) {
	    if (executor.equals(FailAtEndExecutor.class)) {
		return new FailAtEndExecutor();
	    }
	    return new FailAtEndExecutor();
	}
	
    }
 
}
