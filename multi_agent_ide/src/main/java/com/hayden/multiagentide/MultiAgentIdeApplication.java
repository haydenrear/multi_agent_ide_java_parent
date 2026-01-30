package com.hayden.multiagentide;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Spring Boot application for Multi-Agent IDE.
 * A multi-agent orchestration platform with recursive git worktrees, spec files,
 * and Embabel agents for goal-driven development.
 */
@SpringBootApplication
@EnableAsync
public class MultiAgentIdeApplication {

    static void main(String[] args) {
        SpringApplication.run(MultiAgentIdeApplication.class, args);
    }
}
