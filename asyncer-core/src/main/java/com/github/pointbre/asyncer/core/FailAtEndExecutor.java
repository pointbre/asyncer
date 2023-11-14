package com.github.pointbre.asyncer.core;

import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeoutException;

import com.github.pointbre.asyncer.core.Asyncer.Action;
import com.github.pointbre.asyncer.core.Asyncer.DynamicTransition;
import com.github.pointbre.asyncer.core.Asyncer.Executor;
import com.github.pointbre.asyncer.core.Asyncer.Result;
import com.github.pointbre.asyncer.core.Asyncer.State;
import com.github.pointbre.asyncer.core.Asyncer.StaticTransition;
import com.github.pointbre.asyncer.core.Asyncer.Transition;
import com.github.pointbre.asyncer.core.Asyncer.Result.Type;

import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

public non-sealed class FailAtEndExecutor extends Executor {
    
    private final Queue<Subtask<? extends Result>> tasks = new LinkedTransferQueue<>();

    @Override
    protected void handleComplete(Subtask<? extends Result> task) {
	
	tasks.add(task);
	
    }

    @Override
    public Tuple3<State, Result, List<Result>> run(Transition transition) {
	
	State state = null;
	Result result = null;
	List<Result> results = null;

	Action action = null;
	if (transition instanceof StaticTransition t) {
	    action = t.getAction();
	} else if (transition instanceof DynamicTransition t) {
	    action = t.getAction();
	    if (action == null) {
		// Return result
	    }
	}

	if (action != null) {
	    action.getTasks().stream().forEach(task -> fork(task));

	    try {
		joinUntil(Instant.now().plusSeconds(5));
//		join();
	    } catch (InterruptedException e) {
		//
	    } catch (TimeoutException e) {
		//
	    }
	}

	ensureOwnerAndJoined();
	
	Asyncer.Result.Type resultType1 = Asyncer.Result.Type.FAILED;
	if (transition instanceof StaticTransition t) {
	    state = t.getTo();
	    resultType1 = Asyncer.Result.Type.PROCESSED;
	} else if (transition instanceof DynamicTransition t) {
	    if (isAllExecutedSuccessfully(tasks)) {
		state = t.getToWhenProcessed();
		resultType1 = Asyncer.Result.Type.PROCESSED;
	    } else {
		state = t.getToWhenFailed();
	    }	    
	}
	result = new Asyncer.Result(resultType1, ""); // FIXME Is description required?
	
	results = tasks.stream().map(task -> {
	    Asyncer.Result.Type resultType2 = Asyncer.Result.Type.FAILED;
	    if (isExecutedSuccessfully(task)) {
		resultType2 = Asyncer.Result.Type.PROCESSED;
	    }
	    return new Asyncer.Result(resultType2, ""); // FIXME Is description required?
	}).toList();
	
	return Tuples.of(state, result, results);
    }
    
    private boolean isExecutedSuccessfully(Subtask<? extends Result> task) {
	return task.state().equals(Subtask.State.SUCCESS) && task.get().getType().equals(Result.Type.PROCESSED);
    }
    
    private boolean isAllExecutedSuccessfully(Queue<Subtask<? extends Result>> tasks) {
	return tasks.stream().allMatch(task -> isExecutedSuccessfully(task));
    }
}