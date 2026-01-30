package com.netflix.graphql.dgs;

import graphql.schema.Coercing;
import graphql.schema.GraphQLScalarType;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.util.Map;

@Configuration
public class DgsScalarWiringConfigurer {

    @Bean
    RuntimeWiringConfigurer dgsScalarConfigurer(ApplicationContext applicationContext) {
        return wiringBuilder -> {
            Map<String, Object> scalars = applicationContext.getBeansWithAnnotation(DgsScalar.class);
            for (Object scalarBean : scalars.values()) {
                DgsScalar annotation = scalarBean.getClass().getAnnotation(DgsScalar.class);
                if (annotation == null || !(scalarBean instanceof Coercing)) {
                    continue;
                }
                GraphQLScalarType scalarType = GraphQLScalarType.newScalar()
                        .name(annotation.name())
                        .coercing((Coercing<?, ?>) scalarBean)
                        .build();
                wiringBuilder.scalar(scalarType);
            }
        };
    }
}
