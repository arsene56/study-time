package com.studytime.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ApiModels {
    private ApiModels() {
    }

    public record ChildView(String id, String name, int grade, String bedtime, int stars) {
    }

    public record DemoContextView(
            String familyId,
            String familyName,
            String parentId,
            String parentName,
            String parentRelation,
            List<ChildView> children) {
    }

    public record RecognizedTaskView(
            String id,
            String subject,
            String title,
            String taskType,
            String icon,
            int estimatedMinutes,
            String estimateSource,
            String difficulty,
            String eyeLoad,
            String confidence,
            String status) {
    }

    public record HomeworkBatchView(
            String id,
            String childId,
            String status,
            String recognitionMode,
            String sourceObjectKey,
            List<RecognizedTaskView> tasks) {
    }

    public record CreatePlanRequest(@NotBlank String startTime) {
    }

    public record ActorRequest(String actorId, String actorName, String actorRelation) {
    }

    public record CompletePlanItemRequest(
            String actorId,
            String actorName,
            String actorRelation,
            @Min(0) Integer actualSeconds) {
    }

    public record OverrunDecisionRequest(
            @NotBlank String decision,
            @Min(0) Integer actualSeconds,
            @Min(1) Integer extraMinutes,
            String actorId,
            String actorName,
            String actorRelation) {
    }

    public record ReorderPlanRequest(
            @NotEmpty List<String> orderedItemIds,
            String actorId,
            String actorName,
            String actorRelation) {
    }

    public record PlanItemView(
            String id,
            String homeworkTaskId,
            String kind,
            String subject,
            String title,
            String taskType,
            String icon,
            int estimatedMinutes,
            int sortOrder,
            String plannedStart,
            String plannedEnd,
            String status,
            int actualSeconds,
            String startedAt,
            String overrunDecision) {
    }

    public record PlanView(
            String id,
            String childId,
            String planDate,
            String startTime,
            String originalEndTime,
            String plannedEndTime,
            int bedtimeBufferMinutes,
            String warningMessage,
            String status,
            int version,
            List<PlanItemView> items) {
    }

    public record ActivityView(
            String id,
            String actorName,
            String actorRelation,
            String actionType,
            String description,
            String createdAt) {
    }

    public record SubjectSummaryView(
            String subject,
            int completedTasks,
            int averageEstimatedMinutes,
            int averageActualMinutes) {
    }

    public record DailyProgressView(
            String date,
            String dayLabel,
            int completedTasks,
            int totalTasks,
            int completionRate,
            int focusedMinutes) {
    }

    public record WeeklyComparisonView(
            int completionRateChange,
            int focusedMinutesChange,
            String trendText) {
    }

    public record WeeklyGoalView(
            String id,
            int targetTasks,
            int targetFocusMinutes,
            int bonusStars,
            int taskProgress,
            int focusProgress,
            int overallProgress,
            boolean achieved,
            String status,
            String claimedAt) {
    }

    public record BadgeView(
            String id,
            String icon,
            String title,
            String description,
            int progress,
            int target,
            boolean unlocked,
            String unlockedAt) {
    }

    public record WeeklyCommentView(
            String id,
            String actorName,
            String actorRelation,
            String content,
            String createdAt) {
    }

    public record WeeklyReportView(
            String weekStart,
            String weekEnd,
            int completedTasks,
            int totalTasks,
            int completionRate,
            int focusedMinutes,
            int starsEarned,
            int streakDays,
            String growthMessage,
            List<DailyProgressView> dailyProgress,
            WeeklyComparisonView comparison,
            WeeklyGoalView goal,
            List<SubjectSummaryView> subjects,
            List<BadgeView> badges,
            List<WeeklyCommentView> comments) {
    }

    public record AddWeeklyCommentRequest(
            @NotBlank @Size(max = 240) String content,
            String weekStart,
            String actorId,
            String actorName,
            String actorRelation) {
    }

    public record SaveWeeklyGoalRequest(
            @Min(1) int targetTasks,
            @Min(1) int targetFocusMinutes,
            @Min(1) int bonusStars,
            String actorId,
            String actorName,
            String actorRelation) {
    }

    public record ClaimWeeklyBonusRequest(
            String actorId,
            String actorName,
            String actorRelation) {
    }

    public record RewardView(
            String id,
            String name,
            String icon,
            int requiredStars,
            String category,
            String sourceType,
            String createdByName,
            boolean canRedeem,
            boolean owned,
            boolean equipped,
            String redemptionId,
            String redemptionStatus) {
    }

    public record RewardRedemptionView(
            String id,
            String rewardId,
            String rewardName,
            String rewardIcon,
            int requiredStars,
            String status,
            String requestedByName,
            String reviewedByName,
            String requestedAt,
            String reviewedAt) {
    }

    public record StarTransactionView(
            String id,
            int amount,
            String reason,
            String createdAt) {
    }

    public record RewardStoreView(
            int childStars,
            String equippedSkinRewardId,
            List<RewardView> rewards,
            List<RewardRedemptionView> redemptions,
            List<StarTransactionView> starTransactions) {
    }

    public record CreateRewardRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank String icon,
            @Min(1) int requiredStars,
            @NotBlank String category,
            String actorId,
            String actorName,
            String actorRelation) {
    }

    public record RedeemRewardRequest(
            @NotBlank String childId,
            String actorId,
            String actorName,
            String actorRelation) {
    }

    public record ReviewRewardRequest(
            @NotNull Boolean approved,
            String actorId,
            String actorName,
            String actorRelation) {
    }

    public record EquipSkinRequest(
            @NotBlank String childId,
            String actorId,
            String actorName,
            String actorRelation) {
    }

    public record ApiMessage(String message) {
    }
}
