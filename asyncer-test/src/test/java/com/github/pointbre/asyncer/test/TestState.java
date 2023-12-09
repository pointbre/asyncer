package com.github.pointbre.asyncer.test;

import com.github.pointbre.asyncer.core.Asyncer.State;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class TestState extends State<TestState.Type> {

    public enum Type {
        STARTING, STARTED, STOPPING, STOPPED;
    }

    public TestState(Type type) {
        super(type);
    }

    @Override
    public String toString() {
        return this.getType().name();
    }

}
