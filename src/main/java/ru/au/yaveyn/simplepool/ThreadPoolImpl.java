package ru.au.yaveyn.simplepool;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public class ThreadPoolImpl implements ThreadPool {

    private final List<Thread> threads;
    private final Queue<Runnable> tasks = new LinkedList<>();

    public ThreadPoolImpl(int n) {
        threads = new ArrayList<>(n);
        for (int i = 0; i < n; ++i) {
            Thread workerThread = new Thread(new Worker());
            threads.add(workerThread);
        }
        threads.forEach(Thread::start);
    }

    @Override
    public <R> LightFuture<R> submit(Supplier<R> supplier) {
        LightFutureImpl<R> future = new LightFutureImpl<>(this);
        Runnable task = packSupplier(supplier, future);
        queueTask(task);
        return future;
    }

    private void queueTask(Runnable task) {
        synchronized (tasks) {
            tasks.add(task);
            tasks.notify();
        }
    }

    @Override
    public void shutdown() {
        synchronized (tasks) {
            tasks.clear();
        }
        threads.forEach(Thread::interrupt);
    }

    private static <R> Runnable packSupplier(Supplier<R> supplier, LightFutureImpl<R> future) {
        return () -> {
            try {
                future.feedResult(supplier.get());
            }
            catch (Throwable e) {
                future.feedThrowable(e);
            }
        };
    }

    private class Worker implements Runnable{
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                Runnable currentTask;
                synchronized (tasks) {
                    while (tasks.isEmpty()) {
                        try {
                            tasks.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                    currentTask = tasks.poll();
                }
                currentTask.run();
            }
        }
    }

    private class LightFutureImpl<R> implements LightFuture<R> {

        private static final long MILLIS_TO_WAIT_FOR_PREV_TASK = 3;

        private final ThreadPoolImpl pool;

        volatile private boolean isReady;
        private boolean finishedExceptionally;

        private R result;
        private Throwable exceptionalResult;

        LightFutureImpl(ThreadPoolImpl pool) {
            this.pool = pool;
        }

        private <T> LightFutureImpl(LightFutureImpl<T> prevTask, Function<T, R> transformation) {
            this.pool = prevTask.pool;

            Runnable newTask = new Runnable() {
                @Override
                public void run() {
                    try {
                        Optional<T> prevResult = prevTask.getWithTimeout(MILLIS_TO_WAIT_FOR_PREV_TASK);
                        if (prevResult.isPresent()) {
                            LightFutureImpl.this.feedResult(transformation.apply(prevResult.get()));
                        }
                        else {
                            pool.queueTask(this);
                        }
                    }
                    catch (Throwable e) {
                        LightFutureImpl.this.feedThrowable(e);
                    }
                }
            };

            pool.queueTask(newTask);
        }

        @Override
        public Boolean isReady() {
            return isReady;
        }

        @Override
        public synchronized R get() throws LightExecutionException, InterruptedException {
            while (!isReady) {
                wait();
            }
            return doGet();
        }

        private synchronized Optional<R> getWithTimeout(long millis) throws LightExecutionException, InterruptedException {
            if (!isReady) {
                wait(millis);
            }
            if (isReady) {
                return Optional.of(doGet());
            } else {
                return Optional.empty();
            }
        }

        private R doGet() throws LightExecutionException {
            if (finishedExceptionally) throw new LightExecutionException(exceptionalResult);
            return result;
        }

        @Override
        public <T> LightFuture<T> thanApply(Function<R, T> transformation) {
            return new LightFutureImpl<>(this, transformation);
        }

        void feedResult(R result) {
            doFeed(result, null, false);
        }

        void feedThrowable(Throwable result) {
            doFeed(null, result, true);
        }

        private synchronized void doFeed(R result, Throwable exceptionalResult, Boolean finishedExceptionally) {
            assert(!isReady);
            this.result = result;
            this.exceptionalResult = exceptionalResult;
            this.finishedExceptionally = finishedExceptionally;
            isReady = true;
            notify();
        }
    }

}