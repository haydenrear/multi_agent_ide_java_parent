package com.hayden.multiagentidelib.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.commitdiffcontext.cdc_utils.SetFromHeader;
import com.hayden.commitdiffcontext.mcp.ToolCarrier;
import com.hayden.multiagentidelib.model.worktree.SandboxValidationResult;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandbox;
import com.hayden.acp_cdc_ai.repository.RequestContext;
import com.hayden.acp_cdc_ai.repository.RequestContextRepository;
import com.hayden.acp_cdc_ai.permission.IPermissionGate;
import lombok.RequiredArgsConstructor;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.ShellTools;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static com.hayden.acp_cdc_ai.acp.AcpChatModel.MCP_SESSION_HEADER;

@Component
@Profile("goose")
@RequiredArgsConstructor
public class AcpTooling implements ToolCarrier {

    private final FileSystemTools fileSystemTools = new FileSystemTools();

    private final ShellTools shellTools = new ShellTools();

    private final RequestContextRepository requestContextRepository;

    private final ObjectMapper objectMapper;

    private IPermissionGate permissionGate;

    @Autowired(required = false)
    public void setPermissionGate(IPermissionGate permissionGate) {
        this.permissionGate = permissionGate;
    }

    @Tool(name = "read", description = "Reads a file from the local filesystem. You can access any file directly by using this tool.\nAssume this tool is able to read all files on the machine. If the User provides a path to a file assume that path is valid. It is okay to read a file that does not exist; an error will be returned.\n\nUsage:\n- The file_path parameter must be an absolute path, not a relative path\n- By default, it reads up to 2000 lines starting from the beginning of the file\n- You can optionally specify a line offset and limit (especially handy for long files), but it's recommended to read the whole file by not providing these parameters\n- Any lines longer than 2000 characters will be truncated\n- Results are returned using cat -n format, with line numbers starting at 1\n- This tool allows Claude Code to read images (eg PNG, JPG, etc). When reading an image file the contents are presented visually as Claude Code is a multimodal LLM.\n- This tool can read PDF files (.pdf). PDFs are processed page by page, extracting both text and visual content for analysis.\n- This tool can read Jupyter notebooks (.ipynb files) and returns all cells with their outputs, combining code, text, and visualizations.\n- This tool can only read files, not directories. To read a directory, use an ls command via the Bash tool.\n- You can call multiple tools in a single response. It is always better to speculatively read multiple potentially useful files in parallel.\n- You will regularly be asked to read screenshots. If the user provides a path to a screenshot, ALWAYS use this tool to view the file at the path. This tool will work with all temporary file paths.\n- If you read a file that exists but has empty contents you will receive a system reminder warning in place of file contents.\n")
    public String read(
            @SetFromHeader(MCP_SESSION_HEADER)
            String sessionId,
            String filePath,
            Integer offset,
            Integer limit,
            ToolContext toolContext
    ) {
        SandboxValidationResult validation = validateSandbox(sessionId, filePath);
        if (!validation.allowed()) {
            return toJson(validation);
        }
        return fileSystemTools.read(filePath, offset, limit, toolContext);
    }

    @Tool(name = "edit", description = "Performs exact string replacements in files.\n\nUsage:\n- You must use your `Read` tool at least once in the conversation before editing. This tool will error if you attempt an edit without reading the file.\n- When editing text from Read tool output, ensure you preserve the exact indentation (tabs/spaces) as it appears AFTER the line number prefix. The line number prefix format is: spaces + line number + tab. Everything after that tab is the actual file content to match. Never include any part of the line number prefix in the old_string or new_string.\n- ALWAYS prefer editing existing files in the codebase. NEVER write new files unless explicitly required.\n- Only use emojis if the user explicitly requests it. Avoid adding emojis to files unless asked.\n- The edit will FAIL if `old_string` is not unique in the file. Either provide a larger string with more surrounding context to make it unique or use `replace_all` to change every instance of `old_string`.\n- Use `replace_all` for replacing and renaming strings across the file. This parameter is useful if you want to rename a variable for instance.\n")
    public String edit(
            @SetFromHeader(MCP_SESSION_HEADER)
            String sessionId,
            String filePath,
            String old_string,
            String new_string,
            Boolean replace_all,
            ToolContext toolContext
    ) {
        SandboxValidationResult validation = validateSandbox(sessionId, filePath);
        if (!validation.allowed()) {
            return toJson(validation);
        }
        return fileSystemTools.edit(filePath, old_string, new_string, replace_all, toolContext);
    }

    @Tool(name = "write", description = "Writes a file to the local filesystem.\n\nUsage:\n- This tool will overwrite the existing file if there is one at the provided path.\n- If this is an existing file, you MUST use the Read tool first to read the file's contents. This tool will fail if you did not read the file first.\n- ALWAYS prefer editing existing files in the codebase. NEVER write new files unless explicitly required.\n- NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.\n- Only use emojis if the user explicitly requests it. Avoid writing emojis to files unless asked.\n")
    public String write(
            @SetFromHeader(MCP_SESSION_HEADER)
            String sessionId,
            String filePath,
            String content,
            ToolContext toolContext
    ) {
        SandboxValidationResult validation = validateSandbox(sessionId, filePath);
        if (!validation.allowed()) {
            return toJson(validation);
        }
        return fileSystemTools.write(filePath, content, toolContext);
    }

    private SandboxValidationResult validateSandbox(String sessionId, String filePath) {
        if (!StringUtils.hasText(sessionId)) {
            return SandboxValidationResult.denied("missing session id", filePath);
        }
        RequestContext context = requestContextRepository.findBySessionId(sessionId).orElse(null);
        if (context == null) {
            return SandboxValidationResult.denied("missing request context for session", filePath);
        }
        WorktreeSandbox sandbox = WorktreeSandbox.fromRequestContext(context);
        return sandbox.validatePath(filePath);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{\"allowed\":false,\"reason\":\"sandbox validation failed\"}";
        }
    }

    @Tool(name = "bash", description = "Executes a given bash command in a persistent shell session with optional timeout, ensuring proper handling and security measures.\n\nIMPORTANT: This tool is for terminal operations like git, npm, docker, etc. DO NOT use it for file operations (reading, writing, editing, searching, finding files) - use the specialized tools for this instead.\n\nBefore executing the command, please follow these steps:\n\n1. Directory Verification:\n- If the command will create new directories or files, first use `ls` to verify the parent directory exists and is the correct location\n- For example, before running \"mkdir foo/bar\", first use `ls foo` to check that \"foo\" exists and is the intended parent directory\n\n2. Command Execution:\n- Always quote file paths that contain spaces with double quotes (e.g., cd \"path with spaces/file.txt\")\n- Examples of proper quoting:\n\t- cd \"/Users/[REDACTED]/My Documents\" (correct)\n\t- cd /Users/[REDACTED]/My Documents (incorrect - will fail)\n\t- python \"/path/with spaces/script.py\" (correct)\n\t- python /path/with spaces/script.py (incorrect - will fail)\n- After ensuring proper quoting, execute the command.\n- Capture the output of the command.\n\nUsage notes:\n- The command argument is required.\n- You can specify an optional timeout in milliseconds (up to 600000ms / 10 minutes). If not specified, commands will timeout after 120000ms (2 minutes).\n- It is very helpful if you write a clear, concise description of what this command does in 5-10 words.\n- If the output exceeds 30000 characters, output will be truncated before being returned to you.\n- You can use the `run_in_background` parameter to run the command in the background, which allows you to continue working while the command runs. You can monitor the output using the Bash tool as it becomes available. Never use `run_in_background` to run 'sleep' as it will return immediately. You do not need to use '&' at the end of the command when using this parameter.\n\n- Avoid using Bash with the `find`, `grep`, `cat`, `head`, `tail`, `sed`, `awk`, or `echo` commands, unless explicitly instructed or when these commands are truly necessary for the task. Instead, always prefer using the dedicated tools for these commands:\n\t- File search: Use Glob (NOT find or ls)\n\t- Content search: Use Grep (NOT grep or rg)\n\t- Read files: Use Read (NOT cat/head/tail)\n\t- Edit files: Use Edit (NOT sed/awk)\n\t- Write files: Use Write (NOT echo >/cat <<EOF)\n\t- Communication: Output text directly (NOT echo/printf)\n- When issuing multiple commands:\n\t- If the commands are independent and can run in parallel, make multiple Bash tool calls in a single message. For example, if you need to run \"git status\" and \"git diff\", send a single message with two Bash tool calls in parallel.\n\t- If the commands depend on each other and must run sequentially, use a single Bash call with '&&' to chain them together (e.g., `git add . && git commit -m \"message\" && git push`). For instance, if one operation must complete before another starts (like mkdir before cp, Write before Bash for git operations, or git add before git commit), run these operations sequentially instead.\n\t- Use ';' only when you need to run commands sequentially but don't care if earlier commands fail\n\t- DO NOT use newlines to separate commands (newlines are ok in quoted strings)\n- Try to maintain your current working directory throughout the session by using absolute paths and avoiding usage of `cd`. You may use `cd` if the User explicitly requests it.\n\t<good-example>\n\tpytest /foo/bar/tests\n\t</good-example>\n\t<bad-example>\n\tcd /foo/bar && pytest tests\n\t</bad-example>\n\n# Committing changes with git\n\nOnly create commits when requested by the user. If unclear, ask first. When the user asks you to create a new git commit, follow these steps carefully:\n\nGit Safety Protocol:\n- NEVER update the git config\n- NEVER run destructive/irreversible git commands (like push --force, hard reset, etc) unless the user explicitly requests them\n- NEVER skip hooks (--no-verify, --no-gpg-sign, etc) unless the user explicitly requests it\n- NEVER run force push to main/master, warn the user if they request it\n- Avoid git commit --amend.  ONLY use --amend when either (1) user explicitly requested amend OR (2) adding edits from pre-commit hook (additional instructions below)\n- Before amending: ALWAYS check authorship (git log -1 --format='%an %ae')\n- NEVER commit changes unless the user explicitly asks you to. It is VERY IMPORTANT to only commit when explicitly asked, otherwise the user will feel that you are being too proactive.\n\n1. You can call multiple tools in a single response. When multiple independent pieces of information are requested and all commands are likely to succeed, run multiple tool calls in parallel for optimal performance. run the following bash commands in parallel, each using the Bash tool:\n- Run a git status command to see all untracked files.\n- Run a git diff command to see both staged and unstaged changes that will be committed.\n- Run a git log command to see recent commit messages, so that you can follow this repository's commit message style.\n2. Analyze all staged changes (both previously staged and newly added) and draft a commit message:\n- Summarize the nature of the changes (eg. new feature, enhancement to an existing feature, bug fix, refactoring, test, docs, etc.). Ensure the message accurately reflects the changes and their purpose (i.e. \"add\" means a wholly new feature, \"update\" means an enhancement to an existing feature, \"fix\" means a bug fix, etc.).\n- Do not commit files that likely contain secrets (.env, credentials.json, etc). Warn the user if they specifically request to commit those files\n- Draft a concise (1-2 sentences) commit message that focuses on the \"why\" rather than the \"what\"\n- Ensure it accurately reflects the changes and their purpose\n3. You can call multiple tools in a single response. When multiple independent pieces of information are requested and all commands are likely to succeed, run multiple tool calls in parallel for optimal performance. run the following commands:\n- Add relevant untracked files to the staging area.\n- Create the commit with a message ending with:\n\uD83E\uDD16 Generated with [Claude Code](https://claude.com/claude-code)\n\nCo-Authored-By: Claude <noreply@anthropic.com>\n- Run git status after the commit completes to verify success.\nNote: git status depends on the commit completing, so run it sequentially after the commit.\n4. If the commit fails due to pre-commit hook changes, retry ONCE. If it succeeds but files were modified by the hook, verify it's safe to amend:\n- Check authorship: git log -1 --format='%an %ae'\n- Check not pushed: git status shows \"Your branch is ahead\"\n- If both true: amend your commit. Otherwise: create NEW commit (never amend other developers' commits)\n\nImportant notes:\n- NEVER run additional commands to read or explore code, besides git bash commands\n- NEVER use the TodoWrite or Task tools\n- DO NOT push to the remote repository unless the user explicitly asks you to do so\n- IMPORTANT: Never use git commands with the -i flag (like git rebase -i or git add -i) since they require interactive input which is not supported.\n- If there are no changes to commit (i.e., no untracked files and no modifications), do not create an empty commit\n- In order to ensure good formatting, ALWAYS pass the commit message via a HEREDOC, a la this example:\n<example>\ngit commit -m \"$(cat <<'EOF'\nCommit message here.\n\n\uD83E\uDD16 Generated with [Claude Code](https://claude.com/claude-code)\n\nCo-Authored-By: Claude <noreply@anthropic.com>\nEOF\n)\"\n</example>\n\n# Creating pull requests\nUse the gh command via the Bash tool for ALL GitHub-related tasks including working with issues, pull requests, checks, and releases. If given a Github URL use the gh command to get the information needed.\n\nIMPORTANT: When the user asks you to create a pull request, follow these steps carefully:\n\n1. You can call multiple tools in a single response. When multiple independent pieces of information are requested and all commands are likely to succeed, run multiple tool calls in parallel for optimal performance. run the following bash commands in parallel using the Bash tool, in order to understand the current state of the branch since it diverged from the main branch:\n- Run a git status command to see all untracked files\n- Run a git diff command to see both staged and unstaged changes that will be committed\n- Check if the current branch tracks a remote branch and is up to date with the remote, so you know if you need to push to the remote\n- Run a git log command and `git diff [base-branch]...HEAD` to understand the full commit history for the current branch (from the time it diverged from the base branch)\n2. Analyze all changes that will be included in the pull request, making sure to look at all relevant commits (NOT just the latest commit, but ALL commits that will be included in the pull request!!!), and draft a pull request summary\n3. You can call multiple tools in a single response. When multiple independent pieces of information are requested and all commands are likely to succeed, run multiple tool calls in parallel for optimal performance. run the following commands in parallel:\n- Create new branch if needed\n- Push to remote with -u flag if needed\n- Create PR using gh pr create with the format below. Use a HEREDOC to pass the body to ensure correct formatting.\n<example>\ngh pr create --title \"the pr title\" --body \"$(cat <<'EOF'\n\n## Summary\n<1-3 bullet points>\n\n## Test plan\n[Bulleted markdown checklist of TODOs for testing the pull request...]\n\n\uD83E\uDD16 Generated with [Claude Code](https://claude.com/claude-code)\nEOF\n)\"\n</example>\n\nImportant:\n- DO NOT use the TodoWrite or Task tools\n- Return the PR URL when you're done, so the user can see it\n\n# Other common operations\n- View comments on a Github PR: gh api repos/foo/bar/pulls/123/comments\n")
    public String bash(String command, Long timeout, String description, Boolean runInBackground) {
        return shellTools.bash(command, timeout, description, runInBackground);
    }

    @Tool(name = "killShell", description = "- Kills a running background bash shell by its ID\n- Takes a shell_id parameter identifying the shell to kill\n- Returns a success or failure status\n- Use this tool when you need to terminate a long-running shell\n- Shell IDs can be found using the /bashes command\n")
    public String killShell(String bash_id) {
        return shellTools.killShell(bash_id);
    }

    @Tool(name = "BashOutput", description = "- Retrieves output from a running or completed background bash shell\n- Takes a shell_id parameter identifying the shell\n- Always returns only new output since the last check\n- Returns stdout and stderr output along with shell status\n- Supports optional regex filtering to show only lines matching a pattern\n- Use this tool when you need to monitor or check the output of a long-running shell\n- Shell IDs can be found using the /bashes command\n")
    public String bashOutput(String bash_id, String filter) {
        return shellTools.bashOutput(bash_id, filter);
    }
}
