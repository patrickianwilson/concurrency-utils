# concurrency-utils
Some useful threading and concurrency pattern building classes for java.

Why Does Java Need Another Library For Concurrency??
----------------------------------------------------

There is a startlingly small amount of java community work that does the simplest 80% of concurrency work as simply
as possible.  A number of primitives exist in the JDK directly (ExecutorService, etc) and Java 8 has added some good support for Promises
but I have found myself building the following simple classes over and over and thus felt maybe the rest of the community was also
struggling with simple (but non-trivial) concurrency.  This library simplifies the following use cases:
 
 * Creating a chain of tasks where each task that depend on the output from the previous task.
 * Tracking all tasks to completion 
 * Running tasks on different thread pools - useful for safely maximizing usage of a non scalable resource.
 
How To Use
----------

The following code demonstrates how to use the ChainedTask Builder class.

```java
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
```






