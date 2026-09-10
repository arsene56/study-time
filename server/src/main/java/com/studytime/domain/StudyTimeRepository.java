package com.studytime.domain;

import com.studytime.api.ApiModels.ActivityView;
import com.studytime.api.ApiModels.ChildView;
import com.studytime.api.ApiModels.HomeworkBatchView;
import com.studytime.api.ApiModels.PlanItemView;
import com.studytime.api.ApiModels.PlanView;
import com.studytime.api.ApiModels.RecognizedTaskView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Repository
public class StudyTimeRepository {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcClient jdbc;

    public StudyTimeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record ChildRow(String id, String familyId, String name, int grade, LocalTime bedtime, int stars) {
    }

    public record TaskRow(
            String id,
            String batchId,
            String childId,
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
            String status,
            int sortOrder) {
    }

    public record BatchRow(String id, String familyId, String childId, String status) {
    }

    public record HistorySample(
            String subject,
            String taskType,
            int estimatedMinutes,
            int actualSeconds,
            LocalDateTime completedAt) {
    }

    public record PlanItemRow(
            String id,
            String planId,
            String homeworkTaskId,
            String kind,
            String subject,
            String title,
            String taskType,
            String icon,
            int estimatedMinutes,
            int sortOrder,
            LocalTime plannedStart,
            LocalTime plannedEnd,
            String status,
            int actualSeconds,
            LocalDateTime startedAt,
            String overrunDecision) {
    }

    public record PlanRow(
            String id,
            String childId,
            LocalDate planDate,
            LocalTime startTime,
            LocalTime originalEndTime,
            LocalTime plannedEndTime,
            int bedtimeBufferMinutes,
            String status,
            int version) {
    }

    public List<ChildView> findDemoChildren() {
        return jdbc.sql("SELECT id, name, grade, bedtime, stars FROM children WHERE family_id = 'demo-family' ORDER BY grade DESC")
                .query((rs, rowNum) -> new ChildView(
                        rs.getString("id"), rs.getString("name"), rs.getInt("grade"),
                        rs.getTime("bedtime").toLocalTime().format(TIME), rs.getInt("stars")))
                .list();
    }

    public ChildRow requireChild(String childId) {
        return jdbc.sql("SELECT id, family_id, name, grade, bedtime, stars FROM children WHERE id = :id")
                .param("id", childId)
                .query((rs, rowNum) -> new ChildRow(
                        rs.getString("id"), rs.getString("family_id"), rs.getString("name"),
                        rs.getInt("grade"), rs.getTime("bedtime").toLocalTime(), rs.getInt("stars")))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("未找到孩子：" + childId));
    }

    public void insertBatch(
            String id,
            ChildRow child,
            String objectKey,
            String recognitionMode,
            String status,
            String ocrProvider) {
        jdbc.sql("""
                        INSERT INTO homework_batches
                        (id, family_id, child_id, source_object_key, recognition_mode, status, created_by, ocr_provider)
                        VALUES (:id, :familyId, :childId, :objectKey, :recognitionMode, :status,
                                'demo-parent-mom', :ocrProvider)
                        """)
                .param("id", id)
                .param("familyId", child.familyId())
                .param("childId", child.id())
                .param("objectKey", objectKey)
                .param("recognitionMode", recognitionMode)
                .param("status", status)
                .param("ocrProvider", ocrProvider)
                .update();
    }

    public void updateBatchRecognition(
            String batchId,
            String status,
            String requestId,
            String rawText,
            Double averageConfidence,
            String error) {
        jdbc.sql("""
                        UPDATE homework_batches
                        SET status = :status,
                            ocr_request_id = :requestId,
                            ocr_raw_text = :rawText,
                            ocr_average_confidence = :averageConfidence,
                            recognition_error = :error,
                            recognized_at = CURRENT_TIMESTAMP(6)
                        WHERE id = :id
                        """)
                .param("status", status)
                .param("requestId", requestId)
                .param("rawText", rawText)
                .param("averageConfidence", averageConfidence)
                .param("error", error)
                .param("id", batchId)
                .update();
    }

    public void insertTask(TaskRow task) {
        jdbc.sql("""
                        INSERT INTO homework_tasks
                        (id, batch_id, child_id, subject, title, task_type, icon, estimated_minutes,
                         base_estimated_minutes, estimate_source, estimate_sample_size, estimate_confidence,
                         estimate_reason, difficulty, eye_load, confidence, ocr_confidence, manually_edited,
                         status, sort_order)
                        VALUES (:id, :batchId, :childId, :subject, :title, :taskType, :icon, :minutes,
                                :baseMinutes, :estimateSource, :sampleSize, :estimateConfidence, :estimateReason,
                                :difficulty, :eyeLoad, :confidence, :ocrConfidence, :manuallyEdited,
                                :status, :sortOrder)
                        """)
                .param("id", task.id())
                .param("batchId", task.batchId())
                .param("childId", task.childId())
                .param("subject", task.subject())
                .param("title", task.title())
                .param("taskType", task.taskType())
                .param("icon", task.icon())
                .param("minutes", task.estimatedMinutes())
                .param("baseMinutes", task.baseEstimatedMinutes())
                .param("estimateSource", task.estimateSource())
                .param("sampleSize", task.estimateSampleSize())
                .param("estimateConfidence", task.estimateConfidence())
                .param("estimateReason", task.estimateReason())
                .param("difficulty", task.difficulty())
                .param("eyeLoad", task.eyeLoad())
                .param("confidence", task.confidence())
                .param("ocrConfidence", task.ocrConfidence())
                .param("manuallyEdited", task.manuallyEdited())
                .param("status", task.status())
                .param("sortOrder", task.sortOrder())
                .update();
    }

    public List<TaskRow> findTasksByBatch(String batchId) {
        return jdbc.sql("""
                        SELECT id, batch_id, child_id, subject, title, task_type, icon, estimated_minutes,
                               base_estimated_minutes, estimate_source, estimate_sample_size, estimate_confidence,
                               estimate_reason, difficulty, eye_load, confidence, ocr_confidence, manually_edited,
                               status, sort_order
                        FROM homework_tasks WHERE batch_id = :batchId ORDER BY sort_order
                        """)
                .param("batchId", batchId)
                .query(this::mapTask)
                .list();
    }

    public HomeworkBatchView getBatch(String batchId) {
        return jdbc.sql("""
                        SELECT id, child_id, status, recognition_mode, source_object_key, ocr_provider,
                               ocr_request_id, ocr_raw_text, ocr_average_confidence, recognition_error, recognized_at
                        FROM homework_batches WHERE id = :id
                        """)
                .param("id", batchId)
                .query((rs, rowNum) -> new HomeworkBatchView(
                        rs.getString("id"), rs.getString("child_id"), rs.getString("status"),
                        rs.getString("recognition_mode"), rs.getString("source_object_key"),
                        rs.getString("ocr_provider"), rs.getString("ocr_request_id"),
                        rs.getString("ocr_raw_text"), nullableDouble(rs, "ocr_average_confidence"),
                        rs.getString("recognition_error"), formatDateTime(rs.getTimestamp("recognized_at")),
                        findTasksByBatch(batchId).stream().map(this::toTaskView).toList()))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("未找到作业批次：" + batchId));
    }

    public BatchRow requireEditableBatch(String batchId) {
        BatchRow batch = jdbc.sql("SELECT id, family_id, child_id, status FROM homework_batches WHERE id = :id")
                .param("id", batchId)
                .query((rs, rowNum) -> new BatchRow(
                        rs.getString("id"), rs.getString("family_id"), rs.getString("child_id"),
                        rs.getString("status")))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("未找到作业批次：" + batchId));
        if ("CONFIRMED".equals(batch.status())) {
            throw new IllegalArgumentException("已生成计划的作业不能再修改");
        }
        return batch;
    }

    public TaskRow requireEditableTask(String taskId) {
        TaskRow task = jdbc.sql("""
                        SELECT t.id, t.batch_id, t.child_id, t.subject, t.title, t.task_type, t.icon,
                               t.estimated_minutes, t.base_estimated_minutes, t.estimate_source,
                               t.estimate_sample_size, t.estimate_confidence, t.estimate_reason,
                               t.difficulty, t.eye_load, t.confidence, t.ocr_confidence, t.manually_edited,
                               t.status, t.sort_order
                        FROM homework_tasks t
                        JOIN homework_batches b ON b.id = t.batch_id
                        WHERE t.id = :id AND b.status <> 'CONFIRMED'
                        """)
                .param("id", taskId)
                .query(this::mapTask)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("未找到可修改的作业项：" + taskId));
        return task;
    }

    public int nextTaskSortOrder(String batchId) {
        return jdbc.sql("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM homework_tasks WHERE batch_id = :batchId")
                .param("batchId", batchId)
                .query(Integer.class)
                .single();
    }

    public void updateTask(TaskRow task) {
        jdbc.sql("""
                        UPDATE homework_tasks
                        SET subject = :subject, title = :title, task_type = :taskType, icon = :icon,
                            estimated_minutes = :minutes, base_estimated_minutes = :baseMinutes,
                            estimate_source = :estimateSource,
                            estimate_sample_size = :sampleSize, estimate_confidence = :estimateConfidence,
                            estimate_reason = :estimateReason, difficulty = :difficulty, eye_load = :eyeLoad,
                            confidence = :confidence, manually_edited = :manuallyEdited
                        WHERE id = :id
                        """)
                .param("subject", task.subject())
                .param("title", task.title())
                .param("taskType", task.taskType())
                .param("icon", task.icon())
                .param("minutes", task.estimatedMinutes())
                .param("baseMinutes", task.baseEstimatedMinutes())
                .param("estimateSource", task.estimateSource())
                .param("sampleSize", task.estimateSampleSize())
                .param("estimateConfidence", task.estimateConfidence())
                .param("estimateReason", task.estimateReason())
                .param("difficulty", task.difficulty())
                .param("eyeLoad", task.eyeLoad())
                .param("confidence", task.confidence())
                .param("manuallyEdited", task.manuallyEdited())
                .param("id", task.id())
                .update();
    }

    public void deleteTask(String taskId) {
        int changed = jdbc.sql("""
                        DELETE t FROM homework_tasks t
                        JOIN homework_batches b ON b.id = t.batch_id
                        WHERE t.id = :id AND b.status <> 'CONFIRMED'
                        """)
                .param("id", taskId)
                .update();
        if (changed == 0) {
            throw new IllegalArgumentException("未找到可删除的作业项：" + taskId);
        }
    }

    public void markBatchPendingConfirmation(String batchId) {
        jdbc.sql("""
                        UPDATE homework_batches
                        SET status = 'PENDING_CONFIRMATION', recognition_error = NULL
                        WHERE id = :id AND status <> 'CONFIRMED'
                        """)
                .param("id", batchId)
                .update();
    }

    public void confirmBatch(String batchId) {
        int changed = jdbc.sql("UPDATE homework_batches SET status = 'CONFIRMED' WHERE id = :id")
                .param("id", batchId)
                .update();
        if (changed == 0) {
            throw new IllegalArgumentException("未找到作业批次：" + batchId);
        }
        jdbc.sql("UPDATE homework_tasks SET status = 'PLANNED' WHERE batch_id = :id")
                .param("id", batchId)
                .update();
    }

    public void replaceTodayPlan(String childId, String planId, LocalTime start, LocalTime end) {
        jdbc.sql("DELETE FROM plans WHERE child_id = :childId AND plan_date = :planDate")
                .param("childId", childId)
                .param("planDate", LocalDate.now())
                .update();
        jdbc.sql("""
                        INSERT INTO plans
                        (id, child_id, plan_date, start_time, original_end_time, planned_end_time,
                         bedtime_buffer_minutes, warning_message, status, version)
                        VALUES (:id, :childId, :planDate, :start, :end, :end, 45, :warning, 'READY', 1)
                        """)
                .param("id", planId)
                .param("childId", childId)
                .param("planDate", LocalDate.now())
                .param("start", start)
                .param("end", end)
                .param("warning", scheduleWarning(requireChild(childId), end, 45))
                .update();
    }

    public void insertPlanItem(PlanItemRow item) {
        jdbc.sql("""
                        INSERT INTO plan_items
                        (id, plan_id, homework_task_id, kind, subject, title, task_type, icon,
                         estimated_minutes, sort_order, planned_start, planned_end, status, actual_seconds)
                        VALUES (:id, :planId, :taskId, :kind, :subject, :title, :taskType, :icon,
                                :minutes, :sortOrder, :plannedStart, :plannedEnd, :status, 0)
                        """)
                .param("id", item.id())
                .param("planId", item.planId())
                .param("taskId", item.homeworkTaskId())
                .param("kind", item.kind())
                .param("subject", item.subject())
                .param("title", item.title())
                .param("taskType", item.taskType())
                .param("icon", item.icon())
                .param("minutes", item.estimatedMinutes())
                .param("sortOrder", item.sortOrder())
                .param("plannedStart", item.plannedStart())
                .param("plannedEnd", item.plannedEnd())
                .param("status", item.status())
                .update();
    }

    public Optional<PlanView> findTodayPlan(String childId) {
        return jdbc.sql("""
                        SELECT id, child_id, plan_date, start_time, original_end_time, planned_end_time,
                               bedtime_buffer_minutes, warning_message, status, version
                        FROM plans WHERE child_id = :childId AND plan_date = :planDate
                        """)
                .param("childId", childId)
                .param("planDate", LocalDate.now())
                .query((rs, rowNum) -> new PlanView(
                        rs.getString("id"), rs.getString("child_id"), rs.getDate("plan_date").toLocalDate().toString(),
                        rs.getTime("start_time").toLocalTime().format(TIME),
                        rs.getTime("original_end_time").toLocalTime().format(TIME),
                        rs.getTime("planned_end_time").toLocalTime().format(TIME),
                        rs.getInt("bedtime_buffer_minutes"), rs.getString("warning_message"),
                        rs.getString("status"), rs.getInt("version"), findPlanItems(rs.getString("id"))))
                .optional();
    }

    public List<PlanItemView> findPlanItems(String planId) {
        return jdbc.sql("""
                        SELECT id, homework_task_id, kind, subject, title, task_type, icon, estimated_minutes,
                               sort_order, planned_start, planned_end, status, actual_seconds, started_at,
                               overrun_decision
                        FROM plan_items WHERE plan_id = :planId ORDER BY sort_order
                        """)
                .param("planId", planId)
                .query((rs, rowNum) -> new PlanItemView(
                        rs.getString("id"), rs.getString("homework_task_id"), rs.getString("kind"),
                        rs.getString("subject"), rs.getString("title"), rs.getString("task_type"),
                        rs.getString("icon"), rs.getInt("estimated_minutes"), rs.getInt("sort_order"),
                        rs.getTime("planned_start").toLocalTime().format(TIME),
                        rs.getTime("planned_end").toLocalTime().format(TIME),
                        rs.getString("status"), rs.getInt("actual_seconds"),
                        formatDateTime(rs.getTimestamp("started_at")),
                        rs.getString("overrun_decision")))
                .list();
    }

    public PlanItemRow requirePlanItemForUpdate(String itemId) {
        return jdbc.sql("""
                        SELECT id, plan_id, homework_task_id, kind, subject, title, task_type, icon,
                               estimated_minutes, sort_order, planned_start, planned_end, status, actual_seconds,
                               started_at, overrun_decision
                        FROM plan_items WHERE id = :id FOR UPDATE
                        """)
                .param("id", itemId)
                .query((rs, rowNum) -> new PlanItemRow(
                        rs.getString("id"), rs.getString("plan_id"), rs.getString("homework_task_id"),
                        rs.getString("kind"), rs.getString("subject"), rs.getString("title"),
                        rs.getString("task_type"), rs.getString("icon"), rs.getInt("estimated_minutes"),
                        rs.getInt("sort_order"), rs.getTime("planned_start").toLocalTime(),
                        rs.getTime("planned_end").toLocalTime(), rs.getString("status"),
                        rs.getInt("actual_seconds"),
                        rs.getTimestamp("started_at") == null
                                ? null
                                : rs.getTimestamp("started_at").toLocalDateTime(),
                        rs.getString("overrun_decision")))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("未找到计划项：" + itemId));
    }

    public String findChildIdByPlan(String planId) {
        return jdbc.sql("SELECT child_id FROM plans WHERE id = :id")
                .param("id", planId)
                .query(String.class)
                .single();
    }

    public void startPlanItem(String itemId) {
        jdbc.sql("""
                        UPDATE plan_items
                        SET status = 'ACTIVE', started_at = COALESCE(started_at, CURRENT_TIMESTAMP(6))
                        WHERE id = :id AND status = 'PENDING'
                        """)
                .param("id", itemId)
                .update();
    }

    public void completePlanItem(PlanItemRow item, int actualSeconds) {
        jdbc.sql("""
                        UPDATE plan_items
                        SET status = 'DONE', actual_seconds = :seconds, completed_at = CURRENT_TIMESTAMP(6)
                        WHERE id = :id
                        """)
                .param("seconds", actualSeconds)
                .param("id", item.id())
                .update();
        if (item.homeworkTaskId() != null) {
            jdbc.sql("UPDATE homework_tasks SET status = 'DONE', actual_seconds = :seconds WHERE id = :id")
                    .param("seconds", actualSeconds)
                    .param("id", item.homeworkTaskId())
                    .update();
        }
    }

    public void recordOverrunDecision(
            PlanItemRow item,
            String decision,
            String status,
            int actualSeconds,
            int estimatedMinutes) {
        jdbc.sql("""
                        UPDATE plan_items
                        SET status = :status,
                            actual_seconds = :actualSeconds,
                            estimated_minutes = :estimatedMinutes,
                            overrun_decision = :decision,
                            skipped_at = CASE WHEN :status = 'SKIPPED' THEN CURRENT_TIMESTAMP(6) ELSE skipped_at END
                        WHERE id = :id
                        """)
                .param("status", status)
                .param("actualSeconds", actualSeconds)
                .param("estimatedMinutes", estimatedMinutes)
                .param("decision", decision)
                .param("id", item.id())
                .update();
        if (item.homeworkTaskId() != null && "SKIPPED".equals(status)) {
            jdbc.sql("UPDATE homework_tasks SET status = 'SKIPPED', actual_seconds = :seconds WHERE id = :id")
                    .param("seconds", actualSeconds)
                    .param("id", item.homeworkTaskId())
                    .update();
        }
    }

    public PlanRow requirePlanForUpdate(String planId) {
        return jdbc.sql("""
                        SELECT id, child_id, plan_date, start_time, original_end_time, planned_end_time,
                               bedtime_buffer_minutes, status, version
                        FROM plans WHERE id = :id FOR UPDATE
                        """)
                .param("id", planId)
                .query((rs, rowNum) -> new PlanRow(
                        rs.getString("id"), rs.getString("child_id"), rs.getDate("plan_date").toLocalDate(),
                        rs.getTime("start_time").toLocalTime(),
                        rs.getTime("original_end_time").toLocalTime(),
                        rs.getTime("planned_end_time").toLocalTime(),
                        rs.getInt("bedtime_buffer_minutes"), rs.getString("status"), rs.getInt("version")))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("未找到计划：" + planId));
    }

    public List<PlanItemRow> findPlanItemRows(String planId) {
        return jdbc.sql("""
                        SELECT id, plan_id, homework_task_id, kind, subject, title, task_type, icon,
                               estimated_minutes, sort_order, planned_start, planned_end, status, actual_seconds,
                               started_at, overrun_decision
                        FROM plan_items WHERE plan_id = :planId ORDER BY sort_order
                        """)
                .param("planId", planId)
                .query(this::mapPlanItemRow)
                .list();
    }

    public void updatePlanItemSchedule(
            String itemId,
            int sortOrder,
            LocalTime plannedStart,
            LocalTime plannedEnd) {
        jdbc.sql("""
                        UPDATE plan_items
                        SET sort_order = :sortOrder, planned_start = :plannedStart, planned_end = :plannedEnd
                        WHERE id = :id
                        """)
                .param("sortOrder", sortOrder)
                .param("plannedStart", plannedStart)
                .param("plannedEnd", plannedEnd)
                .param("id", itemId)
                .update();
    }

    public void updatePlanSummary(
            String planId,
            LocalTime plannedEnd,
            String status,
            String warningMessage) {
        jdbc.sql("""
                        UPDATE plans
                        SET planned_end_time = :plannedEnd,
                            status = :status,
                            warning_message = :warning,
                            version = version + 1
                        WHERE id = :id
                        """)
                .param("plannedEnd", plannedEnd)
                .param("status", status)
                .param("warning", warningMessage)
                .param("id", planId)
                .update();
    }

    public List<HistorySample> findRecentExactHistory(
            String childId,
            String subject,
            String taskType,
            int limit) {
        return jdbc.sql("""
                        SELECT subject, task_type, base_estimated_minutes AS estimated_minutes, actual_seconds,
                               COALESCE(updated_at, created_at) AS completed_at
                        FROM homework_tasks
                        WHERE child_id = :childId
                          AND subject = :subject
                          AND task_type = :taskType
                          AND status = 'DONE'
                          AND actual_seconds > 0
                          AND created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 180 DAY)
                        ORDER BY completed_at DESC
                        LIMIT :limit
                        """)
                .param("childId", childId)
                .param("subject", subject)
                .param("taskType", taskType)
                .param("limit", limit)
                .query(this::mapHistorySample)
                .list();
    }

    public List<HistorySample> findRecentSubjectHistory(String childId, String subject, int limit) {
        return jdbc.sql("""
                        SELECT subject, task_type, base_estimated_minutes AS estimated_minutes, actual_seconds,
                               COALESCE(updated_at, created_at) AS completed_at
                        FROM homework_tasks
                        WHERE child_id = :childId
                          AND subject = :subject
                          AND status = 'DONE'
                          AND actual_seconds > 0
                          AND created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 180 DAY)
                        ORDER BY completed_at DESC
                        LIMIT :limit
                        """)
                .param("childId", childId)
                .param("subject", subject)
                .param("limit", limit)
                .query(this::mapHistorySample)
                .list();
    }

    public List<HistorySample> findRecentChildHistory(String childId, int limit) {
        return jdbc.sql("""
                        SELECT subject, task_type, base_estimated_minutes AS estimated_minutes, actual_seconds,
                               COALESCE(updated_at, created_at) AS completed_at
                        FROM homework_tasks
                        WHERE child_id = :childId
                          AND status = 'DONE'
                          AND actual_seconds > 0
                          AND created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 180 DAY)
                        ORDER BY completed_at DESC
                        LIMIT :limit
                        """)
                .param("childId", childId)
                .param("limit", limit)
                .query(this::mapHistorySample)
                .list();
    }

    public void insertStarTransaction(String childId, int amount, String reason, String relatedId) {
        jdbc.sql("""
                        INSERT INTO star_transactions (id, child_id, amount, reason, related_id)
                        VALUES (:id, :childId, :amount, :reason, :relatedId)
                        """)
                .param("id", java.util.UUID.randomUUID().toString())
                .param("childId", childId)
                .param("amount", amount)
                .param("reason", reason)
                .param("relatedId", relatedId)
                .update();
    }

    public void addStars(String childId, int stars) {
        jdbc.sql("UPDATE children SET stars = stars + :stars WHERE id = :id")
                .param("stars", stars)
                .param("id", childId)
                .update();
    }

    public void insertActivity(
            String id,
            String familyId,
            String childId,
            String actorId,
            String actorName,
            String actorRelation,
            String actionType,
            String description) {
        jdbc.sql("""
                        INSERT INTO activity_log
                        (id, family_id, child_id, actor_id, actor_name, actor_relation, action_type, description)
                        VALUES (:id, :familyId, :childId, :actorId, :actorName, :actorRelation, :actionType, :description)
                        """)
                .param("id", id)
                .param("familyId", familyId)
                .param("childId", childId)
                .param("actorId", actorId)
                .param("actorName", actorName)
                .param("actorRelation", actorRelation)
                .param("actionType", actionType)
                .param("description", description)
                .update();
    }

    public List<ActivityView> findActivities(String childId) {
        return jdbc.sql("""
                        SELECT id, actor_name, actor_relation, action_type, description, created_at
                        FROM activity_log WHERE child_id = :childId ORDER BY created_at DESC LIMIT 30
                        """)
                .param("childId", childId)
                .query((rs, rowNum) -> {
                    LocalDateTime createdAt = rs.getTimestamp("created_at").toLocalDateTime();
                    return new ActivityView(
                            rs.getString("id"), rs.getString("actor_name"), rs.getString("actor_relation"),
                            rs.getString("action_type"), rs.getString("description"), createdAt.format(DATE_TIME));
                })
                .list();
    }

    private TaskRow mapTask(ResultSet rs, int rowNum) throws SQLException {
        return new TaskRow(
                rs.getString("id"), rs.getString("batch_id"), rs.getString("child_id"),
                rs.getString("subject"), rs.getString("title"), rs.getString("task_type"),
                rs.getString("icon"), rs.getInt("estimated_minutes"), rs.getInt("base_estimated_minutes"),
                rs.getString("estimate_source"), rs.getInt("estimate_sample_size"),
                rs.getString("estimate_confidence"), rs.getString("estimate_reason"),
                rs.getString("difficulty"), rs.getString("eye_load"), rs.getString("confidence"),
                nullableDouble(rs, "ocr_confidence"), rs.getBoolean("manually_edited"), rs.getString("status"),
                rs.getInt("sort_order"));
    }

    private HistorySample mapHistorySample(ResultSet rs, int rowNum) throws SQLException {
        return new HistorySample(
                rs.getString("subject"), rs.getString("task_type"), rs.getInt("estimated_minutes"),
                rs.getInt("actual_seconds"), rs.getTimestamp("completed_at").toLocalDateTime());
    }

    private PlanItemRow mapPlanItemRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp startedAt = rs.getTimestamp("started_at");
        return new PlanItemRow(
                rs.getString("id"), rs.getString("plan_id"), rs.getString("homework_task_id"),
                rs.getString("kind"), rs.getString("subject"), rs.getString("title"),
                rs.getString("task_type"), rs.getString("icon"), rs.getInt("estimated_minutes"),
                rs.getInt("sort_order"), rs.getTime("planned_start").toLocalTime(),
                rs.getTime("planned_end").toLocalTime(), rs.getString("status"),
                rs.getInt("actual_seconds"), startedAt == null ? null : startedAt.toLocalDateTime(),
                rs.getString("overrun_decision"));
    }

    private RecognizedTaskView toTaskView(TaskRow task) {
        return new RecognizedTaskView(
                task.id(), task.subject(), task.title(), task.taskType(), task.icon(), task.estimatedMinutes(),
                task.baseEstimatedMinutes(), task.estimateSource(), task.estimateSampleSize(),
                task.estimateConfidence(), task.estimateReason(), task.difficulty(), task.eyeLoad(),
                task.confidence(), task.ocrConfidence(), task.manuallyEdited(), task.status());
    }

    public String scheduleWarning(ChildRow child, LocalTime plannedEnd, int bufferMinutes) {
        int minutesBeforeBed = (int) Duration.between(plannedEnd, child.bedtime()).toMinutes();
        if (minutesBeforeBed >= bufferMinutes) {
            return null;
        }
        if (minutesBeforeBed < 0) {
            return "预计 " + plannedEnd.format(TIME) + " 完成，已超过睡觉时间；建议提前开始或压缩非作业安排";
        }
        return "预计 " + plannedEnd.format(TIME) + " 完成，睡前仅剩 " + minutesBeforeBed
                + " 分钟；建议提前开始或压缩非作业安排";
    }

    private String formatDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime().format(DATE_TIME);
    }

    private Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
