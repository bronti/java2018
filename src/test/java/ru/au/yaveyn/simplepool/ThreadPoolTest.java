package ru.au.yaveyn.simplepool;


import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPoolTest {

    private static int NUM_OF_RUNS = 1000;

    private ThreadPool tp;
    private ArrayList<LightFuture<Integer>> futures;
    private volatile AtomicInteger counter;

    @Before
    public void setUp() throws Exception {
        tp = new ThreadPoolImpl(4);
        futures = new ArrayList<>(NUM_OF_RUNS);
        counter = new AtomicInteger(0);
    }

    @After
    public void tearDown() throws Exception {
        if (tp != null) {
            tp.shutdown();
            tp = null;
        }
        futures = null;
        counter= null;
    }

    @Test
    public void testAllTasksDoneAndGot() throws LightExecutionException, InterruptedException {
        for (int i = 0; i < NUM_OF_RUNS; ++i) {
            final int k =  i;
            futures.add(tp.submit(() -> {
                counter.incrementAndGet();
                return k * k;
            }));
        }
        for (int i = 0; i < NUM_OF_RUNS; ++i) {
            Assert.assertEquals(new Integer(i * i), futures.get(i).get());
        }
        Assert.assertEquals(NUM_OF_RUNS, counter.get());
    }

    @Test
    public void testShutdown() throws LightExecutionException, InterruptedException {
        for (int i = 0; i < NUM_OF_RUNS; ++i) {
            tp.submit(counter::incrementAndGet);
        }
        int threadCount = Thread.activeCount();
        tp.shutdown();
        Thread.sleep(3000);
        Assert.assertEquals(threadCount - 4, Thread.activeCount());
    }

    @Test
    public void testExceptionFromSupplier() {
        boolean exceptionThrown = false;
        try {
            tp.submit(() -> Integer.parseInt("239 tiny mice")).get();
        } catch (Throwable e) {
            exceptionThrown = true;
            Assert.assertEquals(LightExecutionException.class, e.getClass());
            Assert.assertEquals(NumberFormatException.class, e.getCause().getClass());
        }
        Assert.assertTrue(exceptionThrown);
    }

    @Test
    public void testThanApply() throws LightExecutionException,  InterruptedException {
        LightFuture<Integer> lf = tp.submit(() -> {
            try {
                Thread.sleep(100);
            } catch (Throwable e) {
                return 0;
            }
            return 5;
        }).thanApply((i) -> i + 15).thanApply((i) -> i * 2).thanApply((i) -> i + 2);
        Assert.assertEquals(42, lf.get().intValue());
    }

    @Test
    public void testIsReady() throws LightExecutionException,  InterruptedException {
        LightFuture<Integer> lf = tp.submit(() -> {
            try {
                Thread.sleep(1000);
            } catch (Throwable e) {
                return 0;
            }
            return 5;
        });
        Assert.assertFalse(lf.isReady());
        lf.get();
        Assert.assertTrue(lf.isReady());
    }
}