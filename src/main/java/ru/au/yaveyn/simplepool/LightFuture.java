package ru.au.yaveyn.simplepool;

import java.util.function.Function;

public interface LightFuture<R> {

    Boolean isReady();
    R get() throws LightExecutionException, InterruptedException;
    <T> LightFuture<T> thanApply(Function<R, T> apply);
}