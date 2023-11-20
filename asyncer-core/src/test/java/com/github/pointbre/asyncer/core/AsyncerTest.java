package com.github.pointbre.asyncer.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.pointbre.asyncer.core.Asyncer.Result;

@ExtendWith(MockitoExtension.class)
public class AsyncerTest {
    @Test
    void test() throws Exception {

	var locked = new Asyncer.State("locked");
	var unlocked = new Asyncer.State("unlocked");
	var states = new HashSet<Asyncer.State>();
	states.add(locked);
	states.add(unlocked);

	var coin = new Asyncer.Event("Coin");
	var push = new Asyncer.Event("Push");
	var events = new HashSet<Asyncer.Event>();
	events.add(coin);
	events.add(push);

//	var lockedToUnlocked = new Asyncer.StaticTransition(locked, coin, null, unlocked);
	List<Callable<Result>> tasks1 = new ArrayList<>(Arrays.asList(
		() -> {
		    System.out.println(">>>1 task #1 of action");
		    System.out.println(">>>1 Taking 3 seconds sleep");
		    try {
			Thread.sleep(Duration.ofSeconds(3));
		    } catch (InterruptedException e) {	
//			e.printStackTrace();
		    }
		    System.out.println(">>>1 Will return result now");
		    return new Asyncer.Result(Asyncer.Result.Type.PROCESSED, null);
		},
		
		() -> {
		    System.out.println(">>>2 task #2 of action");
		    System.out.println(">>>2 Taking 4 seconds sleep");
		    try {
			Thread.sleep(Duration.ofSeconds(4));
		    } catch (InterruptedException e) {		
//			e.printStackTrace();
		    }
		    System.out.println(">>>2 Will return result now");
		    return new Asyncer.Result(Asyncer.Result.Type.PROCESSED, null);
		}
	));
	var action1 = new Asyncer.Action("action1", FailAtEndExecutor.class, tasks1);	
	var lockedToUnlocked = new Asyncer.DynamicTransition(locked, coin, action1, unlocked, locked);
	
	var lockedToLocked = new Asyncer.StaticTransition(locked, push, null, locked);

	//	var unlockedToLocked = new Asyncer.StaticTransition(unlocked, push, null, locked);
	List<Callable<Result>> tasks2 = new ArrayList<>(Arrays.asList(
		() -> {
		    System.out.println(">>>1 task #1 of action");
		    System.out.println(">>>1 Taking 3 seconds sleep");
//		    try {
//			Thread.sleep(Duration.ofSeconds(3));
//		    } catch (InterruptedException e) {
////			e.printStackTrace();
//		    }
		    System.out.println(">>>1 Will return result now");
		    return new Asyncer.Result(Asyncer.Result.Type.PROCESSED, null);
		}
	));
	var action2 = new Asyncer.Action("action2", FailAtEndExecutor.class, tasks2);
	var unlockedToLocked = new Asyncer.StaticTransition(unlocked, push, action2, locked);

	var unlockedToUnlocked = new Asyncer.StaticTransition(unlocked, coin, null, unlocked);
	var transitions = new HashSet<Asyncer.Transition>();
	transitions.add(lockedToUnlocked);
	transitions.add(lockedToLocked);
	transitions.add(unlockedToLocked);
	transitions.add(unlockedToUnlocked);

	CountDownLatch latch = new CountDownLatch(2);
	try (var asyncer = new AsyncerImpl(states, locked, null, events, transitions)) {
	    
	    System.out.println("1");
	    
	    asyncer.state().subscribe(s -> {
		System.out.println("state updated=" + s);
//		latch.countDown();
	    }, e -> {
		System.out.println("state error=" + e);
//		latch.countDown();
	    });

	    System.out.println("2");

	    long time1 = System.currentTimeMillis();
	    asyncer.fire(coin).subscribe(r -> {
		System.out.println("fire coin finished=" + r);
//		latch.countDown();
	    }, e -> {
		System.out.println("fire coin error=" + e);
//		latch.countDown();
	    });
	    
	    System.out.println("3");
	    
	    IntStream.range(1, 10).forEach(n -> {
		    asyncer.fire(push).subscribe(r -> {
//			System.out.println("fire push " + n + " finished=" + r);
//			latch.countDown();
		    }, e -> {
//			System.out.println("fire push " + n + " error=" + e);
//			latch.countDown();
		    });
	    });
	    
	    System.out.println("4");	    

	    long time2 = System.currentTimeMillis();
	    
	    System.out.println("5 --> " + (time2 - time1));
	    
	    try {
		latch.await(5, TimeUnit.SECONDS);
//		latch.await();
	    } catch (InterruptedException e) {

	    } finally {

	    }
	    
	    System.out.println("6");
	}
	
	System.out.println("7");
    }
}
