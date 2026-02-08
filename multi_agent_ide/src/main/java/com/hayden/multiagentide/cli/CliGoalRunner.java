package com.hayden.multiagentide.cli;

import com.hayden.multiagentide.controller.OrchestrationController;
import com.hayden.utilitymodule.config.EnvConfigProps;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class CliGoalRunner implements ApplicationRunner {

    private final OrchestrationController orchestrationController;
    private final EnvConfigProps envConfigProps;
    private final CliOutputWriter outputWriter;
    private final CliInteractionLoop interactionLoop;
    private final String repositoryUrl;
    private final String baseBranch;
    private final String title;

    public CliGoalRunner(
            OrchestrationController orchestrationController,
            EnvConfigProps envConfigProps,
            CliOutputWriter outputWriter,
            CliInteractionLoop interactionLoop,
            String repositoryUrl,
            String baseBranch,
            String title
    ) {
        this.orchestrationController = orchestrationController;
        this.envConfigProps = envConfigProps;
        this.outputWriter = outputWriter;
        this.interactionLoop = interactionLoop;
        this.repositoryUrl = repositoryUrl;
        this.baseBranch = baseBranch;
        this.title = title;
    }

    @Override
    public void run(ApplicationArguments args) {
        outputWriter.println("CLI mode active. Enter a goal to begin.");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            outputWriter.prompt("Goal> ");
            String goal = readLine(reader);
            if (goal == null) {
                outputWriter.println("No input provided. Exiting CLI mode.");
                return;
            }
            String trimmed = goal.trim();
            if (trimmed.isBlank()) {
                outputWriter.println("Goal cannot be empty. Please try again.");
                continue;
            }
            String repo = promptRepositoryUrl(reader);
            try {
                OrchestrationController.StartGoalResponse response =
                        orchestrationController.startGoal(new OrchestrationController.StartGoalRequest(
                                trimmed,
                                repo,
                                baseBranch,
                                resolveTitle(trimmed)
                        ));
                outputWriter.println("Goal started. Orchestrator nodeId=" + response.nodeId());
                interactionLoop.start(reader);
                return;
            } catch (Exception e) {
                outputWriter.println("Failed to start goal: " + e.getMessage());
            }
        }
    }

    private String promptRepositoryUrl(BufferedReader reader) {
        String defaultRepo = resolveDefaultRepositoryUrl();
        String prompt = defaultRepo != null && !defaultRepo.isBlank()
                ? "Repository URL [" + defaultRepo + "]> "
                : "Repository URL> ";
        outputWriter.prompt(prompt);
        String input = readLine(reader);
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isBlank()) {
            return defaultRepo;
        }
        return trimmed;
    }

    private String resolveDefaultRepositoryUrl() {
        if (repositoryUrl != null && !repositoryUrl.isBlank()) {
            return repositoryUrl;
        }
        if (envConfigProps != null && envConfigProps.getProjectDir() != null) {
            return envConfigProps.getProjectDir().toString();
        }
        return System.getProperty("user.dir");
    }

    private String resolveTitle(String goal) {
        if (title != null && !title.isBlank()) {
            return title;
        }
        if (goal.length() <= 60) {
            return goal;
        }
        return goal.substring(0, 60) + "...";
    }

    private String readLine(BufferedReader reader) {
        try {
            return reader.readLine();
        } catch (Exception e) {
            outputWriter.println("Error reading input: " + e.getMessage());
            return null;
        }
    }
}
