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
public class ChainedTaskedBuilder<T> implements Runnable {
    private ChainedTaskedBuilder<?> next;
    private final AtomicInteger outstandingTasks;
    private final AtomicBoolean chainComplete;

    private final Callable<T> work;
    private final ExecutorService executor;
    private final List<Callable> exceptionHandler = new ArrayList<>();
    private Object input;

    public ChainedTaskedBuilder(Callable work, ExecutorService nextExecutor, AtomicInteger outstandingTasks, AtomicBoolean chainComplete) {
        this.work = work;
        this.executor = nextExecutor;
        this.chainComplete = chainComplete;
        this.outstandingTasks = outstandingTasks;

    }

    public final static class ChainBuilder<OUTCOME> {

        private ChainedTaskedBuilder<OUTCOME> head = null;
        private ChainedTaskedBuilder<?> tail = null;
        private ExecutorService mainExecutor;

        private ChainBuilder(ExecutorService e) {
            this.mainExecutor = e;
        }

        public static <OUTCOME> ChainBuilder<OUTCOME> newBuilder(ExecutorService e) {
            return new ChainBuilder<>(e);
        }

        public ChainBuilder<OUTCOME> startsWith(Callable<?> head) {
            this.head = new ChainedTaskedBuilder<>(head, this.mainExecutor, new AtomicInteger(0), new AtomicBoolean(false));
            this.tail = this.head;
            return this;
        }

        public ChainBuilder<OUTCOME> thenIfSuccessful(Callable<?> next) {
            this.tail.next = new ChainedTaskedBuilder(next, this.mainExecutor, this.head.outstandingTasks, this.head.chainComplete);
            this.tail= this.tail.next;
            return this;
        }

        public ChainBuilder<OUTCOME> thenIfSuccessful(Callable<?> next, ExecutorService pool) {
            this.tail.next = new ChainedTaskedBuilder(next, pool, this.head.outstandingTasks, this.head.chainComplete);
            this.tail = this.tail.next;
            return this;
        }

        public ChainBuilder<OUTCOME> thenIfException(Callable<?> handler) {
            this.tail.exceptionHandler.add(handler);
            return this;
        }

        public ChainedTaskedBuilder<OUTCOME> build() {
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
                    T output = ChainedTaskedBuilder.this.work.call();

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

    public interface ChainedCallable<I,O> extends Callable<O> {
        public void setInput(I input);
        public void setException(Throwable t);
    }

    public static abstract class BaseChainedCallable<I, O> implements ChainedCallable<I, O> {
        protected I input;
        protected Throwable t;

        @Override
        public void setInput(I input) {
            this.input = input;
        }

        public I getInput() {
            return input;
        }

        public Throwable getException() {
            return t;
        }

        public void setException(Throwable t) {
            this.t = t;
        }
    }

}
