package com.studytime.api;

import jakarta.validation.constraints.NotBlank;

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

    public record CompletePlanItemRequest(String actorId, String actorName, String actorRelation) {
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
            int actualSeconds) {
    }

    public record PlanView(
            String id,
            String childId,
            String planDate,
            String startTime,
            String originalEndTime,
            String plannedEndTime,
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

    public record ApiMessage(String message) {
    }
}
