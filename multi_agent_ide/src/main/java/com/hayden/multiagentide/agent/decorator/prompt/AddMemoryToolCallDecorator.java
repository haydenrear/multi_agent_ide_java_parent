package com.hayden.multiagentide.agent.decorator.prompt;

import com.hayden.multiagentide.tool.EmbabelToolObjectRegistry;
import com.hayden.multiagentide.tool.ToolAbstraction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

@Slf4j
@Component
@RequiredArgsConstructor
public class AddMemoryToolCallDecorator implements LlmCallDecorator {

    private final EmbabelToolObjectRegistry toolObjectRegistry;

    @Override
    public int order() {
        return -10_000;
    }

    @Override
    public LlmCallContext decorate(LlmCallContext promptContext) {
        var t = withHindsight(promptContext);

        return promptContext.toBuilder()
                .tcc(
                        promptContext.tcc().toBuilder()
                                .tools(t)
                                .build())
                .build();
    }

    private @NonNull List<ToolAbstraction> withHindsight(LlmCallContext promptContext) {
        var t = new ArrayList<>(promptContext.tcc().tools());

        toolObjectRegistry.tool("hindsight")
                .flatMap(to -> {
                    var obj = to.stream().filter(Objects::nonNull)
                            .map(ToolAbstraction.EmbabelToolObject::new).toList();

                    return Optional.of(obj)
                            .filter(Predicate.not(CollectionUtils::isEmpty));
                })
                .ifPresentOrElse(
                        t::addAll,
                        () -> log.error("Could not find hindsight tool."));

        return t;
    }
}
