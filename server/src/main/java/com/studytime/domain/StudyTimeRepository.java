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
            String difficulty,
            String eyeLoad,
            String confidence,
            String status,
            int sortOrder) {
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
            int actualSeconds) {
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

    public void insertBatch(String id, ChildRow child, String objectKey) {
        jdbc.sql("""
                        INSERT INTO homework_batches
                        (id, family_id, child_id, source_object_key, recognition_mode, status, created_by)
                        VALUES (:id, :familyId, :childId, :objectKey, 'MOCK_OCR', 'PENDING_CONFIRMATION', 'demo-parent-mom')
                        """)
                .param("id", id)
                .param("familyId", child.familyId())
                .param("childId", child.id())
                .param("objectKey", objectKey)
                .update();
    }

    public void insertTask(TaskRow task) {
        jdbc.sql("""
                        INSERT INTO homework_tasks
                        (id, batch_id, child_id, subject, title, task_type, icon, estimated_minutes,
                         difficulty, eye_load, confidence, status, sort_order)
                        VALUES (:id, :batchId, :childId, :subject, :title, :taskType, :icon, :minutes,
                                :difficulty, :eyeLoad, :confidence, :status, :sortOrder)
                        """)
                .param("id", task.id())
                .param("batchId", task.batchId())
                .param("childId", task.childId())
                .param("subject", task.subject())
                .param("title", task.title())
                .param("taskType", task.taskType())
                .param("icon", task.icon())
                .param("minutes", task.estimatedMinutes())
                .param("difficulty", task.difficulty())
                .param("eyeLoad", task.eyeLoad())
                .param("confidence", task.confidence())
                .param("status", task.status())
                .param("sortOrder", task.sortOrder())
                .update();
    }

    public List<TaskRow> findTasksByBatch(String batchId) {
        return jdbc.sql("""
                        SELECT id, batch_id, child_id, subject, title, task_type, icon, estimated_minutes,
                               difficulty, eye_load, confidence, status, sort_order
                        FROM homework_tasks WHERE batch_id = :batchId ORDER BY sort_order
                        """)
                .param("batchId", batchId)
                .query(this::mapTask)
                .list();
    }

    public HomeworkBatchView getBatch(String batchId) {
        return jdbc.sql("""
                        SELECT id, child_id, status, recognition_mode, source_object_key
                        FROM homework_batches WHERE id = :id
                        """)
                .param("id", batchId)
                .query((rs, rowNum) -> new HomeworkBatchView(
                        rs.getString("id"), rs.getString("child_id"), rs.getString("status"),
                        rs.getString("recognition_mode"), rs.getString("source_object_key"),
                        findTasksByBatch(batchId).stream().map(this::toTaskView).toList()))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("未找到作业批次：" + batchId));
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
                        (id, child_id, plan_date, start_time, original_end_time, planned_end_time, status, version)
                        VALUES (:id, :childId, :planDate, :start, :end, :end, 'READY', 1)
                        """)
                .param("id", planId)
                .param("childId", childId)
                .param("planDate", LocalDate.now())
                .param("start", start)
                .param("end", end)
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
                        SELECT id, child_id, plan_date, start_time, original_end_time, planned_end_time, status, version
                        FROM plans WHERE child_id = :childId AND plan_date = :planDate
                        """)
                .param("childId", childId)
                .param("planDate", LocalDate.now())
                .query((rs, rowNum) -> new PlanView(
                        rs.getString("id"), rs.getString("child_id"), rs.getDate("plan_date").toLocalDate().toString(),
                        rs.getTime("start_time").toLocalTime().format(TIME),
                        rs.getTime("original_end_time").toLocalTime().format(TIME),
                        rs.getTime("planned_end_time").toLocalTime().format(TIME),
                        rs.getString("status"), rs.getInt("version"), findPlanItems(rs.getString("id"))))
                .optional();
    }

    public List<PlanItemView> findPlanItems(String planId) {
        return jdbc.sql("""
                        SELECT id, homework_task_id, kind, subject, title, task_type, icon, estimated_minutes,
                               sort_order, planned_start, planned_end, status, actual_seconds
                        FROM plan_items WHERE plan_id = :planId ORDER BY sort_order
                        """)
                .param("planId", planId)
                .query((rs, rowNum) -> new PlanItemView(
                        rs.getString("id"), rs.getString("homework_task_id"), rs.getString("kind"),
                        rs.getString("subject"), rs.getString("title"), rs.getString("task_type"),
                        rs.getString("icon"), rs.getInt("estimated_minutes"), rs.getInt("sort_order"),
                        rs.getTime("planned_start").toLocalTime().format(TIME),
                        rs.getTime("planned_end").toLocalTime().format(TIME),
                        rs.getString("status"), rs.getInt("actual_seconds")))
                .list();
    }

    public PlanItemRow requirePlanItemForUpdate(String itemId) {
        return jdbc.sql("""
                        SELECT id, plan_id, homework_task_id, kind, subject, title, task_type, icon,
                               estimated_minutes, sort_order, planned_start, planned_end, status, actual_seconds
                        FROM plan_items WHERE id = :id FOR UPDATE
                        """)
                .param("id", itemId)
                .query((rs, rowNum) -> new PlanItemRow(
                        rs.getString("id"), rs.getString("plan_id"), rs.getString("homework_task_id"),
                        rs.getString("kind"), rs.getString("subject"), rs.getString("title"),
                        rs.getString("task_type"), rs.getString("icon"), rs.getInt("estimated_minutes"),
                        rs.getInt("sort_order"), rs.getTime("planned_start").toLocalTime(),
                        rs.getTime("planned_end").toLocalTime(), rs.getString("status"),
                        rs.getInt("actual_seconds")))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("未找到计划项：" + itemId));
    }

    public String findChildIdByPlan(String planId) {
        return jdbc.sql("SELECT child_id FROM plans WHERE id = :id")
                .param("id", planId)
                .query(String.class)
                .single();
    }

    public void completePlanItem(PlanItemRow item) {
        int actualSeconds = Math.max(item.actualSeconds(), item.estimatedMinutes() * 60);
        jdbc.sql("""
                        UPDATE plan_items SET status = 'DONE', actual_seconds = :seconds, completed_at = CURRENT_TIMESTAMP
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
                rs.getString("icon"), rs.getInt("estimated_minutes"), rs.getString("difficulty"),
                rs.getString("eye_load"), rs.getString("confidence"), rs.getString("status"),
                rs.getInt("sort_order"));
    }

    private RecognizedTaskView toTaskView(TaskRow task) {
        return new RecognizedTaskView(
                task.id(), task.subject(), task.title(), task.taskType(), task.icon(), task.estimatedMinutes(),
                task.difficulty(), task.eyeLoad(), task.confidence(), task.status());
    }
}
