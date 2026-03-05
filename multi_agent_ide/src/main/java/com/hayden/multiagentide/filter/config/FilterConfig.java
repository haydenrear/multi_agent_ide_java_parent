package com.hayden.multiagentide.filter.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration that wires the FilterModelModule into the application context.
 * The FilterModelModule bean is auto-discovered via component scanning since it's
 * annotated with @Configuration and returns a @Bean Module.
 */
@Configuration
@Import(FilterModelModule.class)
public class FilterConfig {
}
