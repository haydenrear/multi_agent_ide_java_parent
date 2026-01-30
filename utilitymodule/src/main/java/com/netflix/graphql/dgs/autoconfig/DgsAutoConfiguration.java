package com.netflix.graphql.dgs.autoconfig;

import com.netflix.graphql.dgs.DgsQueryExecutorConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(DgsQueryExecutorConfig.class)
public class DgsAutoConfiguration {
}
