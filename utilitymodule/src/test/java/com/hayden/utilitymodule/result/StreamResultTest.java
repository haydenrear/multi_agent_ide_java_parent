package com.hayden.utilitymodule.result;

import com.hayden.utilitymodule.result.res_support.many.stream.StreamResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class StreamResultTest {
    record TestErr() {}

    @Test
    public void resultStreamToResultStreamFlatMap() {
        var res = Result.from(Stream.of(Result.ok(Stream.of("one", "two"))))
                .flatMapResult(e -> Result.from(Stream.<String>of("first", "second").map(ei -> Result.<String, Object>ok(ei))))
                .toList();

        assertThat(res.size()).isEqualTo(4);
        assertThat(res).containsExactlyElementsOf(List.of("first", "second", "first", "second"));
    }

    @Test
    public void resultStreamToResultStream() {
        var found = Result.ok(Stream.of("first", "second", "third"))
                .flatMapResult(s -> Result.from(Stream.of(Result.ok("first"), Result.ok("second"), Result.ok("third"))))
                .toList();

        assertThat(found).containsExactlyElementsOf(List.of("first", "second", "third", "first", "second", "third", "first", "second", "third"));

        var foundAgain = Result.ok(Stream.of("first", "second", "third"))
                .flatMapResult(s -> Result.empty())
                .many()
                .hasAnyOr(() -> Result.ok("whatever"))
                .r().get();

        assertThat(foundAgain).isEqualTo("whatever");

        var foundThird = Result.stream(Stream.of("first", "second", "third"))
                .map(e -> e)
                .flatMapResult(n -> {
                    return Result.ok("whatever")
                            .flatMapResult(e -> Result.from(Stream.of(Result.ok("hello!"), Result.ok("world!"), Result.ok("world!"))));
                })
                .toList();

        assertThat(foundThird.size()).isEqualTo(9);

        var foundFourth = Result.stream(Stream.of("first", "second", "third"))
                .map(e -> e)
                .flatMapResult(n -> {
                    return Result.ok("whatever")
                            .flatMapResult(e -> Result.from(Stream.of(Result.ok("hello!"), Result.ok("world!"), Result.ok("world!"))));
                })
                .many()
                .hasAnyOr(() -> Result.ok("ouch"))
                .toList();

        assertThat(foundFourth.size()).isEqualTo(9);

        var foundFifth = Result.stream(Stream.of("first", "second", "third"))
                .map(e -> e)
                .flatMapResult(n -> {
                    return Result.ok("whatever")
                            .flatMapResult(e -> Result.ok("touche"))
                            .one();
                })
                .many()
                .hasAnyOr(() -> Result.ok("ouch"))
                .toList();

        assertThat(foundFifth.size()).isEqualTo(3);
        assertThat(foundFifth).hasSameElementsAs(List.of("touche", "touche", "touche"));

        var foundSixth = Result.stream(Stream.of("first", "second", "third"))
                .map(e -> e)
                .flatMapResult(n -> Result.empty())
                .many()
                .hasAnyOr(() -> Result.ok("ouch"))
                .toList();

        assertThat(foundSixth.size()).isEqualTo(1);
        assertThat(foundSixth).hasSameElementsAs(List.of("ouch"));
    }

    @Test
    public void doTestStreamResultErr() {
        var found = Result.from(Stream.of(Result.ok("whatever"), Result.ok("ok")))
                .flatMapResult(e -> Result.from(Stream.of(Result.ok("whatever"), Result.ok("ok"))))
                .flatMapResult(e -> Result.from(Stream.of(Result.ok("then"), Result.ok("hello"))));

        var f = found.toList();

        assertThat(f).containsExactlyElementsOf(List.of("then", "hello", "then", "hello", "then", "hello", "then", "hello"));
    }


    @Test
    public void doTestStreamResultFlatMap() {
        var found = Result.ok(Stream.of("first", "second", "third"))
                .many()
                .flatMapResult(e -> Result.from(Stream.of(Result.ok("first"))))
                .flatMapResult(e -> Result.from(Stream.of(Result.ok("first"))))
                .mapError(e -> e.toString())
                .toList();

        System.out.println();
    }

    @Test
    public void doTestStreamResultMapErr() {
        var found = Result.ok(Stream.of("first", "second", "third"))
                .many()
                .flatMapResult(e -> Result.from(Stream.of(Result.err(new TestErr()))))
                .mapError(e -> e.toString())
                .toList();

        System.out.println();
    }

    @Test
    public void doTestStreamResultCollect() {
        Result<String, TestErr> sr = new StreamResult<>(Stream.of(Result.ok("first"), Result.ok("second"), Result.ok("third"),
                Result.err(new TestErr())));

        var collected = sr.toList();
        var e = collected.errs();
        var r = collected.res();
        var errors = collected.errsList();
        var results = collected.results();
        var cached = collected.resultCache();

        assertThat(results).containsExactlyElementsOf(List.of("first", "second", "third"));
        assertThat(errors).hasSameElementsAs(List.of(new TestErr()));

        assertThat(cached.size()).isNotZero();

        assertThat(e.size()).isNotZero();
        assertThat(r.size()).isNotZero();
    }

    @Test
    public void doTestStreamResultCollectWithFlatMap() {
        record TestErr() {}
        Result<String, TestErr> sr = new StreamResult<>(Stream.of(Result.ok("first"), Result.ok("second"), Result.ok("third"), Result.err(new TestErr())))
                .flatMapResult(u -> Result.ok("ok"));

        var collected = sr.toList();
        var e = collected.errs();
        var r = collected.res();
        var errors = collected.errsList();
        var results = collected.results();
        var cached = collected.resultCache();

        assertThat(results).containsExactlyElementsOf(List.of("ok", "ok", "ok"));
        assertThat(errors).hasSameElementsAs(List.of(new TestErr()));

        assertThat(cached.size()).isNotZero();

        assertThat(e.size()).isNotZero();
        assertThat(r.size()).isNotZero();
    }

    @Test
    public void doTestResultCollect() {
        record TestErr() {}
        Result<String, TestErr> sr = Result.from("hello", new TestErr());

        var collected = sr.toList();
        var e = collected.errs();
        var r = collected.res();
        var errors = collected.errsList();
        var results = collected.results();
        var cached = collected.resultCache();

        assertThat(results).containsExactlyElementsOf(List.of("hello"));
        assertThat(errors).hasSameElementsAs(List.of(new TestErr()));

        assertThat(cached.size()).isNotZero();

        assertThat(e.size()).isNotZero();
        assertThat(r.size()).isNotZero();
    }

    @Test
    public void doTestResultCollectWithMap() {
        record TestErr() {}
        Result<String, TestErr> sr = Result.from("hello", new TestErr());

        sr = sr.flatMapResult(h -> Result.ok("whatever"));

        var collected = sr.toList();
        var e = collected.errs();
        var r = collected.res();
        var errors = collected.errsList();
        var results = collected.results();
        var cached = collected.resultCache();

        assertThat(results).containsExactlyElementsOf(List.of("whatever"));
        assertThat(errors).hasSameElementsAs(List.of(new TestErr()));

        assertThat(cached.size()).isNotZero();

        assertThat(e.size()).isNotZero();
        assertThat(r.size()).isNotZero();
    }

}
