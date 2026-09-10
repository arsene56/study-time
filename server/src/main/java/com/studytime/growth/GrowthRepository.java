package com.studytime.growth;

import com.studytime.api.ApiModels.DailyProgressView;
import com.studytime.api.ApiModels.RewardRedemptionView;
import com.studytime.api.ApiModels.RewardView;
import com.studytime.api.ApiModels.StarTransactionView;
import com.studytime.api.ApiModels.SubjectSummaryView;
import com.studytime.api.ApiModels.WeeklyCommentView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
            String studentId,
            String familyId,
            String status) {
    }

    public record WeeklyGoalRow(
            String id,
            String studentId,
            LocalDate weekStart,
            int targetTasks,
            int targetFocusMinutes,
            int bonusStars,
            String status,
            LocalDateTime claimedAt) {
    }

    public WeeklyTotals weeklyTotals(String studentId, LocalDate weekStart, LocalDate weekEndExclusive) {
        int[] taskTotals = jdbc.sql("""
                        SELECT COUNT(*) AS total_tasks,
                               SUM(CASE WHEN item.status = 'DONE' THEN 1 ELSE 0 END) AS completed_tasks,
                               COALESCE(SUM(CASE WHEN item.status = 'DONE' THEN item.actual_seconds ELSE 0 END), 0)
                                   AS focused_seconds
                        FROM plan_items item
                        JOIN plans plan ON plan.id = item.plan_id
                        WHERE plan.student_id = :studentId
                          AND plan.plan_date >= :weekStart
                          AND plan.plan_date < :weekEnd
                          AND item.kind = 'HOMEWORK'
                        """)
                .param("studentId", studentId)
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
                        WHERE student_id = :studentId
                          AND amount > 0
                          AND created_at >= :weekStart
                          AND created_at < :weekEnd
                        """)
                .param("studentId", studentId)
                .param("weekStart", weekStart)
                .param("weekEnd", weekEndExclusive)
                .query(Integer.class)
                .single();
        return new WeeklyTotals(taskTotals[0], taskTotals[1], taskTotals[2], stars);
    }

    public List<SubjectSummaryView> subjectSummaries(
            String studentId,
            LocalDate weekStart,
            LocalDate weekEndExclusive) {
        return jdbc.sql("""
                        SELECT subject,
                               COUNT(*) AS completed_tasks,
                               ROUND(AVG(estimated_minutes)) AS average_estimated_minutes,
                               ROUND(AVG(actual_seconds) / 60.0) AS average_actual_minutes
                        FROM plan_items item
                        JOIN plans plan ON plan.id = item.plan_id
                        WHERE plan.student_id = :studentId
                          AND item.kind = 'HOMEWORK'
                          AND item.status = 'DONE'
                          AND plan.plan_date >= :weekStart
                          AND plan.plan_date < :weekEnd
                        GROUP BY subject
                        ORDER BY completed_tasks DESC, subject
                        """)
                .param("studentId", studentId)
                .param("weekStart", weekStart)
                .param("weekEnd", weekEndExclusive)
                .query((rs, rowNum) -> new SubjectSummaryView(
                        rs.getString("subject"), rs.getInt("completed_tasks"),
                        rs.getInt("average_estimated_minutes"), rs.getInt("average_actual_minutes")))
                .list();
    }

    public List<DailyProgressView> dailyProgress(
            String studentId,
            LocalDate weekStart,
            LocalDate weekEndExclusive) {
        Map<LocalDate, int[]> totals = jdbc.sql("""
                        SELECT plan.plan_date,
                               COUNT(*) AS total_tasks,
                               SUM(CASE WHEN item.status = 'DONE' THEN 1 ELSE 0 END) AS completed_tasks,
                               COALESCE(SUM(CASE WHEN item.status = 'DONE' THEN item.actual_seconds ELSE 0 END), 0)
                                   AS focused_seconds
                        FROM plans plan
                        JOIN plan_items item ON item.plan_id = plan.id AND item.kind = 'HOMEWORK'
                        WHERE plan.student_id = :studentId
                          AND plan.plan_date >= :weekStart
                          AND plan.plan_date < :weekEnd
                        GROUP BY plan.plan_date
                        """)
                .param("studentId", studentId)
                .param("weekStart", weekStart)
                .param("weekEnd", weekEndExclusive)
                .query((rs, rowNum) -> Map.entry(
                        rs.getDate("plan_date").toLocalDate(),
                        new int[]{rs.getInt("total_tasks"), rs.getInt("completed_tasks"),
                                rs.getInt("focused_seconds")}))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        String[] labels = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        return weekStart.datesUntil(weekEndExclusive)
                .map(date -> {
                    int[] day = totals.getOrDefault(date, new int[]{0, 0, 0});
                    int rate = day[0] == 0 ? 0 : Math.round(day[1] * 100f / day[0]);
                    return new DailyProgressView(date.toString(), labels[date.getDayOfWeek().getValue() - 1],
                            day[1], day[0], rate, Math.round(day[2] / 60f));
                })
                .toList();
    }

    public int lifetimeCompletedTasks(String studentId) {
        return jdbc.sql("""
                        SELECT COUNT(*) FROM homework_tasks
                        WHERE student_id = :studentId AND status = 'DONE'
                        """)
                .param("studentId", studentId)
                .query(Integer.class)
                .single();
    }

    public List<LocalDate> completionDates(String studentId) {
        return jdbc.sql("""
                        SELECT plan.plan_date AS completion_date
                        FROM plans plan
                        JOIN plan_items item ON item.plan_id = plan.id AND item.kind = 'HOMEWORK'
                        WHERE plan.student_id = :studentId AND plan.plan_date <= CURRENT_DATE
                        GROUP BY plan.id, plan.plan_date
                        HAVING COUNT(*) > 0
                           AND SUM(CASE WHEN item.status = 'DONE' THEN 1 ELSE 0 END) = COUNT(*)
                        ORDER BY plan.plan_date DESC
                        """)
                .param("studentId", studentId)
                .query((rs, rowNum) -> rs.getDate("completion_date").toLocalDate())
                .list();
    }

    public Map<String, LocalDateTime> unlockedBadges(String studentId) {
        return jdbc.sql("""
                        SELECT badge_code, unlocked_at
                        FROM student_badges
                        WHERE student_id = :studentId
                        """)
                .param("studentId", studentId)
                .query((rs, rowNum) -> Map.entry(
                        rs.getString("badge_code"), rs.getTimestamp("unlocked_at").toLocalDateTime()))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public boolean unlockBadge(String studentId, String badgeCode) {
        return jdbc.sql("""
                        INSERT IGNORE INTO student_badges (id, student_id, badge_code)
                        VALUES (:id, :studentId, :badgeCode)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("studentId", studentId)
                .param("badgeCode", badgeCode)
                .update() == 1;
    }

    public Optional<WeeklyGoalRow> weeklyGoal(String studentId, LocalDate weekStart) {
        return jdbc.sql("""
                        SELECT id, student_id, week_start, target_tasks, target_focus_minutes,
                               bonus_stars, status, claimed_at
                        FROM weekly_goals
                        WHERE student_id = :studentId AND week_start = :weekStart
                        """)
                .param("studentId", studentId)
                .param("weekStart", weekStart)
                .query((rs, rowNum) -> new WeeklyGoalRow(
                        rs.getString("id"), rs.getString("student_id"),
                        rs.getDate("week_start").toLocalDate(), rs.getInt("target_tasks"),
                        rs.getInt("target_focus_minutes"), rs.getInt("bonus_stars"),
                        rs.getString("status"), rs.getTimestamp("claimed_at") == null
                                ? null : rs.getTimestamp("claimed_at").toLocalDateTime()))
                .optional();
    }

    public WeeklyGoalRow requireWeeklyGoalForUpdate(String studentId, LocalDate weekStart) {
        return jdbc.sql("""
                        SELECT id, student_id, week_start, target_tasks, target_focus_minutes,
                               bonus_stars, status, claimed_at
                        FROM weekly_goals
                        WHERE student_id = :studentId AND week_start = :weekStart
                        FOR UPDATE
                        """)
                .param("studentId", studentId)
                .param("weekStart", weekStart)
                .query((rs, rowNum) -> new WeeklyGoalRow(
                        rs.getString("id"), rs.getString("student_id"),
                        rs.getDate("week_start").toLocalDate(), rs.getInt("target_tasks"),
                        rs.getInt("target_focus_minutes"), rs.getInt("bonus_stars"),
                        rs.getString("status"), rs.getTimestamp("claimed_at") == null
                                ? null : rs.getTimestamp("claimed_at").toLocalDateTime()))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("本周还没有设置成长目标"));
    }

    public void upsertWeeklyGoal(
            String id,
            String familyId,
            String studentId,
            LocalDate weekStart,
            int targetTasks,
            int targetFocusMinutes,
            int bonusStars,
            String actorId) {
        jdbc.sql("""
                        INSERT INTO weekly_goals
                            (id, family_id, student_id, week_start, target_tasks, target_focus_minutes,
                             bonus_stars, status, created_by)
                        VALUES (:id, :familyId, :studentId, :weekStart, :targetTasks, :targetFocusMinutes,
                                :bonusStars, 'ACTIVE', :actorId)
                        ON DUPLICATE KEY UPDATE
                            target_tasks = VALUES(target_tasks),
                            target_focus_minutes = VALUES(target_focus_minutes),
                            bonus_stars = VALUES(bonus_stars),
                            updated_at = CURRENT_TIMESTAMP(6)
                        """)
                .param("id", id)
                .param("familyId", familyId)
                .param("studentId", studentId)
                .param("weekStart", weekStart)
                .param("targetTasks", targetTasks)
                .param("targetFocusMinutes", targetFocusMinutes)
                .param("bonusStars", bonusStars)
                .param("actorId", actorId)
                .update();
    }

    public void claimWeeklyGoal(String goalId, String actorId) {
        jdbc.sql("""
                        UPDATE weekly_goals
                        SET status = 'CLAIMED', claimed_by = :actorId, claimed_at = CURRENT_TIMESTAMP(6)
                        WHERE id = :id AND status = 'ACTIVE'
                        """)
                .param("actorId", actorId)
                .param("id", goalId)
                .update();
    }

    public List<WeeklyCommentView> weeklyComments(String studentId, LocalDate weekStart) {
        return jdbc.sql("""
                        SELECT id, actor_name, actor_relation, content, created_at
                        FROM weekly_comments
                        WHERE student_id = :studentId AND week_start = :weekStart
                        ORDER BY created_at DESC
                        """)
                .param("studentId", studentId)
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
            String studentId,
            LocalDate weekStart,
            String actorId,
            String actorName,
            String actorRelation,
            String content) {
        jdbc.sql("""
                        INSERT INTO weekly_comments
                        (id, family_id, student_id, week_start, actor_id, actor_name, actor_relation, content)
                        VALUES (:id, :familyId, :studentId, :weekStart, :actorId, :actorName, :actorRelation, :content)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("familyId", familyId)
                .param("studentId", studentId)
                .param("weekStart", weekStart)
                .param("actorId", actorId)
                .param("actorName", actorName)
                .param("actorRelation", actorRelation)
                .param("content", content)
                .update();
    }

    public List<RewardView> rewards(String familyId, String studentId, int studentStars) {
        return jdbc.sql("""
                        SELECT reward.id, reward.name, reward.icon, reward.required_stars,
                               reward.category, reward.source_type, creator.display_name AS created_by_name,
                               redemption.id AS redemption_id, redemption.status AS redemption_status,
                               EXISTS(
                                   SELECT 1 FROM reward_redemptions owned_redemption
                                   WHERE owned_redemption.reward_id = reward.id
                                     AND owned_redemption.student_id = :studentId
                                     AND owned_redemption.status = 'APPROVED'
                               ) AS owned,
                               student.equipped_skin_reward_id = reward.id AS equipped
                        FROM reward_definitions reward
                        JOIN members creator ON creator.id = reward.created_by
                        JOIN students student ON student.id = :studentId
                        LEFT JOIN reward_redemptions redemption
                          ON redemption.id = (
                              SELECT latest.id
                              FROM reward_redemptions latest
                              WHERE latest.reward_id = reward.id AND latest.student_id = :studentId
                              ORDER BY latest.requested_at DESC
                              LIMIT 1
                          )
                        WHERE reward.family_id = :familyId AND reward.active = TRUE
                        ORDER BY reward.required_stars, reward.created_at
                        """)
                .param("studentId", studentId)
                .param("familyId", familyId)
                .query((rs, rowNum) -> {
                    String redemptionStatus = rs.getString("redemption_status");
                    boolean pending = "REQUESTED".equals(redemptionStatus);
                    boolean owned = rs.getBoolean("owned");
                    boolean skinOwned = "SKIN".equals(rs.getString("category")) && owned;
                    return new RewardView(
                            rs.getString("id"), rs.getString("name"), rs.getString("icon"),
                            rs.getInt("required_stars"), rs.getString("category"),
                            rs.getString("source_type"), rs.getString("created_by_name"),
                            studentStars >= rs.getInt("required_stars") && !pending && !skinOwned,
                            owned, rs.getBoolean("equipped"),
                            rs.getString("redemption_id"), redemptionStatus);
                })
                .list();
    }

    public List<RewardRedemptionView> redemptions(String studentId) {
        return jdbc.sql("""
                        SELECT redemption.id, reward.id AS reward_id, reward.name, reward.icon,
                               reward.required_stars, redemption.status,
                               requester.display_name AS requested_by_name,
                               reviewer.display_name AS reviewed_by_name,
                               redemption.requested_at, redemption.reviewed_at
                        FROM reward_redemptions redemption
                        JOIN reward_definitions reward ON reward.id = redemption.reward_id
                        JOIN members requester ON requester.id = redemption.requested_by
                        LEFT JOIN members reviewer ON reviewer.id = redemption.reviewed_by
                        WHERE redemption.student_id = :studentId
                        ORDER BY redemption.requested_at DESC
                        LIMIT 30
                        """)
                .param("studentId", studentId)
                .query((rs, rowNum) -> new RewardRedemptionView(
                        rs.getString("id"), rs.getString("reward_id"), rs.getString("name"),
                        rs.getString("icon"), rs.getInt("required_stars"), rs.getString("status"),
                        rs.getString("requested_by_name"), rs.getString("reviewed_by_name"),
                        rs.getTimestamp("requested_at").toLocalDateTime().format(DATE_TIME),
                        rs.getTimestamp("reviewed_at") == null ? null
                                : rs.getTimestamp("reviewed_at").toLocalDateTime().format(DATE_TIME)))
                .list();
    }

    public List<StarTransactionView> starTransactions(String studentId) {
        return jdbc.sql("""
                        SELECT id, amount, reason, created_at
                        FROM star_transactions
                        WHERE student_id = :studentId
                        ORDER BY created_at DESC
                        LIMIT 30
                        """)
                .param("studentId", studentId)
                .query((rs, rowNum) -> new StarTransactionView(
                        rs.getString("id"), rs.getInt("amount"), rs.getString("reason"),
                        rs.getTimestamp("created_at").toLocalDateTime().format(DATE_TIME)))
                .list();
    }

    public String equippedSkinRewardId(String studentId) {
        return jdbc.sql("SELECT equipped_skin_reward_id FROM students WHERE id = :studentId")
                .param("studentId", studentId)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    public boolean ownsApprovedSkin(String studentId, String rewardId) {
        return jdbc.sql("""
                        SELECT COUNT(*)
                        FROM reward_redemptions redemption
                        JOIN reward_definitions reward ON reward.id = redemption.reward_id
                        WHERE redemption.student_id = :studentId
                          AND reward.id = :rewardId
                          AND reward.category = 'SKIN'
                          AND redemption.status = 'APPROVED'
                        """)
                .param("studentId", studentId)
                .param("rewardId", rewardId)
                .query(Integer.class)
                .single() > 0;
    }

    public void equipSkin(String studentId, String rewardId) {
        jdbc.sql("UPDATE students SET equipped_skin_reward_id = :rewardId WHERE id = :studentId")
                .param("rewardId", rewardId)
                .param("studentId", studentId)
                .update();
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

    public void insertRedemption(String id, String rewardId, String studentId, String actorId) {
        jdbc.sql("""
                        INSERT INTO reward_redemptions
                        (id, reward_id, student_id, status, requested_by)
                        VALUES (:id, :rewardId, :studentId, 'REQUESTED', :actorId)
                        """)
                .param("id", id)
                .param("rewardId", rewardId)
                .param("studentId", studentId)
                .param("actorId", actorId)
                .update();
    }

    public boolean hasPendingRedemption(String rewardId, String studentId) {
        return jdbc.sql("""
                        SELECT COUNT(*) FROM reward_redemptions
                        WHERE reward_id = :rewardId AND student_id = :studentId AND status = 'REQUESTED'
                        """)
                .param("rewardId", rewardId)
                .param("studentId", studentId)
                .query(Integer.class)
                .single() > 0;
    }

    public RedemptionRow requireRedemptionForUpdate(String redemptionId) {
        return jdbc.sql("""
                        SELECT redemption.id, redemption.reward_id, reward.name AS reward_name,
                               reward.required_stars, redemption.student_id, reward.family_id, redemption.status
                        FROM reward_redemptions redemption
                        JOIN reward_definitions reward ON reward.id = redemption.reward_id
                        WHERE redemption.id = :id
                        FOR UPDATE
                        """)
                .param("id", redemptionId)
                .query((rs, rowNum) -> new RedemptionRow(
                        rs.getString("id"), rs.getString("reward_id"), rs.getString("reward_name"),
                        rs.getInt("required_stars"), rs.getString("student_id"),
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

    public boolean deductStars(String studentId, int stars) {
        return jdbc.sql("""
                        UPDATE students SET stars = stars - :stars
                        WHERE id = :studentId AND stars >= :stars
                        """)
                .param("stars", stars)
                .param("studentId", studentId)
                .update() == 1;
    }
}
