package com.hayden.utilitymodule.result;

import com.google.common.collect.Sets;
import com.hayden.utilitymodule.result.agg.Agg;
import com.hayden.utilitymodule.result.agg.AggregateError;
import com.hayden.utilitymodule.result.error.SingleError;
import com.hayden.utilitymodule.result.agg.Responses;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class ResultTestModel {
    public static @NotNull Result<TestRes, TestAgg> errorAndMessage2() {
        Result<TestRes, TestAgg> r3 = Result.from(
                new TestRes(Sets.newHashSet("hello2")),
                new TestAgg(Sets.newHashSet(SingleError.fromMessage("goodbye3")))
        );
        return r3;
    }

    public static @NotNull Result<TestRes, TestAgg> errorAndMessage() {
        Result<TestRes, TestAgg> r1 = Result.from(
                new TestRes(Sets.newHashSet("hello1")),
                new TestAgg(Sets.newHashSet(SingleError.fromMessage("goodbye1")))
        );
        return r1;
    }

    public static @NotNull Result<TestRes, TestAgg> singleMessage() {
        Result<TestRes, TestAgg> r = Result.ok(new TestRes(Sets.newHashSet("hello")));
        return r;
    }

    public static @NotNull Result<TestRes, TestAgg> withSingleError() {
        Result<TestRes, TestAgg> r2 = Result.err(
                new TestAgg(Sets.newHashSet(SingleError.fromMessage("goodbye2")))
        );
        return r2;
    }

    public static @NotNull Result<TestRes, TestAgg> multipleErrors() {
        Result<TestRes, TestAgg> r2;
        r2 = Result.err(
                new TestAgg(Sets.newHashSet(SingleError.fromMessage("goodbye9"), SingleError.fromMessage("goodbye10")))
        );
        return r2;
    }

    public static @NotNull Result<TestRes, TestAgg> multipleMessages() {
        Result<TestRes, TestAgg> r2 = Result.ok(new TestRes(Sets.newHashSet("hello9", "hello10", "hello11")));
        return r2;
    }

    public static @NotNull Result<TestRes, TestAgg> multipleErrorAndMessages() {
        return Result.from(
                new TestRes(Sets.newHashSet("hello12", "hello13", "hello14")),
                new TestAgg(Sets.newHashSet(SingleError.fromMessage("goodbye11"), SingleError.fromMessage("goodbye12")))
        );
    }

    public record TestAgg(Set<SingleError> errors) implements AggregateError.StdAggregateError {
    }

    public record TestRes(Set<String> values) implements Responses.AggregateResponse {

        @Override
        public void addAgg(Agg aggregateResponse) {
            this.values.addAll(((TestRes)aggregateResponse).values());
        }
    }

    public record TestOneAggResp(List<String> all) implements Responses.ParamAggregateResponse<String> {
        @Override
        public void addItem(String s) {
            this.all.add(s);
        }

        @Override
        public void addAgg(Agg t) {
            if (t instanceof TestOneAggResp(List<String> a)) {
                this.all.addAll(a);
            }
        }
    }

    public record TestOneErr(String getMessage) implements SingleError {
    }

    public record TestOneAggErr(Set<TestOneErr> allItems) implements AggregateError<TestOneErr> {
        @Override
        public void addAgg(Agg t) {
            if (t instanceof TestOneAggErr(Set<TestOneErr> a)) {
                this.allItems.addAll(a);
            }
        }

        @Override
        public Set<TestOneErr> errors() {
            return allItems;
        }
    }

    public record TestErr(String getMessage) implements SingleError {
    }

    record TestErr1(String getMessage) implements SingleError {
    }

}
