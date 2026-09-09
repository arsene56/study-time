package com.studytime.homework;

import com.studytime.api.ApiModels.ActorRequest;
import com.studytime.api.ApiModels.CompletePlanItemRequest;
import com.studytime.api.ApiModels.OverrunDecisionRequest;
import com.studytime.api.ApiModels.PlanView;
import com.studytime.api.ApiModels.ReorderPlanRequest;
import com.studytime.domain.StudyTimeRepository;
import com.studytime.domain.StudyTimeRepository.ChildRow;
import com.studytime.domain.StudyTimeRepository.PlanItemRow;
import com.studytime.domain.StudyTimeRepository.PlanRow;
import com.studytime.realtime.PlanUpdateWebSocketHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PlanExecutionService {
    private final StudyTimeRepository repository;
    private final PlanUpdateWebSocketHandler updates;

    public PlanExecutionService(StudyTimeRepository repository, PlanUpdateWebSocketHandler updates) {
        this.repository = repository;
        this.updates = updates;
    }

    @Transactional
    public PlanView startItem(String itemId, ActorRequest request) {
        PlanItemRow item = repository.requirePlanItemForUpdate(itemId);
        PlanRow plan = repository.requirePlanForUpdate(item.planId());
        ChildRow child = repository.requireChild(plan.childId());
        if ("PENDING".equals(item.status())) {
            boolean anotherTaskIsActive = repository.findPlanItemRows(plan.id()).stream()
                    .anyMatch(other -> !other.id().equals(item.id()) && "ACTIVE".equals(other.status()));
            if (anotherTaskIsActive) {
                throw new IllegalArgumentException("请先完成或跳过当前正在进行的任务");
            }
            repository.startPlanItem(itemId);
            repository.updatePlanSummary(plan.id(), plan.plannedEndTime(), "IN_PROGRESS",
                    repository.scheduleWarning(child, plan.plannedEndTime(), plan.bedtimeBufferMinutes()));
            Actor actor = childActor(child, request == null ? null : request.actorId(),
                    request == null ? null : request.actorName(),
                    request == null ? null : request.actorRelation());
            log(child, actor, "TASK_STARTED", "开始了“" + item.title() + "”");
        }
        PlanView result = repository.findTodayPlan(child.id()).orElseThrow();
        updates.publish(child.id(), "TASK_STARTED");
        return result;
    }

    @Transactional
    public PlanView completeItem(String itemId, CompletePlanItemRequest request) {
        PlanItemRow item = repository.requirePlanItemForUpdate(itemId);
        PlanRow plan = repository.requirePlanForUpdate(item.planId());
        ChildRow child = repository.requireChild(plan.childId());
        if (!"DONE".equals(item.status()) && !"SKIPPED".equals(item.status())) {
            int actualSeconds = actualSeconds(item, request == null ? null : request.actualSeconds());
            repository.completePlanItem(item, actualSeconds);
            int stars = "BREAK".equals(item.kind()) ? 1 : 5;
            repository.addStars(child.id(), stars);
            repository.insertStarTransaction(child.id(), stars, "完成“" + item.title() + "”", item.id());
            Actor actor = childActor(child, request == null ? null : request.actorId(),
                    request == null ? null : request.actorName(),
                    request == null ? null : request.actorRelation());
            LocalTime oldEnd = plan.plannedEndTime();
            PlanView rescheduled = reschedule(plan, child);
            String changed = oldEnd.equals(LocalTime.parse(rescheduled.plannedEndTime()))
                    ? ""
                    : "，预计完成时间由 " + oldEnd + " 调整为 " + rescheduled.plannedEndTime();
            log(child, actor, "TASK_COMPLETED",
                    "完成了“" + item.title() + "”，获得 " + stars + " 颗星" + changed);
        }
        PlanView result = repository.findTodayPlan(child.id()).orElseThrow();
        updates.publish(child.id(), "TASK_COMPLETED");
        return result;
    }

    @Transactional
    public PlanView decideOverrun(String itemId, OverrunDecisionRequest request) {
        PlanItemRow item = repository.requirePlanItemForUpdate(itemId);
        PlanRow plan = repository.requirePlanForUpdate(item.planId());
        ChildRow child = repository.requireChild(plan.childId());
        String decision = request.decision().toUpperCase();
        if (!Set.of("SKIP", "CONTINUE").contains(decision)) {
            throw new IllegalArgumentException("超时选择只能是 SKIP 或 CONTINUE");
        }
        if ("DONE".equals(item.status()) || "SKIPPED".equals(item.status())) {
            return repository.findTodayPlan(child.id()).orElseThrow();
        }

        int actualSeconds = actualSeconds(item, request.actualSeconds());
        int extraMinutes = request.extraMinutes() == null ? 10 : request.extraMinutes();
        LocalTime oldEnd = plan.plannedEndTime();
        if ("SKIP".equals(decision)) {
            repository.recordOverrunDecision(
                    item, decision, "SKIPPED", actualSeconds, item.estimatedMinutes());
        } else {
            repository.recordOverrunDecision(
                    item, decision, "ACTIVE", actualSeconds, item.estimatedMinutes() + extraMinutes);
        }
        PlanView rescheduled = reschedule(plan, child);
        Actor actor = childActor(child, request.actorId(), request.actorName(), request.actorRelation());
        String action = "SKIP".equals(decision) ? "选择暂时跳过“" + item.title() + "”"
                : "选择继续挑战“" + item.title() + "”，增加 " + extraMinutes + " 分钟";
        log(child, actor, "PLAN_OVERRUN_DECISION",
                action + "；预计完成时间由 " + oldEnd + " 调整为 " + rescheduled.plannedEndTime());
        updates.publish(child.id(), "PLAN_RESCHEDULED");
        return rescheduled;
    }

    @Transactional
    public PlanView reorder(String planId, ReorderPlanRequest request) {
        PlanRow plan = repository.requirePlanForUpdate(planId);
        ChildRow child = repository.requireChild(plan.childId());
        List<PlanItemRow> current = repository.findPlanItemRows(planId);
        List<String> requested = request.orderedItemIds();
        Set<String> expectedIds = current.stream().map(PlanItemRow::id).collect(java.util.stream.Collectors.toSet());
        if (requested.size() != current.size()
                || new HashSet<>(requested).size() != requested.size()
                || !expectedIds.equals(new HashSet<>(requested))) {
            throw new IllegalArgumentException("调整顺序必须包含计划内的全部任务且不能重复");
        }

        Map<String, PlanItemRow> byId = new LinkedHashMap<>();
        current.forEach(item -> byId.put(item.id(), item));
        for (int index = 0; index < requested.size(); index++) {
            PlanItemRow item = byId.get(requested.get(index));
            repository.updatePlanItemSchedule(item.id(), index, item.plannedStart(), item.plannedEnd());
        }
        PlanView rescheduled = reschedule(plan, child);
        Actor actor = childActor(child, request.actorId(), request.actorName(), request.actorRelation());
        log(child, actor, "PLAN_REORDERED",
                "调整了任务顺序，新的预计完成时间为 " + rescheduled.plannedEndTime());
        updates.publish(child.id(), "PLAN_REORDERED");
        return rescheduled;
    }

    private PlanView reschedule(PlanRow plan, ChildRow child) {
        List<PlanItemRow> items = repository.findPlanItemRows(plan.id());
        LocalTime cursor = plan.startTime();
        int order = 0;
        boolean allFinished = true;
        boolean started = false;
        for (PlanItemRow item : items) {
            int minutes = effectiveMinutes(item);
            LocalTime end = cursor.plusMinutes(minutes);
            repository.updatePlanItemSchedule(item.id(), order++, cursor, end);
            cursor = end;
            boolean finished = "DONE".equals(item.status()) || "SKIPPED".equals(item.status());
            allFinished &= finished;
            started |= finished || "ACTIVE".equals(item.status());
        }
        String status = allFinished ? "COMPLETED" : started ? "IN_PROGRESS" : "READY";
        repository.updatePlanSummary(
                plan.id(), cursor, status,
                repository.scheduleWarning(child, cursor, plan.bedtimeBufferMinutes()));
        return repository.findTodayPlan(child.id()).orElseThrow();
    }

    private int effectiveMinutes(PlanItemRow item) {
        if (("DONE".equals(item.status()) || "SKIPPED".equals(item.status())) && item.actualSeconds() > 0) {
            return Math.max(1, (int) Math.ceil(item.actualSeconds() / 60.0));
        }
        if ("SKIPPED".equals(item.status())) {
            return 0;
        }
        return item.estimatedMinutes();
    }

    private int actualSeconds(PlanItemRow item, Integer supplied) {
        if (supplied != null && supplied > 0) {
            return supplied;
        }
        if (item.startedAt() != null) {
            long elapsed = Duration.between(item.startedAt(), LocalDateTime.now()).toSeconds();
            if (elapsed > 0) {
                return Math.toIntExact(Math.min(Integer.MAX_VALUE, elapsed));
            }
        }
        return item.estimatedMinutes() * 60;
    }

    private Actor childActor(ChildRow child, String actorId, String actorName, String relation) {
        return new Actor(
                valueOr(actorId, child.id().replace("demo-child-", "demo-child-member-")),
                valueOr(actorName, child.name()),
                valueOr(relation, "孩子"));
    }

    private void log(ChildRow child, Actor actor, String actionType, String description) {
        repository.insertActivity(
                UUID.randomUUID().toString(), child.familyId(), child.id(),
                actor.id(), actor.name(), actor.relation(), actionType, description);
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record Actor(String id, String name, String relation) {
    }
}
