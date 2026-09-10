package com.studytime.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import org.slf4j.MDC;

import java.util.List;

public final class ApiModels {
    private ApiModels() {
    }

    public record StudentView(String id, String name, int grade, String bedtime, int stars) {
    }

    public record DemoContextView(
            String familyId,
            String familyName,
            String parentId,
            String parentName,
            String parentRelation,
            List<StudentView> students) {
    }

    public record RecognizedTaskView(
            String id,
            String subject,
            String title,
            String taskType,
            String icon,
            int estimatedMinutes,
            int baseEstimatedMinutes,
            String estimateSource,
            int estimateSampleSize,
            String estimateConfidence,
            String estimateReason,
            String difficulty,
            String eyeLoad,
            String confidence,
            Double ocrConfidence,
            boolean manuallyEdited,
            String status) {
    }

    public record HomeworkBatchView(
            String id,
            String studentId,
            String status,
            String recognitionMode,
            String sourceObjectKey,
            String ocrProvider,
            String ocrRequestId,
            String ocrRawText,
            Double ocrAverageConfidence,
            String recognitionError,
            String recognizedAt,
            List<RecognizedTaskView> tasks) {
    }

    public record RecognitionCapabilityView(
            String configuredProvider,
            String providerLabel,
            boolean realOcrAvailable,
            String message) {
    }

    public record SubjectPersonalizationView(
            String subject,
            int sampleSize,
            int averageEstimatedMinutes,
            int averageActualMinutes,
            int pacePercent,
            String trend,
            String suggestion) {
    }

    public record PersonalizationProfileView(
            String studentId,
            int totalSamples,
            String level,
            String confidence,
            int overallPacePercent,
            String summary,
            List<SubjectPersonalizationView> subjects) {
    }

    public record SaveHomeworkTaskRequest(
            @NotBlank @Size(max = 30) String subject,
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 30) String taskType,
            @Min(1) int estimatedMinutes,
            @NotBlank String difficulty,
            @NotBlank String eyeLoad) {
    }

    public record CreatePlanRequest(
            @NotBlank
            @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d", message = "开始时间必须是 HH:mm 格式")
            String startTime) {
    }

    public record CompletePlanItemRequest(
            @Min(0) @Max(43200) Integer actualSeconds) {
    }

    public record OverrunDecisionRequest(
            @NotBlank String decision,
            @Min(0) @Max(43200) Integer actualSeconds,
            @Min(1) @Max(60) Integer extraMinutes) {
    }

    public record ReorderPlanRequest(
            @NotEmpty @Size(max = 100) List<String> orderedItemIds) {
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
            String studentId,
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

    public record NotificationView(
            String id,
            String studentId,
            String studentName,
            String eventType,
            String title,
            String message,
            String actionPath,
            boolean read,
            String createdAt) {
    }

    public record NotificationFeedView(
            int unreadCount,
            List<NotificationView> items) {
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
            String weekStart) {
    }

    public record SaveWeeklyGoalRequest(
            @Min(1) int targetTasks,
            @Min(1) int targetFocusMinutes,
            @Min(1) int bonusStars) {
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
            int studentStars,
            String equippedSkinRewardId,
            List<RewardView> rewards,
            List<RewardRedemptionView> redemptions,
            List<StarTransactionView> starTransactions) {
    }

    public record CreateRewardRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 12) String icon,
            @Min(1) int requiredStars,
            @NotBlank String category) {
    }

    public record RedeemRewardRequest(@NotBlank String studentId) {
    }

    public record ReviewRewardRequest(@NotNull Boolean approved) {
    }

    public record EquipSkinRequest(@NotBlank String studentId) {
    }

    public record ApiMessage(String message, String requestId) {
        public ApiMessage(String message) {
            this(message, MDC.get("requestId"));
        }
    }
}
