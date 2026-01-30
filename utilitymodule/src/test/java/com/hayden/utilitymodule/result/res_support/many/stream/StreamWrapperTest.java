package com.hayden.utilitymodule.result.res_support.many.stream;

import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.error.SingleError;
import com.hayden.utilitymodule.result.res_many.StreamResultItem;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class StreamWrapperTest {



    @Test
    public void doTestOk() {
        Stream<Result<String, Object>> isOk = Stream.of(Result.ok(""));
        StreamResult<String, Object> ok = (StreamResult<String, Object>) Result.from(isOk);
        var t = new StreamResult.StreamResultStreamWrapper<>(StreamResultOptions.builder().build(), isOk, ok);
        // terminal op
        t.forEach(System.out::println);

        assertFalse(t.isCompletelyEmpty(ok));
        assertTrue(t.isAnyNonNull(ok));
        assertTrue(t.hasAnyResult(ok));
        assertFalse(t.hasAnyError(ok));

        var okFound = t.getOk();

        assertNotNull(okFound);
        assertEquals("", okFound.get());
    }

    @Test
    public void doTestErr() {
        Stream<Result<String, SingleError.StandardError>> isOk = Stream.of(Result.err(new SingleError.StandardError("hello")));
        StreamResult<String, SingleError.StandardError> ok = (StreamResult<String, SingleError.StandardError>) Result.from(isOk);
        var t = new StreamResult.StreamResultStreamWrapper<>(StreamResultOptions.builder().build(), isOk, ok);
        // terminal op
        t.forEach(System.out::println);

        assertFalse(t.isCompletelyEmpty(ok));
        assertTrue(t.isAnyNonNull(ok));
        assertFalse(t.hasAnyResult(ok));
        assertTrue(t.hasAnyError(ok));

        var okFound = t.getErr();

        assertNotNull(okFound);
        assertEquals("hello", okFound.get().getMessage());
    }

}