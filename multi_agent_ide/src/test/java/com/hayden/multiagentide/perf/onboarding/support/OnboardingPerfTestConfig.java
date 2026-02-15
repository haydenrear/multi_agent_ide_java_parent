package com.hayden.multiagentide.perf.onboarding.support;

import com.hayden.commitdiffcontext.cdc_config.OnboardingPipelineConfigProps;
import com.hayden.commitdiffcontext.git.GitFactory;
import com.hayden.commitdiffcontext.git.RepositoryHolder;
import com.hayden.commitdiffcontext.git.entity.CommitDiff;
import com.hayden.commitdiffcontext.git.operations.ConsumingOperation;
import com.hayden.commitdiffcontext.git.parser.support.SetEmbedding;
import com.hayden.commitdiffcontext.git.parser.support.episodic.model.OnboardingRunMetadata;
import com.hayden.commitdiffcontext.git.parser.support.episodic.service.OnboardingOrchestrationService;
import com.hayden.commitdiffcontext.git.parser.support.episodic.service.RewriteHistoryService;
import com.hayden.commitdiffcontext.git.parser.support.episodic.service.SerialSegmentEpisodicService;
import com.hayden.commitdiffcontext.git.repo.CodeBranchRepository;
import com.hayden.commitdiffcontext.git.repo.CommitDiffRepository;
import com.hayden.commitdiffmodel.codegen.types.ParseGitOptions;
import com.hayden.commitdiffmodel.codegen.types.RagOptions;
import com.hayden.commitdiffmodel.err.GitErrors;
import com.hayden.utilitymodule.result.Result;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import com.hayden.multiagentide.integration.onboarding.support.OnboardingCommitDiffContextTestConfig;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@SpringBootTest
@ActiveProfiles({"test", "perf"})
@Import(OnboardingCommitDiffContextTestConfig.class)
public abstract class OnboardingPerfTestConfig {

    protected final OpenSourceRepositoryFactory openSourceRepositoryFactory = new OpenSourceRepositoryFactory();

    @Autowired
    protected OnboardingPipelineConfigProps onboardingPipelineConfigProps;
    @Autowired
    protected OnboardingOrchestrationService onboardingOrchestrationService;
    @Autowired
    protected RewriteHistoryService rewriteHistoryService;
    @Autowired
    protected SerialSegmentEpisodicService serialSegmentEpisodicService;
    @Autowired
    protected SetEmbedding setEmbedding;
    @Autowired
    protected CommitDiffRepository commitDiffRepository;
    @Autowired
    protected CodeBranchRepository codeBranchRepository;
    @Autowired
    protected Environment environment;

    @BeforeEach
    void configureOnboardingPipeline() {
        onboardingPipelineConfigProps.setDryRun(false);
        onboardingPipelineConfigProps.setKeepIngestionRepo(false);
        onboardingPipelineConfigProps.setContinueOnSegmentFailure(false);
    }

    protected PerfSettings perfSettings() {
        return new PerfSettings(
                environment.getProperty("onboarding.perf.repo-url", "https://github.com/spring-projects/spring-petclinic.git"),
                environment.getProperty("onboarding.perf.branch", "main"),
                Integer.parseInt(environment.getProperty("onboarding.perf.clone-depth", "250")),
                Integer.parseInt(environment.getProperty("onboarding.perf.max-commit-depth", "250")),
                Integer.parseInt(environment.getProperty("onboarding.perf.max-commit-diffs", "500")),
                Long.parseLong(environment.getProperty("onboarding.perf.max-runtime-seconds", "1800")),
                Integer.parseInt(environment.getProperty("onboarding.perf.min-embedded-commit-diffs", "25"))
        );
    }

    protected OnboardingExecutionContext onboardingContext(Path repositoryRoot,
                                                           String branch,
                                                           int maxCommitDepth,
                                                           int maxCommitDiffs) throws Exception {
        var repoArgs = RepositoryHolder.RepositoryArgs.builder()
                .branch(branch)
                .ragOptions(RagOptions.newBuilder()
                        .parseGitOptions(ParseGitOptions.newBuilder()
                                .maxCommitDepth(maxCommitDepth)
                                .maxCommitDiffs(maxCommitDiffs)
                                .build())
                        .build())
                .gitRepoDirectory(GitFactory.retrieveGitRepoDir(repositoryRoot))
                .build();
        Git git = Git.open(repositoryRoot.toFile());
        RepositoryHolder holder = new RepositoryHolder(git, repoArgs, () -> {});
        var operationArgs = ConsumingOperation.OperationArgs.builder()
                .repositoryArgs(repoArgs)
                .holder(holder)
                .build();
        return new OnboardingExecutionContext(holder, operationArgs);
    }

    protected Result<OnboardingRunMetadata, GitErrors.GitAggregateError> runOnboarding(OnboardingExecutionContext context) {
        var embeddingResult = setEmbedding.parse(context.operationArgs());
        if (embeddingResult.e().isPresent()) {
            return Result.err(embeddingResult.e().get());
        }

        var parseErrors = embeddingResult.r().get().aggregateAllErrors();
        if (parseErrors.isPresent()) {
            return Result.err(parseErrors.get());
        }

        var embeddingValidation = validateEmbeddingPersistence(context.operationArgs().repositoryArgs());
        if (embeddingValidation.e().isPresent()) {
            return Result.err(embeddingValidation.e().get());
        }

        var rewriteResult = rewriteHistoryService.rewrite(context.operationArgs());
        if (rewriteResult.e().isPresent()) {
            return Result.err(rewriteResult.e().get());
        }

        var rewritten = rewriteResult.r().get();
        var episodic = serialSegmentEpisodicService.execute(rewritten);
        if (episodic.e().isPresent()) {
            return Result.err(episodic.e().get());
        }

        return onboardingOrchestrationService.findRun(rewritten.onboardingRunId())
                .map(Result::<OnboardingRunMetadata, GitErrors.GitAggregateError>ok)
                .orElseGet(() -> Result.err(new GitErrors.GitAggregateError("Run metadata missing after execution.")));
    }

    protected Result<EmbeddingValidationSummary, GitErrors.GitAggregateError> validateEmbeddingPersistence(RepositoryHolder.RepositoryArgs repoArgs) {
        if (!codeBranchRepository.containsBranch(repoArgs)) {
            return Result.err(new GitErrors.GitAggregateError(
                    "Code branch was not persisted for repo=%s branch=%s".formatted(repoArgs.repoPath(), repoArgs.branch())));
        }

        List<CommitDiff> scopedCommitDiffs = commitDiffRepository.findAll().stream()
                .filter(cd -> belongsToRepoBranch(cd, repoArgs))
                .toList();
        if (scopedCommitDiffs.isEmpty()) {
            return Result.err(new GitErrors.GitAggregateError(
                    "No commit diffs persisted for repo=%s branch=%s".formatted(repoArgs.repoPath(), repoArgs.branch())));
        }

        long withOnboardingMetadata = scopedCommitDiffs.stream()
                .filter(cd -> cd.getOnboardingEmbeddedAtEpochMillis() != null)
                .count();
        if (withOnboardingMetadata == 0) {
            return Result.err(new GitErrors.GitAggregateError(
                    "No commit diffs contained onboarding embedding metadata for repo=%s branch=%s"
                            .formatted(repoArgs.repoPath(), repoArgs.branch())));
        }

        long embeddedCount = scopedCommitDiffs.stream()
                .filter(CommitDiff::isEmbedded)
                .count();
        return Result.ok(new EmbeddingValidationSummary(
                scopedCommitDiffs.size(),
                embeddedCount,
                withOnboardingMetadata
        ));
    }

    private static boolean belongsToRepoBranch(CommitDiff commitDiff, RepositoryHolder.RepositoryArgs repoArgs) {
        Set<com.hayden.commitdiffcontext.repo_ref.entity.CodeBranchRef> refs = commitDiff.getCommitDiffCodeBranchRefs();
        if (refs == null || refs.isEmpty()) {
            return false;
        }
        String repoPath = repoArgs.repoPath();
        String sourcePath = repoArgs.getSourceOfTruthDir();
        String branch = repoArgs.branch();
        return refs.stream()
                .filter(Objects::nonNull)
                .anyMatch(ref -> Objects.equals(branch, ref.branch())
                        && (Objects.equals(repoPath, ref.url()) || Objects.equals(sourcePath, ref.url())));
    }

    protected OnboardingRunMetadata latestRun() {
        return onboardingOrchestrationService.findRuns().stream()
                .max(Comparator.comparing(OnboardingRunMetadata::getStartedAt))
                .orElseThrow();
    }

    protected record PerfSettings(
            String repoUrl,
            String branch,
            int cloneDepth,
            int maxCommitDepth,
            int maxCommitDiffs,
            long maxRuntimeSeconds,
            int minEmbeddedCommitDiffs
    ) {
    }

    protected record EmbeddingValidationSummary(
            int commitDiffCount,
            long embeddedCommitDiffCount,
            long commitDiffsWithOnboardingMetadata
    ) {
    }

    protected record OnboardingExecutionContext(
            RepositoryHolder holder,
            ConsumingOperation.OperationArgs operationArgs
    ) implements AutoCloseable {
        @Override
        public void close() {
            holder.close();
        }
    }
}
