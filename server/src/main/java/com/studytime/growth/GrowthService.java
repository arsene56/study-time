package com.studytime.growth;

import com.studytime.api.ApiModels.AddWeeklyCommentRequest;
import com.studytime.api.ApiModels.BadgeView;
import com.studytime.api.ApiModels.ClaimWeeklyBonusRequest;
import com.studytime.api.ApiModels.CreateRewardRequest;
import com.studytime.api.ApiModels.EquipSkinRequest;
import com.studytime.api.ApiModels.RedeemRewardRequest;
import com.studytime.api.ApiModels.ReviewRewardRequest;
import com.studytime.api.ApiModels.RewardStoreView;
import com.studytime.api.ApiModels.RewardView;
import com.studytime.api.ApiModels.SaveWeeklyGoalRequest;
import com.studytime.api.ApiModels.WeeklyComparisonView;
import com.studytime.api.ApiModels.WeeklyGoalView;
import com.studytime.api.ApiModels.WeeklyReportView;
import com.studytime.domain.StudyTimeRepository;
import com.studytime.domain.StudyTimeRepository.ChildRow;
import com.studytime.growth.GrowthRepository.RedemptionRow;
import com.studytime.growth.GrowthRepository.WeeklyGoalRow;
import com.studytime.growth.GrowthRepository.WeeklyTotals;
import com.studytime.realtime.PlanUpdateWebSocketHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class GrowthService {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

    @Transactional
    public WeeklyReportView weeklyReport(String childId, LocalDate requestedWeekStart) {
        ChildRow child = studyRepository.requireChild(childId);
        LocalDate weekStart = normalizeWeek(requestedWeekStart);
        LocalDate weekEndExclusive = weekStart.plusDays(7);
        WeeklyTotals totals = growthRepository.weeklyTotals(child.id(), weekStart, weekEndExclusive);
        WeeklyTotals previous = growthRepository.weeklyTotals(child.id(), weekStart.minusWeeks(1), weekStart);
        int completionRate = completionRate(totals);
        int previousRate = completionRate(previous);
        int focusedMinutes = Math.round(totals.focusedSeconds() / 60f);
        int previousFocusedMinutes = Math.round(previous.focusedSeconds() / 60f);
        int lifetimeCompleted = growthRepository.lifetimeCompletedTasks(child.id());
        int streak = streakDays(growthRepository.completionDates(child.id()));
        List<BadgeView> badges = badges(child.id(), lifetimeCompleted, streak, completionRate, focusedMinutes);
        WeeklyGoalView goal = growthRepository.weeklyGoal(child.id(), weekStart)
                .map(item -> goalView(item, totals))
                .orElse(null);
        int rateChange = completionRate - previousRate;
        int focusChange = focusedMinutes - previousFocusedMinutes;
        return new WeeklyReportView(
                weekStart.toString(), weekEndExclusive.minusDays(1).toString(),
                totals.completedTasks(), totals.totalTasks(), completionRate,
                focusedMinutes, totals.starsEarned(), streak,
                growthMessage(totals.completedTasks(), completionRate, rateChange, streak),
                growthRepository.dailyProgress(child.id(), weekStart, weekEndExclusive),
                new WeeklyComparisonView(rateChange, focusChange, trendText(rateChange, focusChange)),
                goal,
                growthRepository.subjectSummaries(child.id(), weekStart, weekEndExclusive),
                badges,
                growthRepository.weeklyComments(child.id(), weekStart));
    }

    @Transactional
    public WeeklyReportView addComment(String childId, AddWeeklyCommentRequest request) {
        ChildRow child = studyRepository.requireChild(childId);
        LocalDate weekStart = normalizeWeek(parseDate(request.weekStart()));
        Actor actor = actor(child, request.actorId(), request.actorName(), request.actorRelation());
        growthRepository.insertWeeklyComment(
                child.familyId(), child.id(), weekStart, actor.id(), actor.name(), actor.relation(),
                request.content().trim());
        studyRepository.insertActivity(
                UUID.randomUUID().toString(), child.familyId(), child.id(), actor.id(), actor.name(), actor.relation(),
                "WEEKLY_COMMENT_ADDED", "留下了本周鼓励：“" + request.content().trim() + "”");
        updates.publish(child.id(), "WEEKLY_REPORT_UPDATED");
        return weeklyReport(child.id(), weekStart);
    }

    @Transactional
    public WeeklyReportView saveWeeklyGoal(String childId, SaveWeeklyGoalRequest request) {
        ChildRow child = studyRepository.requireChild(childId);
        LocalDate weekStart = normalizeWeek(null);
        growthRepository.weeklyGoal(child.id(), weekStart).ifPresent(existing -> {
            if ("CLAIMED".equals(existing.status())) {
                throw new IllegalArgumentException("本周奖励已结算，不能再修改目标");
            }
        });
        Actor actor = parentActor(request.actorId(), request.actorName(), request.actorRelation());
        growthRepository.upsertWeeklyGoal(
                UUID.randomUUID().toString(), child.familyId(), child.id(), weekStart,
                request.targetTasks(), request.targetFocusMinutes(), request.bonusStars(), actor.id());
        studyRepository.insertActivity(
                UUID.randomUUID().toString(), child.familyId(), child.id(), actor.id(), actor.name(), actor.relation(),
                "WEEKLY_GOAL_UPDATED", "设置了本周目标：完成 " + request.targetTasks()
                        + " 项作业、专注 " + request.targetFocusMinutes() + " 分钟，达成奖励 "
                        + request.bonusStars() + " 颗星");
        updates.publish(child.id(), "WEEKLY_GOAL_UPDATED");
        return weeklyReport(child.id(), weekStart);
    }

    @Transactional
    public WeeklyReportView claimWeeklyBonus(String childId, ClaimWeeklyBonusRequest request) {
        ChildRow child = studyRepository.requireChild(childId);
        LocalDate weekStart = normalizeWeek(null);
        WeeklyGoalRow goal = growthRepository.requireWeeklyGoalForUpdate(child.id(), weekStart);
        if ("CLAIMED".equals(goal.status())) {
            return weeklyReport(child.id(), weekStart);
        }
        WeeklyTotals totals = growthRepository.weeklyTotals(child.id(), weekStart, weekStart.plusDays(7));
        if (!goalAchieved(goal, totals)) {
            throw new IllegalArgumentException("还差一点就达成本周目标了，继续加油");
        }
        Actor actor = actor(child, request == null ? null : request.actorId(),
                request == null ? null : request.actorName(),
                request == null ? null : request.actorRelation());
        growthRepository.claimWeeklyGoal(goal.id(), actor.id());
        studyRepository.addStars(child.id(), goal.bonusStars());
        studyRepository.insertStarTransaction(
                child.id(), goal.bonusStars(), "完成本周成长目标", goal.id());
        studyRepository.insertActivity(
                UUID.randomUUID().toString(), child.familyId(), child.id(), actor.id(), actor.name(), actor.relation(),
                "WEEKLY_BONUS_CLAIMED", "领取了本周成长奖励 " + goal.bonusStars() + " 颗星");
        updates.publish(child.id(), "WEEKLY_BONUS_CLAIMED");
        return weeklyReport(child.id(), weekStart);
    }

    public RewardStoreView rewards(String familyId, String childId) {
        ChildRow child = studyRepository.requireChild(childId);
        if (!familyId.equals(child.familyId())) {
            throw new IllegalArgumentException("学生不属于当前家庭");
        }
        return new RewardStoreView(
                child.stars(), growthRepository.equippedSkinRewardId(childId),
                growthRepository.rewards(familyId, childId, child.stars()),
                growthRepository.redemptions(childId), growthRepository.starTransactions(childId));
    }

    @Transactional
    public RewardStoreView createReward(String familyId, String childId, CreateRewardRequest request) {
        ChildRow child = studyRepository.requireChild(childId);
        if (!familyId.equals(child.familyId())) {
            throw new IllegalArgumentException("学生不属于当前家庭");
        }
        Actor actor = actor(child, request.actorId(), request.actorName(), request.actorRelation());
        String category = request.category().toUpperCase();
        if (!Set.of("SKIN", "WISH").contains(category)) {
            throw new IllegalArgumentException("奖励类型只能是嘀嘀皮肤或家庭心愿");
        }
        growthRepository.insertReward(
                UUID.randomUUID().toString(), familyId, request.name().trim(), request.icon(),
                request.requiredStars(), category, actor.id());
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
        if (reward.owned() && "SKIN".equals(reward.category())) {
            throw new IllegalArgumentException("这款嘀嘀皮肤已经解锁");
        }
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
            throw new IllegalArgumentException("学生当前星星不足，暂时无法批准");
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

    @Transactional
    public RewardStoreView equipSkin(String rewardId, EquipSkinRequest request) {
        ChildRow child = studyRepository.requireChild(request.childId());
        if (!growthRepository.ownsApprovedSkin(child.id(), rewardId)) {
            throw new IllegalArgumentException("请先解锁这款嘀嘀皮肤");
        }
        RewardView skin = growthRepository.rewards(child.familyId(), child.id(), child.stars()).stream()
                .filter(item -> item.id().equals(rewardId) && "SKIN".equals(item.category()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到嘀嘀皮肤"));
        Actor actor = actor(child, request.actorId(), request.actorName(), request.actorRelation());
        growthRepository.equipSkin(child.id(), rewardId);
        studyRepository.insertActivity(
                UUID.randomUUID().toString(), child.familyId(), child.id(), actor.id(), actor.name(), actor.relation(),
                "DIDI_SKIN_EQUIPPED", "为嘀嘀换上了“" + skin.name() + "”");
        updates.publish(child.id(), "DIDI_SKIN_EQUIPPED");
        return rewards(child.familyId(), child.id());
    }

    private WeeklyGoalView goalView(WeeklyGoalRow goal, WeeklyTotals totals) {
        int focusedMinutes = Math.round(totals.focusedSeconds() / 60f);
        int taskProgress = percent(totals.completedTasks(), goal.targetTasks());
        int focusProgress = percent(focusedMinutes, goal.targetFocusMinutes());
        boolean achieved = goalAchieved(goal, totals);
        String status = "CLAIMED".equals(goal.status()) ? "CLAIMED" : achieved ? "READY" : "IN_PROGRESS";
        return new WeeklyGoalView(
                goal.id(), goal.targetTasks(), goal.targetFocusMinutes(), goal.bonusStars(),
                taskProgress, focusProgress, Math.min(taskProgress, focusProgress), achieved, status,
                goal.claimedAt() == null ? null : goal.claimedAt().format(DATE_TIME));
    }

    private boolean goalAchieved(WeeklyGoalRow goal, WeeklyTotals totals) {
        int focusedMinutes = Math.round(totals.focusedSeconds() / 60f);
        return totals.completedTasks() >= goal.targetTasks() && focusedMinutes >= goal.targetFocusMinutes();
    }

    private int completionRate(WeeklyTotals totals) {
        return totals.totalTasks() == 0 ? 0 : Math.round(totals.completedTasks() * 100f / totals.totalTasks());
    }

    private int percent(int current, int target) {
        return Math.min(100, Math.round(current * 100f / Math.max(1, target)));
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

    private List<BadgeView> badges(
            String childId,
            int completedTasks,
            int streak,
            int completionRate,
            int focusedMinutes) {
        List<BadgeCandidate> candidates = List.of(
                new BadgeCandidate("first-step", "🌱", "自主第一步", "完成第 1 项自主打卡", completedTasks, 1),
                new BadgeCandidate("focus-five", "⭐", "专注小达人", "累计完成 5 项作业", completedTasks, 5),
                new BadgeCandidate("streak-three", "🔥", "三日连胜", "连续 3 天完成全部作业", streak, 3),
                new BadgeCandidate("weekly-steady", "🎯", "稳稳达成", "本周作业完成率达到 90%", completionRate, 90),
                new BadgeCandidate("focus-120", "🚀", "专注能量站", "本周专注满 120 分钟", focusedMinutes, 120));
        candidates.stream()
                .filter(candidate -> candidate.progress() >= candidate.target())
                .forEach(candidate -> growthRepository.unlockBadge(childId, candidate.id()));
        Map<String, LocalDateTime> unlocked = growthRepository.unlockedBadges(childId);
        return candidates.stream().map(candidate -> new BadgeView(
                candidate.id(), candidate.icon(), candidate.title(), candidate.description(),
                Math.min(candidate.progress(), candidate.target()), candidate.target(),
                unlocked.containsKey(candidate.id()), unlocked.containsKey(candidate.id())
                        ? unlocked.get(candidate.id()).format(DATE_TIME) : null)).toList();
    }

    private String growthMessage(int completedTasks, int completionRate, int rateChange, int streak) {
        if (completedTasks == 0) {
            return "新的一周刚开始，先完成一个小任务，让嘀嘀陪你点亮第一颗星。";
        }
        if (completionRate >= 90) {
            return "这周的节奏非常稳，你已经能够像小队长一样管理自己的时间了！";
        }
        if (rateChange > 0) {
            return "比上周又前进了一步，保持现在的节奏，你会越来越会规划。";
        }
        if (streak >= 2) {
            return "连续坚持很不容易，嘀嘀已经记住你的每一次认真打卡。";
        }
        return "已经完成的每一项都在帮你建立自己的好节奏，继续加油！";
    }

    private String trendText(int rateChange, int focusChange) {
        if (rateChange > 0) {
            return "完成率比上周提高 " + rateChange + "%";
        }
        if (rateChange < 0) {
            return "本周任务节奏还有调整空间";
        }
        if (focusChange > 0) {
            return "专注时间比上周增加 " + focusChange + " 分钟";
        }
        return "正在积累本周的成长数据";
    }

    private LocalDate normalizeWeek(LocalDate requested) {
        LocalDate currentWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekStart = (requested == null ? currentWeek : requested)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (weekStart.isAfter(currentWeek)) {
            throw new IllegalArgumentException("不能查看未来的成长周报");
        }
        return weekStart;
    }

    private LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private Actor actor(ChildRow child, String actorId, String actorName, String relation) {
        return new Actor(
                valueOr(actorId, child.id().replace("demo-child-", "demo-child-member-")),
                valueOr(actorName, child.name()),
                valueOr(relation, "学生"));
    }

    private Actor parentActor(String actorId, String actorName, String relation) {
        return new Actor(
                valueOr(actorId, "demo-parent-mom"),
                valueOr(actorName, "林妈妈"),
                valueOr(relation, "妈妈"));
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record Actor(String id, String name, String relation) {
    }

    private record BadgeCandidate(
            String id,
            String icon,
            String title,
            String description,
            int progress,
            int target) {
    }
}
