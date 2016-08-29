package com.github.patrickianwilson.concurrency.util;

import java.util.concurrent.Callable;

/**
 * Created by pwilson on 8/29/16.
 */
public abstract class ChainedCallable<I, O> implements Callable<O> {

    protected I input;
    protected Throwable t;

    public I getInput() {
        return input;
    }

    public void setInput(I input) {
        this.input = input;
    }

    public Throwable getException() {
        return t;
    }

    public void setException(Throwable t) {
        this.t = t;
    }


}
