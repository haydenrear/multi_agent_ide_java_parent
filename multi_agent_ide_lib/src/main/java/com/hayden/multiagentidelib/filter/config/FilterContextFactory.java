package com.hayden.multiagentidelib.filter.config;

import com.hayden.multiagentidelib.filter.model.layer.FilterContext;
import com.hayden.multiagentidelib.filter.model.layer.DefaultPathFilterContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class FilterContextFactory {

    private final FilterConfigProperties filterConfigProperties;
    private final ContextObjectMapperProvider contextObjectMapperProvider;

    public <T extends FilterContext> T get(Supplier<T> supplier) {
        T context = supplier.get();
        return hydrate(context);
    }

    private <T extends FilterContext> T hydrate(T context) {
        if (context == null) {
            return null;
        }
        context.setFilterConfigProperties(filterConfigProperties);
        if (contextObjectMapperProvider != null) {
            context.setObjectMapper(contextObjectMapperProvider.objectMapper());
        }
        if (context instanceof FilterContext.PathFilterContext pathFilterContext
                && pathFilterContext.filterContext() != null) {
            hydrate(pathFilterContext.filterContext());
        }
        return context;
    }
}
