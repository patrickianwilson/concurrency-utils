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

The following code demonstrates how to use the ChainedTask Builder class (using Java <= 1.7).

```java
 final AtomicBoolean signal = new AtomicBoolean(false);
        final ExecutorService mainPool = Executors.newFixedThreadPool(2);
        ChainedTask task = ChainedTask.Builder.newBuilder(mainPool)
                .startsWith(new ChainedCallable<Integer>() {
                    @Override
                    public Integer call(Object input) throws InterruptedException {
                        System.out.println("step 1");
                        return 1;
                    }


                })
                .thenIfSuccessful(new ChainedCallable<Integer>() {
                    @Override
                    public Integer call(Object input) throws InterruptedException {
                        System.out.println("Long running task.  will sleep for 5 seconds.");
                        Thread.sleep(5000);
                        System.out.println (String.format("Sleep done - step 2 - using input: %s", input));
                        return ((Integer)input).intValue() + 1;
                    }
                })
                .butIfException(new ChainedExceptionHandler() {
                    @Override
                    public void call(Throwable t) throws InterruptedException {
                        System.out.println("An exception occured.");
                        return;
                    }
                })
                .thenIfSuccessful(new ChainedCallable<String>() {
                    @Override
                    public String call(Object input) {

                        System.out.println (String.format("step 3 - using input: %s", input));
                        signal.set(true);
                        return "success";
                    }
                })
                .butIfException(new ChainedExceptionHandler() {
                    @Override
                    public void call(Throwable throwable) {
                        System.out.println("An exception occured: " + throwable.getMessage());
                        throwable.printStackTrace();
                        return;
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

The library is also specifically designed with Lambdas in mind. The equivalent Java 8 code is much simpler:

```java
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
```





