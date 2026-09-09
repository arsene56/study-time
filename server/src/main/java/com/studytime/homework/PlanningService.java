package com.studytime.homework;

import com.studytime.api.ApiModels.PlanView;
import com.studytime.domain.StudyTimeRepository;
import com.studytime.domain.StudyTimeRepository.ChildRow;
import com.studytime.domain.StudyTimeRepository.PlanItemRow;
import com.studytime.domain.StudyTimeRepository.TaskRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PlanningService {
    private final StudyTimeRepository repository;

    public PlanningService(StudyTimeRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PlanView createPlan(String batchId, LocalTime startTime) {
        List<TaskRow> tasks = repository.findTasksByBatch(batchId);
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("该批次没有可排期的作业");
        }
        ChildRow child = repository.requireChild(tasks.getFirst().childId());
        List<TaskRow> orderedTasks = orderTasks(tasks);
        List<ItemDraft> drafts = addHealthyBreaks(orderedTasks, child.grade());
        drafts.add(new ItemDraft(null, "ROUTINE", "整理", "检查作业并整理书包", "整理", "🎒", 10));

        LocalTime cursor = startTime;
        List<ScheduledDraft> scheduled = new ArrayList<>();
        for (ItemDraft draft : drafts) {
            LocalTime end = cursor.plusMinutes(draft.minutes());
            scheduled.add(new ScheduledDraft(draft, cursor, end));
            cursor = end;
        }

        String planId = UUID.randomUUID().toString();
        repository.replaceTodayPlan(child.id(), planId, startTime, cursor);
        int order = 0;
        for (ScheduledDraft item : scheduled) {
            repository.insertPlanItem(new PlanItemRow(
                    UUID.randomUUID().toString(), planId, item.draft().taskId(), item.draft().kind(),
                    item.draft().subject(), item.draft().title(), item.draft().taskType(), item.draft().icon(),
                    item.draft().minutes(), order++, item.start(), item.end(), "PENDING", 0, null, null));
        }
        repository.confirmBatch(batchId);
        repository.insertActivity(
                UUID.randomUUID().toString(), child.familyId(), child.id(), "demo-parent-mom", "林妈妈", "妈妈",
                "PLAN_CREATED", "确认了识别结果，嘀嘀已生成今天的作业计划");
        return repository.findTodayPlan(child.id()).orElseThrow();
    }

    List<TaskRow> orderTasks(List<TaskRow> tasks) {
        List<TaskRow> result = new ArrayList<>();
        Set<String> selected = new HashSet<>();

        tasks.stream()
                .filter(task -> "EASY".equals(task.difficulty()))
                .min(Comparator.comparingInt(TaskRow::estimatedMinutes))
                .ifPresent(task -> {
                    result.add(task);
                    selected.add(task.id());
                });

        tasks.stream()
                .filter(task -> !selected.contains(task.id()) && "CHALLENGE".equals(task.difficulty()))
                .sorted(Comparator.comparingInt(TaskRow::estimatedMinutes).reversed())
                .forEach(task -> {
                    result.add(task);
                    selected.add(task.id());
                });

        tasks.stream()
                .filter(task -> !selected.contains(task.id()) && !"EASY".equals(task.difficulty()))
                .sorted(Comparator.comparingInt(TaskRow::sortOrder))
                .forEach(task -> {
                    result.add(task);
                    selected.add(task.id());
                });

        tasks.stream()
                .filter(task -> !selected.contains(task.id()))
                .sorted(Comparator.comparingInt(TaskRow::estimatedMinutes).reversed())
                .forEach(result::add);

        return result;
    }

    private List<ItemDraft> addHealthyBreaks(List<TaskRow> tasks, int grade) {
        int focusLimit = grade <= 2 ? 20 : grade <= 4 ? 25 : 30;
        int eyeMinutes = 0;
        List<ItemDraft> result = new ArrayList<>();
        for (int index = 0; index < tasks.size(); index++) {
            TaskRow task = tasks.get(index);
            result.add(new ItemDraft(
                    task.id(), "HOMEWORK", task.subject(), task.title(), task.taskType(), task.icon(),
                    task.estimatedMinutes()));
            if ("HIGH".equals(task.eyeLoad())) {
                eyeMinutes += task.estimatedMinutes();
            }
            if (eyeMinutes >= focusLimit && index < tasks.size() - 1) {
                result.add(new ItemDraft(null, "BREAK", "休息", "远眺窗外，起身活动一下", "护眼休息", "🌿", 10));
                eyeMinutes = 0;
            }
        }
        return result;
    }

    private record ItemDraft(
            String taskId,
            String kind,
            String subject,
            String title,
            String taskType,
            String icon,
            int minutes) {
    }

    private record ScheduledDraft(ItemDraft draft, LocalTime start, LocalTime end) {
    }
}
