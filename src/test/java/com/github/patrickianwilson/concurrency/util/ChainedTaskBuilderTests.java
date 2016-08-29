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
                .startsWith(new Callable<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        System.out.println("step 1");
                        return 1;
                    }


                })
                .thenIfSuccessful(new <Integer, Integer>ChainedTask.BaseChainedCallable() {
                    @Override
                    public Integer call() throws Exception {
                        System.out.println("Long running task.  will sleep for 5 seconds.");
                        Thread.sleep(5000);
                        System.out.println (String.format("Sleep done - step 2 - using input: %s", this.input));
                        return ((Integer)this.input).intValue() + 1;
                    }
                })
                .thenIfException(new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        System.out.println("An exception occured.");
                        return null;
                    }
                })
                .thenIfSuccessful(new <Integer, String>ChainedTask.BaseChainedCallable() {
                    @Override
                    public String call() throws Exception {

                        System.out.println (String.format("step 3 - using input: %s", this.input));
                        signal.set(true);
                        return "success";
                    }
                })
                .thenIfException(new Callable<Object>() {
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
