package com.studytime.growth;

import com.studytime.api.ApiModels.RewardView;
import com.studytime.api.ApiModels.SubjectSummaryView;
import com.studytime.api.ApiModels.WeeklyCommentView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Repository
public class GrowthRepository {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcClient jdbc;

    public GrowthRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record WeeklyTotals(int totalTasks, int completedTasks, int focusedSeconds, int starsEarned) {
    }

    public record RedemptionRow(
            String id,
            String rewardId,
            String rewardName,
            int requiredStars,
            String childId,
            String familyId,
            String status) {
    }

    public WeeklyTotals weeklyTotals(String childId, LocalDate weekStart, LocalDate weekEndExclusive) {
        int[] taskTotals = jdbc.sql("""
                        SELECT COUNT(*) AS total_tasks,
                               SUM(CASE WHEN status = 'DONE' THEN 1 ELSE 0 END) AS completed_tasks,
                               COALESCE(SUM(CASE WHEN status = 'DONE' THEN actual_seconds ELSE 0 END), 0)
                                   AS focused_seconds
                        FROM homework_tasks
                        WHERE child_id = :childId
                          AND created_at >= :weekStart
                          AND created_at < :weekEnd
                        """)
                .param("childId", childId)
                .param("weekStart", weekStart)
                .param("weekEnd", weekEndExclusive)
                .query((rs, rowNum) -> new int[]{
                        rs.getInt("total_tasks"),
                        rs.getInt("completed_tasks"),
                        rs.getInt("focused_seconds")})
                .single();
        int stars = jdbc.sql("""
                        SELECT COALESCE(SUM(amount), 0)
                        FROM star_transactions
                        WHERE child_id = :childId
                          AND amount > 0
                          AND created_at >= :weekStart
                          AND created_at < :weekEnd
                        """)
                .param("childId", childId)
                .param("weekStart", weekStart)
                .param("weekEnd", weekEndExclusive)
                .query(Integer.class)
                .single();
        return new WeeklyTotals(taskTotals[0], taskTotals[1], taskTotals[2], stars);
    }

    public List<SubjectSummaryView> subjectSummaries(
            String childId,
            LocalDate weekStart,
            LocalDate weekEndExclusive) {
        return jdbc.sql("""
                        SELECT subject,
                               COUNT(*) AS completed_tasks,
                               ROUND(AVG(estimated_minutes)) AS average_estimated_minutes,
                               ROUND(AVG(actual_seconds) / 60.0) AS average_actual_minutes
                        FROM homework_tasks
                        WHERE child_id = :childId
                          AND status = 'DONE'
                          AND created_at >= :weekStart
                          AND created_at < :weekEnd
                        GROUP BY subject
                        ORDER BY completed_tasks DESC, subject
                        """)
                .param("childId", childId)
                .param("weekStart", weekStart)
                .param("weekEnd", weekEndExclusive)
                .query((rs, rowNum) -> new SubjectSummaryView(
                        rs.getString("subject"), rs.getInt("completed_tasks"),
                        rs.getInt("average_estimated_minutes"), rs.getInt("average_actual_minutes")))
                .list();
    }

    public int lifetimeCompletedTasks(String childId) {
        return jdbc.sql("""
                        SELECT COUNT(*) FROM homework_tasks
                        WHERE child_id = :childId AND status = 'DONE'
                        """)
                .param("childId", childId)
                .query(Integer.class)
                .single();
    }

    public List<LocalDate> completionDates(String childId) {
        return jdbc.sql("""
                        SELECT DISTINCT DATE(created_at) AS completion_date
                        FROM activity_log
                        WHERE child_id = :childId AND action_type = 'TASK_COMPLETED'
                        ORDER BY completion_date DESC
                        """)
                .param("childId", childId)
                .query((rs, rowNum) -> rs.getDate("completion_date").toLocalDate())
                .list();
    }

    public List<WeeklyCommentView> weeklyComments(String childId, LocalDate weekStart) {
        return jdbc.sql("""
                        SELECT id, actor_name, actor_relation, content, created_at
                        FROM weekly_comments
                        WHERE child_id = :childId AND week_start = :weekStart
                        ORDER BY created_at DESC
                        """)
                .param("childId", childId)
                .param("weekStart", weekStart)
                .query((rs, rowNum) -> {
                    LocalDateTime createdAt = rs.getTimestamp("created_at").toLocalDateTime();
                    return new WeeklyCommentView(
                            rs.getString("id"), rs.getString("actor_name"), rs.getString("actor_relation"),
                            rs.getString("content"), createdAt.format(DATE_TIME));
                })
                .list();
    }

    public void insertWeeklyComment(
            String familyId,
            String childId,
            LocalDate weekStart,
            String actorId,
            String actorName,
            String actorRelation,
            String content) {
        jdbc.sql("""
                        INSERT INTO weekly_comments
                        (id, family_id, child_id, week_start, actor_id, actor_name, actor_relation, content)
                        VALUES (:id, :familyId, :childId, :weekStart, :actorId, :actorName, :actorRelation, :content)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("familyId", familyId)
                .param("childId", childId)
                .param("weekStart", weekStart)
                .param("actorId", actorId)
                .param("actorName", actorName)
                .param("actorRelation", actorRelation)
                .param("content", content)
                .update();
    }

    public List<RewardView> rewards(String familyId, String childId, int childStars) {
        return jdbc.sql("""
                        SELECT reward.id, reward.name, reward.icon, reward.required_stars,
                               reward.category, reward.source_type, creator.display_name AS created_by_name,
                               redemption.id AS redemption_id, redemption.status AS redemption_status
                        FROM reward_definitions reward
                        JOIN members creator ON creator.id = reward.created_by
                        LEFT JOIN reward_redemptions redemption
                          ON redemption.id = (
                              SELECT latest.id
                              FROM reward_redemptions latest
                              WHERE latest.reward_id = reward.id AND latest.child_id = :childId
                              ORDER BY latest.requested_at DESC
                              LIMIT 1
                          )
                        WHERE reward.family_id = :familyId AND reward.active = TRUE
                        ORDER BY reward.required_stars, reward.created_at
                        """)
                .param("childId", childId)
                .param("familyId", familyId)
                .query((rs, rowNum) -> {
                    String redemptionStatus = rs.getString("redemption_status");
                    boolean pending = "REQUESTED".equals(redemptionStatus);
                    return new RewardView(
                            rs.getString("id"), rs.getString("name"), rs.getString("icon"),
                            rs.getInt("required_stars"), rs.getString("category"),
                            rs.getString("source_type"), rs.getString("created_by_name"),
                            childStars >= rs.getInt("required_stars") && !pending,
                            rs.getString("redemption_id"), redemptionStatus);
                })
                .list();
    }

    public void insertReward(
            String id,
            String familyId,
            String name,
            String icon,
            int requiredStars,
            String category,
            String actorId) {
        jdbc.sql("""
                        INSERT INTO reward_definitions
                        (id, family_id, name, icon, required_stars, category, source_type, created_by)
                        VALUES (:id, :familyId, :name, :icon, :requiredStars, :category, 'CUSTOM', :actorId)
                        """)
                .param("id", id)
                .param("familyId", familyId)
                .param("name", name)
                .param("icon", icon)
                .param("requiredStars", requiredStars)
                .param("category", category)
                .param("actorId", actorId)
                .update();
    }

    public void insertRedemption(String id, String rewardId, String childId, String actorId) {
        jdbc.sql("""
                        INSERT INTO reward_redemptions
                        (id, reward_id, child_id, status, requested_by)
                        VALUES (:id, :rewardId, :childId, 'REQUESTED', :actorId)
                        """)
                .param("id", id)
                .param("rewardId", rewardId)
                .param("childId", childId)
                .param("actorId", actorId)
                .update();
    }

    public boolean hasPendingRedemption(String rewardId, String childId) {
        return jdbc.sql("""
                        SELECT COUNT(*) FROM reward_redemptions
                        WHERE reward_id = :rewardId AND child_id = :childId AND status = 'REQUESTED'
                        """)
                .param("rewardId", rewardId)
                .param("childId", childId)
                .query(Integer.class)
                .single() > 0;
    }

    public RedemptionRow requireRedemptionForUpdate(String redemptionId) {
        return jdbc.sql("""
                        SELECT redemption.id, redemption.reward_id, reward.name AS reward_name,
                               reward.required_stars, redemption.child_id, reward.family_id, redemption.status
                        FROM reward_redemptions redemption
                        JOIN reward_definitions reward ON reward.id = redemption.reward_id
                        WHERE redemption.id = :id
                        FOR UPDATE
                        """)
                .param("id", redemptionId)
                .query((rs, rowNum) -> new RedemptionRow(
                        rs.getString("id"), rs.getString("reward_id"), rs.getString("reward_name"),
                        rs.getInt("required_stars"), rs.getString("child_id"),
                        rs.getString("family_id"), rs.getString("status")))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("未找到奖励申请：" + redemptionId));
    }

    public void reviewRedemption(String redemptionId, boolean approved, String reviewerId) {
        jdbc.sql("""
                        UPDATE reward_redemptions
                        SET status = :status, reviewed_by = :reviewerId, reviewed_at = CURRENT_TIMESTAMP(6)
                        WHERE id = :id
                        """)
                .param("status", approved ? "APPROVED" : "REJECTED")
                .param("reviewerId", reviewerId)
                .param("id", redemptionId)
                .update();
    }

    public boolean deductStars(String childId, int stars) {
        return jdbc.sql("""
                        UPDATE children SET stars = stars - :stars
                        WHERE id = :childId AND stars >= :stars
                        """)
                .param("stars", stars)
                .param("childId", childId)
                .update() == 1;
    }
}
