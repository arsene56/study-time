package com.studytime.homework;

import com.studytime.api.ApiModels.DemoContextView;
import com.studytime.api.ApiModels.HomeworkBatchView;
import com.studytime.api.ApiModels.PersonalizationProfileView;
import com.studytime.api.ApiModels.PlanView;
import com.studytime.api.ApiModels.RecognitionCapabilityView;
import com.studytime.api.ApiModels.SaveHomeworkTaskRequest;
import com.studytime.domain.StudyTimeRepository;
import com.studytime.domain.StudyTimeRepository.BatchRow;
import com.studytime.domain.StudyTimeRepository.ChildRow;
import com.studytime.domain.StudyTimeRepository.TaskRow;
import com.studytime.homework.HomeworkTextParser.ParsedTask;
import com.studytime.homework.PersonalizedEstimationService.Estimate;
import com.studytime.ocr.OcrGateway;
import com.studytime.ocr.OcrResult;
import com.studytime.realtime.PlanUpdateWebSocketHandler;
import com.studytime.storage.HomeworkStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class HomeworkService {
    private final StudyTimeRepository repository;
    private final HomeworkStorage storage;
    private final PlanningService planningService;
    private final PlanUpdateWebSocketHandler updates;
    private final OcrGateway ocrGateway;
    private final HomeworkTextParser textParser;
    private final PersonalizedEstimationService estimationService;

    public HomeworkService(
            StudyTimeRepository repository,
            HomeworkStorage storage,
            PlanningService planningService,
            PlanUpdateWebSocketHandler updates,
            OcrGateway ocrGateway,
            HomeworkTextParser textParser,
            PersonalizedEstimationService estimationService) {
        this.repository = repository;
        this.storage = storage;
        this.planningService = planningService;
        this.updates = updates;
        this.ocrGateway = ocrGateway;
        this.textParser = textParser;
        this.estimationService = estimationService;
    }

    public DemoContextView demoContext() {
        return new DemoContextView(
                "demo-family", "林家的作业时光", "demo-parent-mom", "林妈妈", "妈妈",
                repository.findDemoChildren());
    }

    public RecognitionCapabilityView recognitionCapability() {
        return ocrGateway.capability();
    }

    public PersonalizationProfileView personalizationProfile(String childId) {
        return estimationService.profile(childId);
    }

    @Transactional
    public HomeworkBatchView mockRecognize(String childId, MultipartFile file) {
        ChildRow child = repository.requireChild(childId);
        String objectKey = file == null || file.isEmpty() ? null : storage.store(childId, file);
        String batchId = UUID.randomUUID().toString();
        repository.insertBatch(
                batchId, child, objectKey, "MOCK_OCR", "PENDING_CONFIRMATION", "MOCK");
        List<TaskSeed> seeds = defaultSeeds(child.grade());
        for (int index = 0; index < seeds.size(); index++) {
            insertSeed(batchId, child, seeds.get(index), index);
        }
        repository.updateBatchRecognition(
                batchId, "PENDING_CONFIRMATION", "mock-" + batchId,
                "语文：朗读《荷花》两遍\n数学：练习册第 32–33 页\n英语：Unit 2 单词跟读 3 遍\n科学：观察一株植物并记录",
                98D, null);
        recordActivity(child, "HOMEWORK_RECOGNIZED",
                "使用预置示例识别出 " + seeds.size() + " 项任务，等待确认");
        updates.publish(childId, "HOMEWORK_RECOGNIZED");
        return repository.getBatch(batchId);
    }

    @Transactional
    public HomeworkBatchView recognize(String childId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请先选择一张作业照片");
        }
        ChildRow child = repository.requireChild(childId);
        String objectKey = storage.store(childId, file);
        String batchId = UUID.randomUUID().toString();
        String provider = ocrGateway.configuredProvider().toUpperCase(Locale.ROOT);
        repository.insertBatch(batchId, child, objectKey, "REAL_OCR", "PROCESSING", provider);

        try {
            OcrResult result = ocrGateway.recognize(readBytes(file));
            List<ParsedTask> parsedTasks = textParser.parse(result, child.grade());
            if (parsedTasks.isEmpty()) {
                repository.updateBatchRecognition(
                        batchId, "NEEDS_MANUAL_ENTRY", result.requestId(), result.rawText(),
                        result.averageConfidence(), "未能从图片文字中拆分出明确作业，请手动添加");
                recordActivity(child, "HOMEWORK_RECOGNITION_NEEDS_REVIEW", "图片已读取，但需要手动录入作业内容");
            } else {
                for (int index = 0; index < parsedTasks.size(); index++) {
                    ParsedTask task = parsedTasks.get(index);
                    insertSeed(batchId, child, new TaskSeed(
                            task.subject(), task.title(), task.taskType(), task.icon(), task.baseMinutes(),
                            task.difficulty(), task.eyeLoad(), task.confidence(), task.ocrConfidence()), index);
                }
                repository.updateBatchRecognition(
                        batchId, "PENDING_CONFIRMATION", result.requestId(), result.rawText(),
                        result.averageConfidence(), null);
                recordActivity(child, "HOMEWORK_RECOGNIZED",
                        "真实 OCR 识别出 " + parsedTasks.size() + " 项任务，等待确认");
            }
        } catch (RuntimeException exception) {
            String message = safeError(exception.getMessage());
            repository.updateBatchRecognition(
                    batchId, "NEEDS_MANUAL_ENTRY", null, null, null, message);
            recordActivity(child, "HOMEWORK_RECOGNITION_NEEDS_REVIEW", "自动识别未完成，已转为手动录入");
        }
        updates.publish(childId, "HOMEWORK_RECOGNIZED");
        return repository.getBatch(batchId);
    }

    @Transactional
    public HomeworkBatchView addTask(String batchId, SaveHomeworkTaskRequest request) {
        BatchRow batch = repository.requireEditableBatch(batchId);
        validateTaskRequest(request);
        int order = repository.nextTaskSortOrder(batchId);
        repository.insertTask(new TaskRow(
                UUID.randomUUID().toString(), batchId, batch.childId(), request.subject().trim(),
                request.title().trim(), request.taskType().trim(),
                GradeTaskRules.icon(request.subject().trim(), request.taskType().trim()),
                request.estimatedMinutes(), request.estimatedMinutes(), "MANUAL_OVERRIDE", 0, "HIGH",
                "由家长手动录入", request.difficulty(), request.eyeLoad(), "HIGH", null, true,
                "PENDING_CONFIRMATION", order));
        repository.markBatchPendingConfirmation(batchId);
        recordActivity(repository.requireChild(batch.childId()), "HOMEWORK_TASK_ADDED",
                "手动添加了“" + request.title().trim() + "”");
        updates.publish(batch.childId(), "HOMEWORK_RECOGNIZED");
        return repository.getBatch(batchId);
    }

    @Transactional
    public HomeworkBatchView updateTask(String taskId, SaveHomeworkTaskRequest request) {
        validateTaskRequest(request);
        TaskRow current = repository.requireEditableTask(taskId);
        ChildRow child = repository.requireChild(current.childId());
        int refreshedBaseMinutes = GradeTaskRules.baselineMinutes(
                child.grade(), request.title().trim(), request.taskType().trim());
        TaskRow updated = new TaskRow(
                current.id(), current.batchId(), current.childId(), request.subject().trim(),
                request.title().trim(), request.taskType().trim(),
                GradeTaskRules.icon(request.subject().trim(), request.taskType().trim()),
                request.estimatedMinutes(), refreshedBaseMinutes, "MANUAL_OVERRIDE", 0, "HIGH",
                "由家长确认时修订", request.difficulty(), request.eyeLoad(), "HIGH",
                current.ocrConfidence(), true, current.status(), current.sortOrder());
        repository.updateTask(updated);
        recordActivity(child, "HOMEWORK_TASK_UPDATED", "修订了“" + request.title().trim() + "”");
        updates.publish(child.id(), "HOMEWORK_RECOGNIZED");
        return repository.getBatch(current.batchId());
    }

    @Transactional
    public HomeworkBatchView deleteTask(String taskId) {
        TaskRow task = repository.requireEditableTask(taskId);
        repository.deleteTask(taskId);
        ChildRow child = repository.requireChild(task.childId());
        recordActivity(child, "HOMEWORK_TASK_DELETED", "删除了识别项“" + task.title() + "”");
        updates.publish(child.id(), "HOMEWORK_RECOGNIZED");
        return repository.getBatch(task.batchId());
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

    private void insertSeed(String batchId, ChildRow child, TaskSeed seed, int index) {
        Estimate estimate = estimationService.estimate(
                child.id(), seed.subject(), seed.taskType(), seed.minutes());
        repository.insertTask(new TaskRow(
                UUID.randomUUID().toString(), batchId, child.id(), seed.subject(), seed.title(),
                seed.taskType(), seed.icon(), estimate.minutes(), estimate.baseMinutes(), estimate.source(),
                estimate.sampleSize(), estimate.confidence(), estimate.reason(), seed.difficulty(), seed.eyeLoad(),
                seed.confidence(), seed.ocrConfidence(), false, "PENDING_CONFIRMATION", index));
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取作业照片", exception);
        }
    }

    private void validateTaskRequest(SaveHomeworkTaskRequest request) {
        if (!List.of("EASY", "MODERATE", "CHALLENGE").contains(request.difficulty())) {
            throw new IllegalArgumentException("难度必须是 EASY、MODERATE 或 CHALLENGE");
        }
        if (!List.of("LOW", "HIGH").contains(request.eyeLoad())) {
            throw new IllegalArgumentException("用眼强度必须是 LOW 或 HIGH");
        }
        if (request.estimatedMinutes() > 180) {
            throw new IllegalArgumentException("单项作业预计时长不能超过 180 分钟");
        }
    }

    private void recordActivity(ChildRow child, String action, String description) {
        repository.insertActivity(
                UUID.randomUUID().toString(), child.familyId(), child.id(), "demo-parent-mom", "林妈妈", "妈妈",
                action, description);
    }

    private String safeError(String message) {
        String value = message == null || message.isBlank() ? "OCR 服务暂时不可用，请手动录入" : message;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private List<TaskSeed> defaultSeeds(int grade) {
        int mathMinutes = grade <= 2 ? 18 : grade <= 4 ? 30 : 40;
        return List.of(
                new TaskSeed("语文", "朗读《荷花》两遍", "朗读/阅读", "📖", 12, "EASY", "HIGH", "HIGH", 98D),
                new TaskSeed("数学", "练习册第 32–33 页", "书写", "✏️", mathMinutes, "CHALLENGE", "HIGH", "HIGH", 98D),
                new TaskSeed("英语", "Unit 2 单词跟读 3 遍", "口语", "🎧", 15, "EASY", "LOW", "HIGH", 98D),
                new TaskSeed("科学", "观察一株植物并记录", "实践", "🔍", 20, "MODERATE", "LOW", "LOW", 78D));
    }

    private record TaskSeed(
            String subject,
            String title,
            String taskType,
            String icon,
            int minutes,
            String difficulty,
            String eyeLoad,
            String confidence,
            Double ocrConfidence) {
    }
}
