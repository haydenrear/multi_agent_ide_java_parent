package com.hayden.multiagentide.prompt;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;

public class TicketOrchestratorWorktreePromptContributor implements PromptContributor {

    private static final String TEMPLATE = """
        ## Ticket Worktree Instructions

        You are responsible for creating a separate git worktree for each ticket agent you spawn.
        - Create each ticket worktree as a sibling of the parent worktree directory.
        - Parent worktree path: {{parent_worktree_path}}
        - Each ticket agent must receive its own worktree path in its TicketAgentRequest.
        - Do not reuse the parent worktree for ticket agents.
        """;

    @Override
    public String name() {
        return "ticket-orchestrator-worktree";
    }

    @Override
    public boolean include(PromptContext promptContext) {
        return promptContext != null && promptContext.agentType() == AgentType.TICKET_ORCHESTRATOR;
    }

    @Override
    public String contribute(PromptContext context) {
        String parentPath = "(unknown)";
        if (context != null && context.previousRequest() != null) {
            WorktreeSandboxContext worktreeContext = context.previousRequest().worktreeContext();
            if (worktreeContext != null && worktreeContext.mainWorktree() != null) {
                parentPath = worktreeContext.mainWorktree().worktreePath().toString();
            }
        }
        return template().replace("{{parent_worktree_path}}", parentPath);
    }

    @Override
    public String template() {
        return TEMPLATE;
    }

    @Override
    public int priority() {
        return 90;
    }
}
