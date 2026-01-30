package com.netflix.graphql.dgs;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "graphql-client")
@Component
@Data
public class GraphQlClientProps {

    private String url = "http://localhost:8080/graphql";

}
