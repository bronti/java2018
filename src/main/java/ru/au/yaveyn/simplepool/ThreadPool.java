package ru.au.yaveyn.simplepool;

import java.util.function.Supplier;

public interface ThreadPool {

    <R> LightFuture<R> submit(Supplier<R> getResult);
    void shutdown();
}