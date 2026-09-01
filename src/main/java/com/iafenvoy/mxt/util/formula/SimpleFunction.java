package com.iafenvoy.mxt.util.formula;

import net.objecthunter.exp4j.function.Function;

public class SimpleFunction extends Function {
    private final FunctionImplementation implementation;

    public SimpleFunction(String name, int numArguments, FunctionImplementation implementation) {
        super(name, numArguments);
        this.implementation = implementation;
    }

    @Override
    public double apply(double... args) {
        return this.implementation.apply(args);
    }

    public interface FunctionImplementation {
        double apply(double... args);
    }
}
