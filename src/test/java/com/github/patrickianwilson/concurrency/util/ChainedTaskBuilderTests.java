package com.github.patrickianwilson.concurrency.util;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by pwilson on 8/29/16.
 */
public class ChainedTaskBuilderTests {

    @Test
    public void verifyTasksAreExecutedAsExpected() {
        final AtomicBoolean signal = new AtomicBoolean(false);
        final ExecutorService mainPool = Executors.newFixedThreadPool(2);
        ChainedTask task = ChainedTask.Builder.newBuilder(mainPool)
                .startsWith(new ChainedCallable<Void, Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        System.out.println("step 1");
                        return 1;
                    }


                })
                .thenIfSuccessful(new ChainedCallable<Integer, Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        System.out.println("Long running task.  will sleep for 5 seconds.");
                        Thread.sleep(5000);
                        System.out.println (String.format("Sleep done - step 2 - using input: %s", this.input));
                        return this.input.intValue() + 1;
                    }
                })
                .butIfException(new ChainedCallable<Integer, Void>() {
                    @Override
                    public Void call() throws Exception {
                        System.out.println("An exception occured.");
                        return null;
                    }
                })
                .thenIfSuccessful(new ChainedCallable<Integer, String>() {
                    @Override
                    public String call() throws Exception {

                        System.out.println (String.format("step 3 - using input: %s", this.input));
                        signal.set(true);
                        return "success";
                    }
                })
                .butIfException(new ChainedCallable<String, Object>() {
                    @Override
                    public Object call() throws Exception {
                        System.out.println("An exception occured.");
                        return null;
                    }
                })
                .build();
        task.defer();
        try {
            task.getFullChain(); //resolve all futures (blocks the test thread)
            mainPool.shutdown();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Assert.assertTrue("Signal was changed as part of finalizer task.", signal.get());
    }
}
