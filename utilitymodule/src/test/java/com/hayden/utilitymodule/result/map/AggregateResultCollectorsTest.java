package com.hayden.utilitymodule.result.map;

import com.google.common.collect.Sets;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.ResultTestModel;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class AggregateResultCollectorsTest {


    @Test
    public void aggregateMappingCollector() {


        Set<Set<Result<String, ResultTestModel.TestErr>>> combinations
                = Sets.combinations(Set.of(Result.err(new ResultTestModel.TestErr("hello")),
                Result.err(new ResultTestModel.TestErr("goodbye")), Result.ok("well"), Result.ok("how")), 4);

        for (var c : combinations) {
            var collected = c.stream().collect(
                    ResultCollectors.from(
                            new ResultTestModel.TestRes(new HashSet<>()),
                            new ResultTestModel.TestAgg(new HashSet<>()),
                            ab -> ab.one().toOptional().map(o -> new ResultTestModel.TestRes(Set.of(o))),
                            b -> {
                                if (b.isError()) {
                                    return Optional.of(new ResultTestModel.TestAgg(Sets.newHashSet(b.e().get())));
                                }
                                return Optional.empty();
                            }
                    ));
            assertAll(
                    () -> assertThat(collected.r().get().values()).hasSameElementsAs(List.of("well", "how")),
                    () -> assertThat(collected.e().get().getMessages()).hasSameElementsAs(List.of("hello", "goodbye"))
            );


        }
    }

    @Test
    void all() {
        var singleMessage = ResultTestModel.singleMessage();
        var errorAndMessage = ResultTestModel.errorAndMessage();
        var singleError = ResultTestModel.withSingleError();
        var errorAndMessage2 = ResultTestModel.errorAndMessage2();
        var multipleErrors = ResultTestModel.multipleErrors();
        var multipleMessage = ResultTestModel.multipleMessages();
        var multipleMessageMultipleError = ResultTestModel.multipleErrorAndMessages();


        Set<Set<Result<ResultTestModel.TestRes, ResultTestModel.TestAgg>>> combinations
                = Sets.combinations(Set.of(singleMessage, errorAndMessage, singleError, errorAndMessage2), 4);

        for (var c : combinations) {
            var all = Result.all(c);
            var collected = c.stream().collect(ResultCollectors.from(new ResultTestModel.TestRes(new HashSet<>()), new ResultTestModel.TestAgg(new HashSet<>())));
            assertThat(all.r().get().values()).hasSameElementsAs(List.of("hello", "hello1", "hello2"));
            assertThat(all.e().get().getMessages()).hasSameElementsAs(List.of("goodbye1", "goodbye2", "goodbye3"));
            assertThat(collected.r().get().values()).hasSameElementsAs(List.of("hello", "hello1", "hello2"));
            assertThat(collected.e().get().getMessages()).hasSameElementsAs(List.of("goodbye1", "goodbye2", "goodbye3"));
        }
        singleMessage = ResultTestModel.singleMessage();

        Result<ResultTestModel.TestRes, ResultTestModel.TestAgg> all3 = Result.all(List.of(singleMessage));
        var collected3 = Stream.of(singleMessage).collect(AggregateResultCollectors.AggregateResultCollector.fromValues(new ResultTestModel.TestRes(new HashSet<>()), new ResultTestModel.TestAgg(new HashSet<>())));

        assertAll(
                () -> assertThat(all3.r().get().values()).hasSameElementsAs(List.of("hello")),
                () -> assertThat(all3.e().isEmpty()).isTrue(),
                () -> assertThat(collected3.r().get().values()).hasSameElementsAs(List.of("hello")),
                () -> assertThat(collected3.e().isPresent()).isTrue(),
                () -> assertThat(collected3.e().get().errors().size()).isZero()
        );

        var all2 = Result.all(List.<Result<ResultTestModel.TestRes, ResultTestModel.TestAgg>>of());
        var collected2 = Stream.<Result<ResultTestModel.TestRes, ResultTestModel.TestAgg>>of().collect(AggregateResultCollectors.AggregateResultCollector.fromValues(new ResultTestModel.TestRes(new HashSet<>()), new ResultTestModel.TestAgg(new HashSet<>())));

        assertAll(
                () -> assertThat(all2).isNull(),
                () -> assertThat(collected2.e().isPresent()).isTrue(),
                () -> assertThat(collected2.e().get().errors().size()).isZero()
        );

        errorAndMessage2 = ResultTestModel.errorAndMessage2();

        Result<ResultTestModel.TestRes, ResultTestModel.TestAgg> all4 = Result.all(List.of(errorAndMessage2));
        Result<ResultTestModel.TestRes, ResultTestModel.TestAgg> collected4 = Stream.of(errorAndMessage2).collect(AggregateResultCollectors.AggregateResultCollector.fromValues(new ResultTestModel.TestRes(new HashSet<>()), new ResultTestModel.TestAgg(new HashSet<>())));

        assertAll(
                () -> assertThat(all4.r().get().values()).hasSameElementsAs(List.of("hello2")),
                () -> assertThat(all4.e().get().getMessages()).hasSameElementsAs(List.of("goodbye3")),
                () -> assertThat(collected4.r().get().values()).hasSameElementsAs(List.of("hello2")),
                () -> assertThat(collected4.e().get().getMessages()).hasSameElementsAs(List.of("goodbye3"))
        );

        singleError = ResultTestModel.withSingleError();

        Result<ResultTestModel.TestRes, ResultTestModel.TestAgg> all5 = Result.all(List.of(singleError));
        Result<ResultTestModel.TestRes, ResultTestModel.TestAgg> collected5 = Stream.of(singleError).collect(AggregateResultCollectors.AggregateResultCollector.fromValues(new ResultTestModel.TestRes(new HashSet<>()), new ResultTestModel.TestAgg(new HashSet<>())));

        assertAll(
                () -> assertThat(all5.r().isEmpty()).isTrue(),
                () -> assertThat(all5.e().get().getMessages()).hasSameElementsAs(List.of("goodbye2")),
                () -> assertThat(collected5.r().isEmpty()).isFalse(),
                () -> assertThat(collected5.r().get().values().isEmpty()).isTrue(),
                () -> assertThat(collected5.e().get().getMessages()).hasSameElementsAs(List.of("goodbye2"))
        );


        assertAll(
                () -> assertThat(multipleErrors.r().isEmpty()).isTrue(),
                () -> assertThat(multipleErrors.e().get().getMessages()).hasSameElementsAs(List.of("goodbye9", "goodbye10"))
        );


        assertAll(
                () -> assertThat(multipleMessage.r().get().values()).hasSameElementsAs(List.of("hello9", "hello10", "hello11")),
                () -> assertThat(multipleMessage.e().isPresent()).isFalse()
        );


        assertAll(
                () -> assertThat(multipleMessageMultipleError.r().get().values()).hasSameElementsAs(List.of("hello12", "hello13", "hello14")),
                () -> assertThat(multipleMessageMultipleError.e().get().getMessages()).hasSameElementsAs(List.of("goodbye11", "goodbye12"))
        );
    }


}