package com.studytime.homework;

import com.studytime.api.ApiModels.CompletePlanItemRequest;
import com.studytime.api.ApiModels.DemoContextView;
import com.studytime.api.ApiModels.HomeworkBatchView;
import com.studytime.api.ApiModels.PlanView;
import com.studytime.domain.StudyTimeRepository;
import com.studytime.domain.StudyTimeRepository.ChildRow;
import com.studytime.domain.StudyTimeRepository.PlanItemRow;
import com.studytime.domain.StudyTimeRepository.TaskRow;
import com.studytime.realtime.PlanUpdateWebSocketHandler;
import com.studytime.storage.HomeworkStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
public class HomeworkService {
    private final StudyTimeRepository repository;
    private final HomeworkStorage storage;
    private final PlanningService planningService;
    private final PlanUpdateWebSocketHandler updates;

    public HomeworkService(
            StudyTimeRepository repository,
            HomeworkStorage storage,
            PlanningService planningService,
            PlanUpdateWebSocketHandler updates) {
        this.repository = repository;
        this.storage = storage;
        this.planningService = planningService;
        this.updates = updates;
    }

    public DemoContextView demoContext() {
        return new DemoContextView(
                "demo-family", "林家的作业时光", "demo-parent-mom", "林妈妈", "妈妈",
                repository.findDemoChildren());
    }

    @Transactional
    public HomeworkBatchView mockRecognize(String childId, MultipartFile file) {
        ChildRow child = repository.requireChild(childId);
        String objectKey = file == null || file.isEmpty() ? null : storage.store(childId, file);
        String batchId = UUID.randomUUID().toString();
        repository.insertBatch(batchId, child, objectKey);
        List<TaskSeed> seeds = defaultSeeds(child.grade());
        for (int index = 0; index < seeds.size(); index++) {
            TaskSeed seed = seeds.get(index);
            repository.insertTask(new TaskRow(
                    UUID.randomUUID().toString(), batchId, childId, seed.subject(), seed.title(),
                    seed.taskType(), seed.icon(), seed.minutes(), seed.difficulty(), seed.eyeLoad(),
                    seed.confidence(), "PENDING_CONFIRMATION", index));
        }
        repository.insertActivity(
                UUID.randomUUID().toString(), child.familyId(), child.id(), "demo-parent-mom", "林妈妈", "妈妈",
                "HOMEWORK_RECOGNIZED", "上传了作业照片，模拟识别出 " + seeds.size() + " 项任务，等待确认");
        updates.publish(childId, "HOMEWORK_RECOGNIZED");
        return repository.getBatch(batchId);
    }

    public PlanView confirmAndPlan(String batchId, LocalTime startTime) {
        PlanView plan = planningService.createPlan(batchId, startTime);
        updates.publish(plan.childId(), "PLAN_CREATED");
        return plan;
    }

    public PlanView todayPlan(String childId) {
        repository.requireChild(childId);
        return repository.findTodayPlan(childId)
                .orElseThrow(() -> new IllegalArgumentException("今天还没有生成作业计划"));
    }

    @Transactional
    public PlanView completeItem(String itemId, CompletePlanItemRequest request) {
        PlanItemRow item = repository.requirePlanItemForUpdate(itemId);
        String childId = repository.findChildIdByPlan(item.planId());
        ChildRow child = repository.requireChild(childId);
        if (!"DONE".equals(item.status())) {
            repository.completePlanItem(item);
            int stars = "BREAK".equals(item.kind()) ? 1 : 5;
            repository.addStars(childId, stars);
            String actorId = valueOr(request == null ? null : request.actorId(), demoChildMemberId(childId));
            String actorName = valueOr(request == null ? null : request.actorName(), child.name());
            String relation = valueOr(request == null ? null : request.actorRelation(), "孩子");
            repository.insertActivity(
                    UUID.randomUUID().toString(), child.familyId(), childId, actorId, actorName, relation,
                    "TASK_COMPLETED", "完成了“" + item.title() + "”，获得 " + stars + " 颗星");
        }
        PlanView plan = repository.findTodayPlan(childId).orElseThrow();
        updates.publish(childId, "TASK_COMPLETED");
        return plan;
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String demoChildMemberId(String childId) {
        return childId.replace("demo-child-", "demo-child-member-");
    }

    private List<TaskSeed> defaultSeeds(int grade) {
        int mathMinutes = grade <= 2 ? 18 : grade <= 4 ? 30 : 40;
        return List.of(
                new TaskSeed("语文", "朗读《荷花》两遍", "朗读", "📖", 12, "EASY", "HIGH", "HIGH"),
                new TaskSeed("数学", "练习册第 32–33 页", "书写", "✏️", mathMinutes, "CHALLENGE", "HIGH", "HIGH"),
                new TaskSeed("英语", "Unit 2 单词跟读 3 遍", "口语", "🎧", 15, "EASY", "LOW", "HIGH"),
                new TaskSeed("科学", "观察一株植物并记录", "实践", "🔍", 20, "MODERATE", "LOW", "LOW"));
    }

    private record TaskSeed(
            String subject,
            String title,
            String taskType,
            String icon,
            int minutes,
            String difficulty,
            String eyeLoad,
            String confidence) {
    }
}
