package com.hayden.multiagentide.integration.onboarding.support;

import com.hayden.commitdiffcontext.cdc_config.OnboardingPipelineConfigProps;
import com.hayden.commitdiffcontext.git.GitFactory;
import com.hayden.commitdiffcontext.git.RepositoryHolder;
import com.hayden.commitdiffcontext.git.entity.CommitDiff;
import com.hayden.commitdiffcontext.git.embed.ModelServerEmbeddingClient;
import com.hayden.commitdiffcontext.git.operations.ConsumingOperation;
import com.hayden.commitdiffcontext.git.parser.support.SetEmbedding;
import com.hayden.commitdiffcontext.git.parser.support.episodic.EpisodicMemoryAgent;
import com.hayden.commitdiffcontext.git.parser.support.episodic.model.OnboardingRunMetadata;
import com.hayden.commitdiffcontext.git.parser.support.episodic.service.OnboardingOrchestrationService;
import com.hayden.commitdiffcontext.git.parser.support.episodic.service.RewriteHistoryService;
import com.hayden.commitdiffcontext.git.parser.support.episodic.service.SerialSegmentEpisodicService;
import com.hayden.commitdiffcontext.git.repo.CodeBranchRepository;
import com.hayden.commitdiffcontext.git.repo.CommitDiffRepository;
import com.hayden.commitdiffcontext.message.Embedding;
import com.hayden.commitdiffcontext.message.EmbeddingClient;
import com.hayden.commitdiffmodel.codegen.types.ParseGitOptions;
import com.hayden.commitdiffmodel.codegen.types.RagOptions;
import com.hayden.commitdiffmodel.err.GitErrors;
import com.hayden.multiagentide.agent.episodic.HindsightOnboardingClient;
import com.hayden.utilitymodule.result.Result;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@SpringBootTest
@ActiveProfiles({"test", "testdocker"})
@Import(OnboardingCommitDiffContextTestConfig.class)
public abstract class OnboardingIntegrationTestConfig {

    protected final RepositoryFixtureFactory repositoryFixtureFactory = new RepositoryFixtureFactory();

    @MockitoBean
    protected ModelServerEmbeddingClient modelServerEmbeddingClient;

    @MockitoBean
    protected HindsightOnboardingClient hindsightOnboardingClient;

    @MockitoBean
    protected EpisodicMemoryAgent episodicMemoryAgent;

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

    @BeforeEach
    void initOnboardingServices() {
        onboardingPipelineConfigProps.setDryRun(false);
        onboardingPipelineConfigProps.setKeepIngestionRepo(false);
        onboardingPipelineConfigProps.setContinueOnSegmentFailure(false);

        // Default mock embedding behavior for integration tests that don't customize embedding stubs.
        lenient().when(modelServerEmbeddingClient.doEmbed(any(), any()))
                .thenAnswer(invocation -> {
                    Object target = invocation.getArgument(0);
                    if (!(target instanceof CommitDiff commitDiff)) {
                        return Result.err(new EmbeddingClient.EmbeddingError(
                                "Unsupported embedding target in onboarding integration test: " +
                                        (target == null ? "null" : target.getClass().getName())));
                    }
                    float[] embeddingVector = java.util.Arrays.copyOf(
                            com.hayden.commitdiffcontext.git.entity.Embedding.INITIALIZED,
                            com.hayden.commitdiffcontext.git.entity.Embedding.INITIALIZED.length
                    );
                    embeddingVector[0] = 0.42f;
                    commitDiff.setEmbedding(embeddingVector);
                    commitDiff.setEmbeddedHashItem("mock-" + UUID.randomUUID());
                    return Result.ok(new Embedding.EmbeddingResponse<>(embeddingVector, commitDiff));
                });
    }

    protected OnboardingExecutionContext onboardingContext(Path repositoryRoot) throws Exception {
        return onboardingContext(repositoryRoot, "main", 500, 500);
    }

    protected OnboardingExecutionContext onboardingContext(Path repositoryRoot,
                                                           String branch,
                                                           int maxCommitDepth,
                                                           int maxCommitDiffs) throws Exception {
        var repoArgs = RepositoryHolder.RepositoryArgs.builder()
                .branch(branch)
                .ragOptions(
                        RagOptions.newBuilder()
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
        if (embeddingResult.isErr()) {
            return Result.err(embeddingResult.e().get());
        }
        var parseErrors = embeddingResult.r().get().aggregateAllErrors();
        if (parseErrors.isPresent()) {
            return Result.err(parseErrors.get());
        }
        var embeddingValidation = validateEmbeddingPersistence(context.operationArgs().repositoryArgs());
        if (embeddingValidation.isErr()) {
            return Result.err(embeddingValidation.e().get());
        }

        var rewriteResult = rewriteHistoryService.rewrite(context.operationArgs());
        if (rewriteResult.isErr()) {
            return Result.err(rewriteResult.e().get());
        }
        var rewritten = rewriteResult.r().get();
        if (onboardingPipelineConfigProps.isDryRun()) {
            serialSegmentEpisodicService.markDryRunComplete(rewritten);
            return onboardingOrchestrationService.findRun(rewritten.onboardingRunId())
                    .map(Result::<OnboardingRunMetadata, GitErrors.GitAggregateError>ok)
                    .orElseGet(() -> Result.err(new GitErrors.GitAggregateError("Run metadata missing after dry-run.")));
        }
        var episodic = serialSegmentEpisodicService.execute(rewritten);
        if (episodic.isErr()) {
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
