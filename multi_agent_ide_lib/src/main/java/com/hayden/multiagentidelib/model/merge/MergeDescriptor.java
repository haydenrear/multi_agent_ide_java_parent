package com.hayden.multiagentidelib.model.merge;

import com.hayden.multiagentidelib.model.MergeResult;
import lombok.Builder;
import lombok.With;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes the result of a merge operation for a single agent's worktree.
 * Attached to individual agent results after merge attempts.
 */
@Builder(toBuilder = true)
@With
public record MergeDescriptor(
        MergeDirection mergeDirection,
        boolean successful,
        List<String> conflictFiles,
        List<SubmoduleMergeResult> submoduleMergeResults,
        MergeResult mainWorktreeMergeResult,
        MergeErrorType errorType,
        String errorMessage,
        List<WorktreeCommitMetadata> commitMetadata
) {
    public MergeDescriptor {
        if (mergeDirection == null) {
            throw new IllegalArgumentException("mergeDirection required");
        }
        if (conflictFiles == null) {
            conflictFiles = new ArrayList<>();
        }
        if (submoduleMergeResults == null) {
            submoduleMergeResults = new ArrayList<>();
        }
        if (commitMetadata == null) {
            commitMetadata = new ArrayList<>();
        }
    }
    
    /**
     * Creates a successful merge descriptor with no conflicts.
     */
    public static MergeDescriptor success(MergeDirection direction, MergeResult mainResult, 
                                          List<SubmoduleMergeResult> submoduleResults) {
        return MergeDescriptor.builder()
                .mergeDirection(direction)
                .successful(true)
                .conflictFiles(List.of())
                .submoduleMergeResults(submoduleResults != null ? submoduleResults : List.of())
                .mainWorktreeMergeResult(mainResult)
                .errorType(MergeErrorType.NONE)
                .commitMetadata(List.of())
                .build();
    }
    
    /**
     * Creates a failed merge descriptor with conflict information.
     */
    public static MergeDescriptor conflict(MergeDirection direction, List<String> conflictFiles,
                                           MergeResult mainResult, List<SubmoduleMergeResult> submoduleResults,
                                           String errorMessage) {
        return conflict(direction, conflictFiles, mainResult, submoduleResults, errorMessage, MergeErrorType.MERGE_CONFLICT);
    }

    public static MergeDescriptor conflict(MergeDirection direction, List<String> conflictFiles,
                                           MergeResult mainResult, List<SubmoduleMergeResult> submoduleResults,
                                           String errorMessage,
                                           MergeErrorType errorType) {
        return MergeDescriptor.builder()
                .mergeDirection(direction)
                .successful(false)
                .conflictFiles(conflictFiles != null ? conflictFiles : List.of())
                .submoduleMergeResults(submoduleResults != null ? submoduleResults : List.of())
                .mainWorktreeMergeResult(mainResult)
                .errorType(errorType != null ? errorType : MergeErrorType.UNKNOWN)
                .errorMessage(errorMessage)
                .commitMetadata(List.of())
                .build();
    }
    
    /**
     * Creates a no-op merge descriptor when no merge was needed.
     */
    public static MergeDescriptor noOp(MergeDirection direction) {
        return MergeDescriptor.builder()
                .mergeDirection(direction)
                .successful(true)
                .conflictFiles(List.of())
                .submoduleMergeResults(List.of())
                .errorType(MergeErrorType.NONE)
                .commitMetadata(List.of())
                .build();
    }
}
