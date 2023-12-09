package com.github.pointbre.asyncer.test;

import com.github.pointbre.asyncer.core.Asyncer.Event;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class TestEvent extends Event<TestEvent.Type> {

    public enum Type {
        START, STOP, SEND;
    }

    String message;

    public TestEvent(Type type) {
        this(type, null);
    }

    public TestEvent(Type type, String message) {
        super(type);
        this.message = message;
    }

    @Override
    public String toString() {
        return this.getType().name() + (message == null ? "" : ": " + message);
    }
}