package com.hayden.utilitymodule.result.res_many;

import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.res_support.many.stream.StreamResultOptions;
import com.hayden.utilitymodule.result.res_support.many.stream.StreamWrapper;
import com.hayden.utilitymodule.result.res_support.many.stream.stream_cache.CachingOperations;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class StreamResultItemTest {

    @Test
    public void testInifiniCache() {
        AtomicBoolean b = new AtomicBoolean(true);
        AtomicInteger i = new AtomicInteger(0);
        var gen = Stream.iterate(
                0,
                s -> i.get() <= 100,
                s -> {
                    if (i.getAndIncrement() > 100)
                        b.set(false);
                    if (b.get()) {
                        return i.get();
                    }

                    return i.get();
                });

        var streamResultItem = new StreamResultItem<>(gen, StreamResultOptions.builder().isInfinite(true).maxSize(10).build());

        var list = streamResultItem.toList();
        var cachedFound = streamResultItem.r.getCached();
        var res = cachedFound.CACHED_RESULTS();

        assertThat(res.size()).isNotZero();
        assertThat(res.get(CachingOperations.RetrieveFirstTy.class).cachedResult()).isEqualTo(0);
        assertThat(list.size()).isEqualTo(11);
        assertThat(list).hasSameElementsAs(IntStream.range(90, 101).boxed().toList());
    }

    @Test
    public void testStreamResultItem() {
        var o = Result.ok("ok")
                .many()
                .flatMapResult(s -> {
                    return Result.from(Stream.of(Result.ok("whatever"), Result.ok("wy")));
                })
                .hasAnyOr(() -> Result.ok("w"));

        var s = Result.ok("ok")
                .many()
                .flatMapResult(u -> {
                    return Result.empty();
                })
                .hasAnyOr(() -> Result.ok("w"));
    }

    @Test
    public void testMapToStreamResult() {
        var found = Result.ok("whatever")
                .flatMapToStreamResult(w -> Result.from(Stream.of(Result.ok("whatever"), Result.ok("ok"))))
                .many();

        var many = found.many().toResultLists().get();
        assertThat(many).isNotNull();
        assertThat(many).containsExactly("whatever", "ok");

        found = Result.ok("whatever")
                .flatMapResult(w -> Result.from(Stream.of(Result.ok("whatever"), Result.ok("ok"))));

        many = found.many().toResultLists().get();
        assertThat(many).isNotNull();
        assertThat(many).containsExactly("whatever", "ok");
    }

    @Test
    public void testOkStreamResultItem() {
        var s = Stream.of("one", "two");
        StreamResultItem<String> stringStreamResultItem = new StreamResultItem<>(s);
        var sWrapper = new StreamResultItem.ResultTyStreamWrapper<>(StreamResultOptions.builder().build(),
                s, stringStreamResultItem);

        List<String> assertList = new ArrayList<>();
        sWrapper.cacheResultsIfNotCachedWithList(assertList::add);

        assertThat(assertList).containsExactly("one", "two");

        var firstFound = sWrapper.first();

        assertNotNull(firstFound);
        assertEquals("one", firstFound);
        assertTrue(sWrapper.isAnyNonNull());
        assertFalse(sWrapper.isCompletelyEmpty());
    }

    @Test
    public void testOkStreamResultItemEmpty() {
        var s = Stream.<String>empty();
        StreamResultItem<String> stringStreamResultItem = new StreamResultItem<>(s);
        var sWrapper = new StreamResultItem.ResultTyStreamWrapper<>(StreamResultOptions.builder().build(),
                s, stringStreamResultItem);

        List<String> assertList = new ArrayList<>();
        sWrapper.cacheResultsIfNotCachedWithList(assertList::add);

        assertThat(assertList).isEmpty();

        var firstFound = sWrapper.first();

        assertNull(firstFound);
        assertFalse(sWrapper.isAnyNonNull());
        assertTrue(sWrapper.isCompletelyEmpty());
    }

}