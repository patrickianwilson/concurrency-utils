package com.github.patrickianwilson.concurrency.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by pwilson on 8/29/16.
 */
public class ChainedTaskBuilderJava8Tests {

    @Test
    public void verifyTasksAreExecutedAsExpectedWhenWrittenInLambdas() {
        final AtomicBoolean signal = new AtomicBoolean(false);
        final ExecutorService mainPool = Executors.newFixedThreadPool(2);
        ChainedTask task = ChainedTask.Builder.newBuilder(mainPool)
                .startsWith( input -> {
                    System.out.println("step 1");
                    return 1;
                })
                .thenIfSuccessful(input -> {
                        System.out.println("Long running task.  will sleep for 5 seconds.");
                        Thread.sleep(5000);
                        System.out.println (String.format("Sleep done - step 2 - using input: %s", input));
                        return ((Integer)input).intValue() + 1;
                })
                .butIfException(input -> {
                        System.out.println("An exception occured.");
                })
                .thenIfSuccessful(input -> {
                        System.out.println (String.format("step 3 - using input: %s", input));
                        signal.set(true);
                        return "success";

                })
                .butIfException(exception -> {
                        System.out.println("An exception occured." +  exception.getLocalizedMessage());
                    exception.printStackTrace();

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
