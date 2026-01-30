package com.hayden.utilitymodule.concurrent.striped;

import com.google.common.util.concurrent.Striped;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.SourceLocation;
import org.aspectj.runtime.internal.AroundClosure;
import org.junit.jupiter.api.Test;

import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;

class StripedLockAspectTest {

    @Test
    public void testStripedLock() {
        Striped<Lock> lockStriped = Striped.lock(1024);
        lockStriped.get("hello").lock();
        lockStriped.get("hello").lock();
        lockStriped.get("hello").unlock();

    }

}