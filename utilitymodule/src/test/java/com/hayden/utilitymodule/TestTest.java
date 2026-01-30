package com.hayden.utilitymodule;

import lombok.AllArgsConstructor;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.stream.IntStream;

public class TestTest {

    @Test
    public void testSubscriber() {
        var o = Flux.create(f -> {
            IntStream.range(0, 100).forEach(f::next);
            f.complete();
        });

        StepVerifier.create(o.buffer(Duration.ofSeconds(10)))
                .thenConsumeWhile(s -> true)
                .verifyComplete()
        ;
    }

    interface Five {}

    class One implements Five {

    }

    @AllArgsConstructor
    class Two<T extends Five> {
        T t;
    }

    @Test
    public void testCasting() {
        Two<? extends Five>  five = new Two<>(new One());
        Two<One> o = (Two<One>) five;
        System.out.println(o);
    }

}
