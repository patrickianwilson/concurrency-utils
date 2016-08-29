package com.github.patrickianwilson.concurrency.util;

import java.util.concurrent.Callable;

/**
 * Created by pwilson on 8/29/16.
 */
public interface ChainedCallable<O>{

    O call(Object input) throws InterruptedException;

}
