package com.hayden.utilitymodule.result;

import com.hayden.utilitymodule.result.agg.AggregateError;
import com.hayden.utilitymodule.result.error.SingleError;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.hayden.utilitymodule.result.ResultTestModel.singleMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class ResultTest {

    @Test
    public void testOr() {
        var found = Result.<String, SingleError>ok((String) null)
                .flatMapResult(e -> Result.ok("hello"))
                .hasAnyOr(() -> Result.ok("whatever"));

        assertThat(found.r().get()).isEqualTo("whatever");

       found = Result.<String, SingleError>ok((String) null)
                .flatMapResult(e -> Result.ok((String) null))
                .hasAnyOr(() -> Result.ok("whatever"));

       found = Result.<String, SingleError>ok((String) null)
                .flatMapResult(e -> Result.ok((String) null))
                .map(w -> "okay then...")
                .hasAnyOr(() -> Result.ok("whatever"));

        assertThat(found.r().get()).isEqualTo("whatever");
    }

    @SneakyThrows
    @Test
    public void autoClosable() {
        AtomicInteger i = new AtomicInteger(0);
        Result.<FileInputStream, SingleError>tryFrom(
                        new FileInputStream("build.gradle.kts"),
                        () -> {
                            i.getAndIncrement();
                            return null;
                        })
                .ifPresent(fi -> {
                    assertThat(ClosableResult.hasOpenResources()).isTrue();
                    assertEquals(1, i.incrementAndGet());
                    try {
                        assertNotEquals(0, fi.available());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        assertEquals(2, i.get());

        assertThat(ClosableResult.hasOpenResources()).isFalse();

        var j = new AtomicInteger();
        var q = new AtomicInteger();
        Result.<FileInputStream, SingleError>tryFrom(
                        () -> {
                            throw new RuntimeException("hello!") ;
                        },
                        () -> {
                            j.getAndIncrement();
                            return null;
                        })
                .except(e -> {
                    q.getAndIncrement();
                    return null;
                })
                .ifPresent(fi -> j.getAndIncrement());

        assertThat(ClosableResult.hasOpenResources()).isFalse();
        assertEquals(1, q.get());
        assertEquals(0, j.get());

        var a = new AtomicInteger();
        var b = new AtomicInteger();
        try {
            Result.<FileInputStream, SingleError>tryFrom(
                            () -> {
                                throw new Exception("hello!");
                            },
                            () -> {
                                a.getAndIncrement();
                                return null;
                            })
                    .exceptRuntime()
                    .ifPresent(fi -> j.getAndIncrement());
        } catch (RuntimeException e) {
            b.getAndIncrement();
            assertThat(e.getCause()).isInstanceOf(Exception.class);
            assertThat(e.getCause().getMessage()).isEqualTo("hello!");
        }

        assertThat(ClosableResult.hasOpenResources()).isFalse();
        assertEquals(1, b.get());
        assertEquals(0, a.get());


        var c = new AtomicInteger();
        var d = new AtomicInteger();
        var e = new AtomicInteger();
        Result.<FileInputStream, SingleError>tryFrom(
                        () -> {
                            throw new Exception("hello!");
                        },
                        () -> {
                            c.getAndIncrement();
                            return null;
                        })
                .exceptErr(exc -> SingleError.fromMessage("hello there..."))
                .peekError(se -> {
                    assertThat(se.getMessage()).isEqualTo("hello there...");
                    d.getAndIncrement();
                })
                .ifPresent(fi -> e.getAndIncrement());

        assertThat(ClosableResult.hasOpenResources()).isFalse();
        assertEquals(1, d.get());
        assertEquals(0, c.get());
        assertEquals(0, e.get());

        var f = new AtomicInteger();
        var g = new AtomicInteger();
        var h = new AtomicInteger();
        Result.<FileInputStream, SingleError>tryFrom(
                        () -> {
                            throw new Exception("hello!");
                        },
                        () -> {
                            f.getAndIncrement();
                            return null;
                        })
                .exceptErr(exc -> SingleError.fromMessage("hello there..."))
                .peekError(se -> {
                    assertThat(se.getMessage()).isEqualTo("hello there...");
                    g.getAndIncrement();
                })
                .onErrorFlatMapResult(
                        () -> Result.ok("hello!"),
                        fs -> Result.ok("whatever"))
                .ifPresent(fi -> {
                    h.getAndIncrement();
                    assertThat(fi).isEqualTo("hello!");
                });

        assertThat(ClosableResult.hasOpenResources()).isFalse();
        assertEquals(1, g.get());
        assertEquals(0, f.get());
        assertEquals(1, h.get());

    }

    @Test
    public void onErrorMap() {
        var res = Result.<String, SingleError>err(SingleError.fromMessage("hello!"))
                .onErrorMap(se -> true, () -> "whatever!");

        assertThat(res.isError()).isFalse();
        assertThat(res.one().r().get()).isEqualTo("whatever!");

        Result<Integer, SingleError> o = Result.<String, SingleError>err(SingleError.fromMessage("hello!"))
                .onErrorMapTo(se -> true, () -> 1, () -> 2);

        assertThat(o.one().r().get()).isEqualTo(1);
        assertThat(o.isError()).isFalse();

        Result<Object, SingleError> b = Result.<String, SingleError>err(SingleError.fromMessage("hello!"))
                .onErrorMapTo(se -> false, () -> 1);

        assertThat(b.isError()).isTrue();
        assertThat(b.e().get().getMessage()).isEqualTo("hello!");

        b = Result.<String, SingleError>ok("hello!")
                .onErrorMapTo(se -> false, () -> 1);

        assertThat(b.isError()).isFalse();
        assertThat(b.r().get()).isEqualTo("hello!");

        var c = Result.<String, SingleError>err(SingleError.fromMessage("hello!"))
                .onErrorFlatMapResult(() -> Result.ok(1), r -> r.map(s -> 2));

        assertThat(c.isError()).isFalse();
        assertThat(c.r().get()).isEqualTo(1);

        c = Result.<String, SingleError>err(SingleError.fromMessage("hello!"))
                .onErrorFlatMapResult(
                        e -> false,
                        () -> Result.ok(1),
                        r -> Result.ok(2));

        assertThat(c.isError()).isFalse();
        assertThat(c.r().get()).isEqualTo(2);

        c = Result.<String, SingleError>err(SingleError.fromMessage("hello!"))
                .onErrorFlatMapResult(
                        e -> false,
                        () -> Result.ok(1),
                        r -> Result.err(SingleError.fromMessage("hello!")));

        assertThat(c.isError()).isTrue();
        assertThat(c.e().get().getMessage()).isEqualTo("hello!");
    }

    @Test
    public void doStream() {
        var collected = Result.from(Stream.of(Result.ok("hello"), Result.ok("hello"), Result.err(SingleError.fromMessage("hello"))))
                .flatMapResult(Result::ok)
                .map(e -> e)
                .map(e -> e)
                .flatMapResult(Result::ok)
                .mapError(e -> e)
                .collectList();

        assertEquals(2, collected.r().get().size());
        assertEquals(1, collected.e().get().size());

        var collected1 = Result.from(Stream.of(Result.ok("hello")))
                .flatMapResult(Result::ok)
                .map(e -> e)
                .map(e -> e)
                .flatMapResult(Result::ok)
                .mapError(e -> e)
                .collectList();

        assertEquals(1, collected1.r().get().size());
        assertEquals(0, collected1.e().get().size());

        var collected2 = Result.from(Stream.of(Result.err(new SingleError.StandardError("i"))))
                .flatMapResult(Result::ok)
                .map(e -> e)
                .map(e -> e)
                .flatMapResult(Result::ok)
                .mapError(e -> e)
                .collectList();

        assertEquals(0, collected2.r().get().size());
        assertEquals(1, collected2.e().get().size());

        var collected3 = Result.from(IntStream.range(0, 100).boxed().map(b -> {
                    if (b % 2 == 0) {
                        return Result.ok("hello");
                    }

                    return Result.err(new SingleError.StandardError("i"));
                }))
                .flatMapResult(Result::ok)
                .map(e -> e)
                .map(e -> e)
                .flatMapResult(Result::ok)
                .mapError(e -> e)
                .collectList();

        assertEquals(50, collected3.r().get().size());
        assertEquals(50, collected3.e().get().size());

        var r = Result.from(IntStream.range(0, 100).boxed().map(b -> {
            if (b % 2 == 0) {
                return Result.ok("hello");
            }

            return Result.err(new SingleError.StandardError("i"));
        }));


        var streamed = r.streamResult();

        streamed.forEach(System.out::println);

        assertThat(streamed.isOk()).isTrue();
    }

    @Test
    public void stream() {
        Result.stream(Stream.of("one", "two", "three"))
                .peek(System.out::println)
                .ifPresent(System.out::println);

        AtomicInteger i = new AtomicInteger(0);
        var found = Result.stream(Stream.of("one", "two", "three", "four"))
                .peek(System.out::println)
                .map(e -> e)
                .mapError(e -> e)
                .flatMapResult(e -> {
                    if (i.getAndIncrement() % 2 == 0) {
                        return Result.ok(e);
                    }

                    return Result.err(SingleError.fromE(new RuntimeException()));
                })
                .collectList();

        assertThat(found.r().get().size()).isEqualTo(2);
        assertThat(found.e().get().size()).isEqualTo(2);
    }

    @Test
    void flatMap() {
        var singleMessageItem = singleMessage();
        Result<String, ResultTestModel.TestAgg> hello = singleMessageItem
                .flatMap(t -> Result.ok("hello"))
                .cast();
        assertThat(hello.r().get()).isEqualTo("hello");
        AggregateError.StandardAggregateError error = new AggregateError.StandardAggregateError("hello...");
        Result<ResultTestModel.TestRes, AggregateError.StandardAggregateError> hello3 = singleMessageItem
//                .flatMapError(e -> Result.err(error))
                .castError();
        assertThat(hello3.e().isEmpty()).isTrue();
//        hello3 = singleMessageItem
//                .<AggregateError.StandardAggregateError>flatMapError(t -> Err.empty())
//                .cast();
//        assertThat(hello3.e().isEmpty()).isTrue();

    }

    @Test
    public void testMapErr() {

        Result<String, ResultTestModel.TestErr1> from = Result.from("hello", new ResultTestModel.TestErr1("hello"));
        Result<Integer, ResultTestModel.TestErr> from1 = Result.from(1, new ResultTestModel.TestErr("hello"));

        var n = from.mapError(e -> new ResultTestModel.TestErr1("yes"));
        Assertions.assertTrue(n.e().get().getMessage().equals("yes"));


        Result<Integer, ResultTestModel.TestErr> from2 = Result.ok(1);
        Result<Integer, ResultTestModel.TestErr1> hello = from2.mapError(e -> new ResultTestModel.TestErr1("hello"), new ResultTestModel.TestErr1("hello"));
        assertEquals("hello", hello.e().get().getMessage());
        hello = from2.mapError(e -> new ResultTestModel.TestErr1("hello"));
        Assertions.assertTrue(hello.e().isEmpty());
    }

}
