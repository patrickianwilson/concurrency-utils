package com.github.patrickianwilson.concurrency.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by pwilson on 8/29/16.
 */
public class ChainedTask<T> implements Runnable {
    private ChainedTask<?> next;
    private final AtomicInteger outstandingTasks;
    private final AtomicBoolean chainComplete;

    private final Callable<T> work;
    private ExecutorService executor;
    private final List<Callable> exceptionHandler = new ArrayList<>();
    private Object input;

    public ChainedTask(Callable work, ExecutorService nextExecutor, AtomicInteger outstandingTasks, AtomicBoolean chainComplete) {
        this.work = work;
        this.executor = nextExecutor;
        this.chainComplete = chainComplete;
        this.outstandingTasks = outstandingTasks;

    }

    public void setPool(ExecutorService pool) {
        this.executor = pool;
    }

    public final static class Builder<OUTCOME> {

        private ChainedTask<OUTCOME> head = null;
        private ChainedTask<?> tail = null;
        private ExecutorService mainExecutor;

        private Builder(ExecutorService e) {
            this.mainExecutor = e;
        }

        public static <OUTCOME> Builder<OUTCOME> newBuilder(ExecutorService e) {
            return new Builder<>(e);
        }

        public Builder<OUTCOME> startsWith(ChainedCallable<?, ?> head) {
            this.head = new ChainedTask<>(head, this.mainExecutor, new AtomicInteger(0), new AtomicBoolean(false));
            this.tail = this.head;
            return this;
        }

        public Builder<OUTCOME> thenIfSuccessful(ChainedCallable<?, ?> next) {
            this.tail.next = new ChainedTask(next, this.mainExecutor, this.head.outstandingTasks, this.head.chainComplete);
            this.tail= this.tail.next;
            return this;
        }


        public Builder<OUTCOME> andRunInPool(ExecutorService pool) {
            this.tail.setPool(pool);
            return this;
        }

        public Builder<OUTCOME> butIfException(ChainedCallable<?, ?> handler) {
            this.tail.exceptionHandler.add(handler);
            return this;
        }

        public ChainedTask<OUTCOME> build() {
            return this.head;
        }



    }

    @Override
    public void run() {
        defer();
    }

    public Future<T> defer() {
        this.outstandingTasks.incrementAndGet();
        return this.executor.submit(new Callable<T>() {
            @Override
            public T call() throws Exception {
                try {
                    if (input != null && work instanceof ChainedCallable) {
                        ((ChainedCallable) work).setInput(input);
                    }
                    T output = ChainedTask.this.work.call();

                    if (next != null) {
                        next.input = output;
                        next.defer();
                    }
                    return output;
                } catch (Throwable t) {
                    for (Callable<?> handler: exceptionHandler) {
                        try {
                            if (handler instanceof ChainedCallable) {
                                ((ChainedCallable) handler).setException(t);
                            }
                            handler.call();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    return null;
                } finally {
                    int remaining = outstandingTasks.decrementAndGet();
                    if (remaining <= 0) {
                        synchronized(chainComplete){
                            chainComplete.set(true);
                            chainComplete.notifyAll();
                        }
                    }
                }
            }
        });
    }

    public void getFullChain() throws InterruptedException {
        while (!this.chainComplete.get()) {
            synchronized (chainComplete) {
                chainComplete.wait();
            }
        }

        return;

    }

}
