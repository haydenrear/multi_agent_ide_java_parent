package com.hayden.utilitymodule.result.map;

import com.google.common.collect.Sets;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.ResultTestModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class ParameterizedResultCollectorsTest {


    @Test
    public void paramTest() {
        var found = IntStream.range(0, 100)
                .boxed()
                .map(Object::toString)
                .map(Result::<String, ResultTestModel.TestOneErr>ok)
                .flatMap(toe -> Stream.<Result<String, ResultTestModel.TestOneErr>>of(toe, Result.err(new ResultTestModel.TestOneErr("fail"))))
                .collect(ResultCollectors.from(new ResultTestModel.TestOneAggResp(new ArrayList<>()), new ResultTestModel.TestOneAggErr(new HashSet<>())));

        assertEquals(100, found.r().get().all().size());
        assertEquals(1, found.e().get().allItems().size());

        var foundCollected = found.collectList();
        assertEquals(1, foundCollected.r().get().size());
        assertEquals(1, foundCollected.e().get().size());

        var foundNone = Stream.<Result<String, ResultTestModel.TestOneErr>>empty()
                .collect(ResultCollectors.from(new ResultTestModel.TestOneAggResp(new ArrayList<>()), new ResultTestModel.TestOneAggErr(new HashSet<>())));
        assertEquals(0, foundNone.r().get().all().size());
        assertEquals(0, foundNone.e().get().allItems().size());

        var foundNoneResCollected = foundNone.collectList();
        assertEquals(1, foundNoneResCollected.r().get().size());
        assertEquals(1, foundNoneResCollected.e().get().size());

        var foundOneRes = Stream.of(Result.<String, ResultTestModel.TestOneErr>ok("hello"))
                .collect(ResultCollectors.from(new ResultTestModel.TestOneAggResp(new ArrayList<>()), new ResultTestModel.TestOneAggErr(new HashSet<>())));
        assertEquals(1, foundOneRes.r().get().all().size());
        assertEquals(0, foundOneRes.e().get().allItems().size());

        var foundOneResCollected = foundOneRes.collectList();
        assertEquals(1, foundOneResCollected.r().get().size());
        assertEquals(1, foundOneResCollected.e().get().size());

        var foundOneErr = Stream.of(Result.<String, ResultTestModel.TestOneErr>err(new ResultTestModel.TestOneErr("fail")))
                .collect(ResultCollectors.from(new ResultTestModel.TestOneAggResp(new ArrayList<>()), new ResultTestModel.TestOneAggErr(new HashSet<>())));
        assertEquals(0, foundOneErr.r().get().all().size());
        assertEquals(1, foundOneErr.e().get().allItems().size());

        var foundOneCollected = foundOneErr.collectList();
        assertEquals(1, foundOneCollected.r().get().size());
        assertEquals(1, foundOneCollected.e().get().size());
    }

    @Test
    public void paramMapTest() {
        var found = IntStream.range(0, 100)
                .boxed()
                .map(Object::toString)
                .map(Result::<String, ResultTestModel.TestOneErr>ok)
                .collect(ResultCollectors.from(
                        new ResultTestModel.TestOneAggResp(new ArrayList<>()),
                        new ResultTestModel.TestOneAggErr(new HashSet<>()),
                        res -> Optional.of(""),
                        err -> Optional.of(new ResultTestModel.TestOneErr("fail"))
                ));

        assertEquals(100, found.r().get().all().size());
        assertEquals(1, found.e().get().allItems().size());

        var foundCollected = found.collectList();
        assertEquals(1, foundCollected.r().get().size());
        assertEquals(1, foundCollected.e().get().size());

        var foundNone = Stream.<Result<String, ResultTestModel.TestOneErr>>empty()
                .collect(ResultCollectors.from(
                        new ResultTestModel.TestOneAggResp(new ArrayList<>()),
                        new ResultTestModel.TestOneAggErr(new HashSet<>()),
                        res -> Optional.empty(),
                        err -> Optional.empty()
                ));
        assertEquals(0, foundNone.r().get().all().size());
        assertEquals(0, foundNone.e().get().allItems().size());

        var foundNoneResCollected = foundNone.collectList();
        assertEquals(1, foundNoneResCollected.r().get().size());
        assertEquals(1, foundNoneResCollected.e().get().size());

        var foundOneRes = Stream.of(Result.<String, ResultTestModel.TestOneErr>ok("hello"))
                .collect(ResultCollectors.from(
                        new ResultTestModel.TestOneAggResp(new ArrayList<>()),
                        new ResultTestModel.TestOneAggErr(new HashSet<>()),
                        res -> Optional.of(""),
                        err -> Optional.empty()
                ));
        assertEquals(1, foundOneRes.r().get().all().size());
        assertEquals(0, foundOneRes.e().get().allItems().size());

        var foundOneResCollected = foundOneRes.collectList();
        assertEquals(1, foundOneResCollected.r().get().size());
        assertEquals(1, foundOneResCollected.e().get().size());

        var foundOneErr = Stream.of(Result.<String, ResultTestModel.TestOneErr>err(new ResultTestModel.TestOneErr("fail")))
                .collect(ResultCollectors.from(
                        new ResultTestModel.TestOneAggResp(new ArrayList<>()),
                        new ResultTestModel.TestOneAggErr(new HashSet<>()),
                        res -> Optional.empty(),
                        err -> Optional.of(new ResultTestModel.TestOneErr("fail"))
                ));
        assertEquals(0, foundOneErr.r().get().all().size());
        assertEquals(1, foundOneErr.e().get().allItems().size());

        var foundOneCollected = foundOneErr.collectList();
        assertEquals(1, foundOneCollected.r().get().size());
        assertEquals(1, foundOneCollected.e().get().size());
    }

    @Test
    public void paramAggMapTest() {
        var found = IntStream.range(0, 100)
                .boxed()
                .map(Object::toString)
                .map(Result::<String, ResultTestModel.TestOneErr>ok)
                .collect(ResultCollectors.aggParamFrom(
                        new ResultTestModel.TestOneAggResp(new ArrayList<>()),
                        new ResultTestModel.TestOneAggErr(new HashSet<>()),
                        err -> Optional.of(new ResultTestModel.TestOneAggResp(List.of(""))),
                        res -> Optional.of(new ResultTestModel.TestOneAggErr(Sets.newHashSet(new ResultTestModel.TestOneErr("hello"))))
                ));

        assertEquals(100, found.r().get().all().size());
        assertEquals(1, found.e().get().allItems().size());

        var foundCollected = found.collectList();
        assertEquals(1, foundCollected.r().get().size());
        assertEquals(1, foundCollected.e().get().size());

        var foundNone = Stream.<Result<String, ResultTestModel.TestOneErr>>empty()
                .collect(ResultCollectors.aggParamFrom(
                        new ResultTestModel.TestOneAggResp(new ArrayList<>()),
                        new ResultTestModel.TestOneAggErr(new HashSet<>()),
                        err -> Optional.of(new ResultTestModel.TestOneAggResp(List.of(""))),
                        res -> Optional.of(new ResultTestModel.TestOneAggErr(Sets.newHashSet(new ResultTestModel.TestOneErr("hello"))))
                ));
        assertEquals(0, foundNone.r().get().all().size());
        assertEquals(0, foundNone.e().get().allItems().size());

        var foundNoneResCollected = foundNone.collectList();
        assertEquals(1, foundNoneResCollected.r().get().size());
        assertEquals(1, foundNoneResCollected.e().get().size());

        var foundOneRes = Stream.of(Result.<String, ResultTestModel.TestOneErr>ok("hello"))
                .collect(ResultCollectors.aggParamFrom(
                        new ResultTestModel.TestOneAggResp(new ArrayList<>()),
                        new ResultTestModel.TestOneAggErr(new HashSet<>()),
                        err -> Optional.of(new ResultTestModel.TestOneAggResp(List.of(""))),
                        res -> Optional.empty())
                );
        assertEquals(1, foundOneRes.r().get().all().size());
        assertEquals(0, foundOneRes.e().get().allItems().size());

        var foundOneResCollected = foundOneRes.collectList();
        assertEquals(1, foundOneResCollected.r().get().size());
        assertEquals(1, foundOneResCollected.e().get().size());

        var foundOneErr = Stream.of(Result.<String, ResultTestModel.TestOneErr>err(new ResultTestModel.TestOneErr("fail")))
                .collect(ResultCollectors.aggParamFrom(
                        new ResultTestModel.TestOneAggResp(new ArrayList<>()),
                        new ResultTestModel.TestOneAggErr(new HashSet<>()),
                        err -> Optional.empty(),
                        res -> Optional.of(new ResultTestModel.TestOneAggErr(Sets.newHashSet(new ResultTestModel.TestOneErr("hello"))))
                ));
        assertEquals(0, foundOneErr.r().get().all().size());
        assertEquals(1, foundOneErr.e().get().allItems().size());

        var foundOneCollected = foundOneErr.collectList();
        assertEquals(1, foundOneCollected.r().get().size());
        assertEquals(1, foundOneCollected.e().get().size());
    }

}