package com.netflix.graphql.dgs.internal;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import graphql.schema.GraphQLSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.client.DgsGraphQlClient;
import org.springframework.graphql.execution.GraphQlSource;

import java.util.Optional;

@RequiredArgsConstructor
public class DefaultDgsQueryExecutor implements DgsQueryExecutor {
    private final GraphQlSource graphQlSource;
    private final DgsGraphQlClient graphQlClient;

    @Override
    public <T> T executeAndExtractJsonPathAsObject(String query, String field, Class<T> targetClass) {
        if (field.startsWith("$.")) {
            field = field.substring(2);
        }
        return graphQlClient.getGraphQlClient()
                .document(query)
                .executeSync()
                .field(field)
                .toEntity(targetClass);
    }

    @Override
    public Optional<GraphQLSchema> getSchema() {
        return Optional.ofNullable(graphQlSource).map(GraphQlSource::schema);
    }
}
