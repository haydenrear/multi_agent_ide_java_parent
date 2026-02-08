package com.hayden.multiagentide.cli;

import com.hayden.multiagentide.controller.OrchestrationController;
import com.hayden.multiagentide.tui.TuiSession;
import com.hayden.utilitymodule.config.EnvConfigProps;
import org.springframework.context.annotation.Profile;
import org.springframework.shell.context.InteractionMode;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@ShellComponent
@Profile("cli")
public class CliTuiRunner {

    private final OrchestrationController orchestrationController;
    private final EnvConfigProps envConfigProps;
    private final CliOutputWriter outputWriter;
    private final TuiSession tuiSession;

    public CliTuiRunner(
            OrchestrationController orchestrationController,
            EnvConfigProps envConfigProps,
            CliOutputWriter outputWriter,
            TuiSession tuiSession
    ) {
        this.orchestrationController = orchestrationController;
        this.envConfigProps = envConfigProps;
        this.outputWriter = outputWriter;
        this.tuiSession = tuiSession;
    }

    @ShellMethod(key = "tui", value = "Launch the interactive TUI session")
    public String tui() {
        outputWriter.println("CLI mode active. Enter a goal in the chat box to begin.");
        String defaultRepo = resolveDefaultRepositoryUrl();
        tuiSession.configureSession("session-" + UUID.randomUUID(), goal -> startGoal(defaultRepo, goal));
        tuiSession.run();
        return "TUI session closed.";
    }

    private String startGoal(String repo, String goal) {
        try {
            OrchestrationController.StartGoalResponse response =
                    orchestrationController.startGoalAsync(new OrchestrationController.StartGoalRequest(
                            goal,
                            repo,
                            "main",
                            resolveTitle(goal)
                    ));
            return response.nodeId();
        } catch (Exception e) {
            outputWriter.println("Failed to start goal: " + e.getMessage());
            return null;
        }
    }

    private String resolveDefaultRepositoryUrl() {
        if (envConfigProps != null && envConfigProps.getProjectDir() != null) {
            return envConfigProps.getProjectDir().toString();
        }
        return System.getProperty("user.dir");
    }

    private String resolveTitle(String goal) {
        if (goal.length() <= 60) {
            return goal;
        }
        return goal.substring(0, 60) + "...";
    }
}
