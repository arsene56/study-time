package com.studytime.growth;

import com.studytime.api.ApiModels.AddWeeklyCommentRequest;
import com.studytime.api.ApiModels.BadgeView;
import com.studytime.api.ApiModels.CreateRewardRequest;
import com.studytime.api.ApiModels.RedeemRewardRequest;
import com.studytime.api.ApiModels.ReviewRewardRequest;
import com.studytime.api.ApiModels.RewardStoreView;
import com.studytime.api.ApiModels.RewardView;
import com.studytime.api.ApiModels.WeeklyReportView;
import com.studytime.domain.StudyTimeRepository;
import com.studytime.domain.StudyTimeRepository.ChildRow;
import com.studytime.growth.GrowthRepository.RedemptionRow;
import com.studytime.growth.GrowthRepository.WeeklyTotals;
import com.studytime.realtime.PlanUpdateWebSocketHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class GrowthService {
    private final GrowthRepository growthRepository;
    private final StudyTimeRepository studyRepository;
    private final PlanUpdateWebSocketHandler updates;

    public GrowthService(
            GrowthRepository growthRepository,
            StudyTimeRepository studyRepository,
            PlanUpdateWebSocketHandler updates) {
        this.growthRepository = growthRepository;
        this.studyRepository = studyRepository;
        this.updates = updates;
    }

    public WeeklyReportView weeklyReport(String childId) {
        ChildRow child = studyRepository.requireChild(childId);
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEndExclusive = weekStart.plusDays(7);
        WeeklyTotals totals = growthRepository.weeklyTotals(child.id(), weekStart, weekEndExclusive);
        int completionRate = totals.totalTasks() == 0
                ? 0
                : Math.round(totals.completedTasks() * 100f / totals.totalTasks());
        int lifetimeCompleted = growthRepository.lifetimeCompletedTasks(child.id());
        int streak = streakDays(growthRepository.completionDates(child.id()));
        return new WeeklyReportView(
                weekStart.toString(), weekEndExclusive.minusDays(1).toString(),
                totals.completedTasks(), totals.totalTasks(), completionRate,
                Math.round(totals.focusedSeconds() / 60f), totals.starsEarned(), streak,
                growthRepository.subjectSummaries(child.id(), weekStart, weekEndExclusive),
                badges(lifetimeCompleted, streak),
                growthRepository.weeklyComments(child.id(), weekStart));
    }

    @Transactional
    public WeeklyReportView addComment(String childId, AddWeeklyCommentRequest request) {
        ChildRow child = studyRepository.requireChild(childId);
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Actor actor = actor(child, request.actorId(), request.actorName(), request.actorRelation());
        growthRepository.insertWeeklyComment(
                child.familyId(), child.id(), weekStart, actor.id(), actor.name(), actor.relation(),
                request.content().trim());
        studyRepository.insertActivity(
                UUID.randomUUID().toString(), child.familyId(), child.id(), actor.id(), actor.name(), actor.relation(),
                "WEEKLY_COMMENT_ADDED", "留下了本周鼓励：“" + request.content().trim() + "”");
        updates.publish(child.id(), "WEEKLY_REPORT_UPDATED");
        return weeklyReport(child.id());
    }

    public RewardStoreView rewards(String familyId, String childId) {
        ChildRow child = studyRepository.requireChild(childId);
        if (!familyId.equals(child.familyId())) {
            throw new IllegalArgumentException("孩子不属于当前家庭");
        }
        return new RewardStoreView(child.stars(), growthRepository.rewards(familyId, childId, child.stars()));
    }

    @Transactional
    public RewardStoreView createReward(String familyId, String childId, CreateRewardRequest request) {
        ChildRow child = studyRepository.requireChild(childId);
        if (!familyId.equals(child.familyId())) {
            throw new IllegalArgumentException("孩子不属于当前家庭");
        }
        Actor actor = actor(child, request.actorId(), request.actorName(), request.actorRelation());
        growthRepository.insertReward(
                UUID.randomUUID().toString(), familyId, request.name().trim(), request.icon(),
                request.requiredStars(), request.category().toUpperCase(), actor.id());
        studyRepository.insertActivity(
                UUID.randomUUID().toString(), familyId, childId, actor.id(), actor.name(), actor.relation(),
                "REWARD_CREATED", "设置了新奖励“" + request.name().trim() + "”，需要 "
                        + request.requiredStars() + " 颗星");
        updates.publish(childId, "REWARD_UPDATED");
        return rewards(familyId, childId);
    }

    @Transactional
    public RewardStoreView redeem(String rewardId, RedeemRewardRequest request) {
        ChildRow child = studyRepository.requireChild(request.childId());
        List<RewardView> available = growthRepository.rewards(child.familyId(), child.id(), child.stars());
        RewardView reward = available.stream()
                .filter(item -> item.id().equals(rewardId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到奖励：" + rewardId));
        if (growthRepository.hasPendingRedemption(rewardId, child.id())) {
            throw new IllegalArgumentException("这个奖励已经在等待家长审批");
        }
        if (child.stars() < reward.requiredStars()) {
            throw new IllegalArgumentException("还差 " + (reward.requiredStars() - child.stars()) + " 颗星");
        }
        Actor actor = actor(child, request.actorId(), request.actorName(), request.actorRelation());
        String redemptionId = UUID.randomUUID().toString();
        growthRepository.insertRedemption(redemptionId, rewardId, child.id(), actor.id());
        studyRepository.insertActivity(
                UUID.randomUUID().toString(), child.familyId(), child.id(), actor.id(), actor.name(), actor.relation(),
                "REWARD_REQUESTED", "发起了“" + reward.name() + "”奖励审批");
        updates.publish(child.id(), "REWARD_REQUESTED");
        return rewards(child.familyId(), child.id());
    }

    @Transactional
    public RewardStoreView review(String redemptionId, ReviewRewardRequest request) {
        RedemptionRow redemption = growthRepository.requireRedemptionForUpdate(redemptionId);
        ChildRow child = studyRepository.requireChild(redemption.childId());
        if (!"REQUESTED".equals(redemption.status())) {
            return rewards(redemption.familyId(), redemption.childId());
        }
        boolean approved = request.approved();
        if (approved && !growthRepository.deductStars(redemption.childId(), redemption.requiredStars())) {
            throw new IllegalArgumentException("孩子当前星星不足，暂时无法批准");
        }
        String actorId = valueOr(request.actorId(), "demo-parent-mom");
        String actorName = valueOr(request.actorName(), "林妈妈");
        String relation = valueOr(request.actorRelation(), "妈妈");
        growthRepository.reviewRedemption(redemption.id(), approved, actorId);
        if (approved) {
            studyRepository.insertStarTransaction(
                    child.id(), -redemption.requiredStars(), "兑换“" + redemption.rewardName() + "”", redemption.id());
        }
        studyRepository.insertActivity(
                UUID.randomUUID().toString(), child.familyId(), child.id(), actorId, actorName, relation,
                approved ? "REWARD_APPROVED" : "REWARD_REJECTED",
                (approved ? "批准了" : "暂缓了") + "“" + redemption.rewardName() + "”奖励");
        updates.publish(child.id(), approved ? "REWARD_APPROVED" : "REWARD_REJECTED");
        return rewards(redemption.familyId(), redemption.childId());
    }

    private int streakDays(List<LocalDate> dates) {
        if (dates.isEmpty()) {
            return 0;
        }
        Set<LocalDate> completedDates = new HashSet<>(dates);
        LocalDate cursor = LocalDate.now();
        if (!completedDates.contains(cursor) && completedDates.contains(cursor.minusDays(1))) {
            cursor = cursor.minusDays(1);
        }
        int streak = 0;
        while (completedDates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private List<BadgeView> badges(int completedTasks, int streak) {
        return List.of(
                new BadgeView("first-step", "🌱", "自主第一步", "完成第 1 项自主打卡", completedTasks >= 1),
                new BadgeView("focus-five", "⭐", "专注小达人", "累计完成 5 项任务", completedTasks >= 5),
                new BadgeView("streak-three", "🔥", "三日连胜", "连续 3 天完成作业计划", streak >= 3));
    }

    private Actor actor(ChildRow child, String actorId, String actorName, String relation) {
        return new Actor(
                valueOr(actorId, child.id().replace("demo-child-", "demo-child-member-")),
                valueOr(actorName, child.name()),
                valueOr(relation, "孩子"));
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record Actor(String id, String name, String relation) {
    }
}
