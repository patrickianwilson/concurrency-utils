package com.github.patrickianwilson.concurrency.util;

/**
 * Created by pwilson on 8/29/16.
 */
public interface ChainedExceptionHandler {


    void call(Throwable t) throws InterruptedException;


}
