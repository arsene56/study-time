package com.studytime.api;

import com.studytime.api.ApiModels.ActorRequest;
import com.studytime.api.ApiModels.ActivityView;
import com.studytime.api.ApiModels.AddWeeklyCommentRequest;
import com.studytime.api.ApiModels.CompletePlanItemRequest;
import com.studytime.api.ApiModels.ClaimWeeklyBonusRequest;
import com.studytime.api.ApiModels.CreatePlanRequest;
import com.studytime.api.ApiModels.CreateRewardRequest;
import com.studytime.api.ApiModels.DemoContextView;
import com.studytime.api.ApiModels.EquipSkinRequest;
import com.studytime.api.ApiModels.HomeworkBatchView;
import com.studytime.api.ApiModels.OverrunDecisionRequest;
import com.studytime.api.ApiModels.PlanView;
import com.studytime.api.ApiModels.RedeemRewardRequest;
import com.studytime.api.ApiModels.ReorderPlanRequest;
import com.studytime.api.ApiModels.ReviewRewardRequest;
import com.studytime.api.ApiModels.RewardStoreView;
import com.studytime.api.ApiModels.SaveWeeklyGoalRequest;
import com.studytime.api.ApiModels.WeeklyReportView;
import com.studytime.domain.StudyTimeRepository;
import com.studytime.growth.GrowthService;
import com.studytime.homework.HomeworkService;
import com.studytime.homework.PlanExecutionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalTime;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class StudyTimeController {
    private final HomeworkService homeworkService;
    private final PlanExecutionService executionService;
    private final GrowthService growthService;
    private final StudyTimeRepository repository;

    public StudyTimeController(
            HomeworkService homeworkService,
            PlanExecutionService executionService,
            GrowthService growthService,
            StudyTimeRepository repository) {
        this.homeworkService = homeworkService;
        this.executionService = executionService;
        this.growthService = growthService;
        this.repository = repository;
    }

    @GetMapping("/demo/context")
    public DemoContextView demoContext() {
        return homeworkService.demoContext();
    }

    @PostMapping("/homework-batches/mock-recognize")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public HomeworkBatchView mockRecognize(
            @RequestParam String childId,
            @RequestParam(required = false) MultipartFile file) {
        return homeworkService.mockRecognize(childId, file);
    }

    @PostMapping("/homework-batches/{batchId}/confirm-and-plan")
    public PlanView confirmAndPlan(
            @PathVariable String batchId,
            @Valid @RequestBody CreatePlanRequest request) {
        return homeworkService.confirmAndPlan(batchId, LocalTime.parse(request.startTime()));
    }

    @GetMapping("/children/{childId}/today-plan")
    public PlanView todayPlan(@PathVariable String childId) {
        return homeworkService.todayPlan(childId);
    }

    @GetMapping("/children/{childId}/activities")
    public List<ActivityView> activities(@PathVariable String childId) {
        repository.requireChild(childId);
        return repository.findActivities(childId);
    }

    @PostMapping("/plan-items/{itemId}/start")
    public PlanView startPlanItem(
            @PathVariable String itemId,
            @RequestBody(required = false) ActorRequest request) {
        return executionService.startItem(itemId, request);
    }

    @PostMapping("/plan-items/{itemId}/complete")
    public PlanView completePlanItem(
            @PathVariable String itemId,
            @Valid @RequestBody(required = false) CompletePlanItemRequest request) {
        return executionService.completeItem(itemId, request);
    }

    @PostMapping("/plan-items/{itemId}/overrun-decision")
    public PlanView overrunDecision(
            @PathVariable String itemId,
            @Valid @RequestBody OverrunDecisionRequest request) {
        return executionService.decideOverrun(itemId, request);
    }

    @PostMapping("/plans/{planId}/reorder")
    public PlanView reorderPlan(
            @PathVariable String planId,
            @Valid @RequestBody ReorderPlanRequest request) {
        return executionService.reorder(planId, request);
    }

    @GetMapping("/children/{childId}/weekly-report")
    public WeeklyReportView weeklyReport(
            @PathVariable String childId,
            @RequestParam(required = false) LocalDate weekStart) {
        return growthService.weeklyReport(childId, weekStart);
    }

    @PostMapping("/children/{childId}/weekly-comments")
    public WeeklyReportView addWeeklyComment(
            @PathVariable String childId,
            @Valid @RequestBody AddWeeklyCommentRequest request) {
        return growthService.addComment(childId, request);
    }

    @PostMapping("/children/{childId}/weekly-goal")
    public WeeklyReportView saveWeeklyGoal(
            @PathVariable String childId,
            @Valid @RequestBody SaveWeeklyGoalRequest request) {
        return growthService.saveWeeklyGoal(childId, request);
    }

    @PostMapping("/children/{childId}/weekly-bonus/claim")
    public WeeklyReportView claimWeeklyBonus(
            @PathVariable String childId,
            @RequestBody(required = false) ClaimWeeklyBonusRequest request) {
        return growthService.claimWeeklyBonus(childId, request);
    }

    @GetMapping("/families/{familyId}/rewards")
    public RewardStoreView rewards(
            @PathVariable String familyId,
            @RequestParam String childId) {
        return growthService.rewards(familyId, childId);
    }

    @PostMapping("/families/{familyId}/rewards")
    public RewardStoreView createReward(
            @PathVariable String familyId,
            @RequestParam String childId,
            @Valid @RequestBody CreateRewardRequest request) {
        return growthService.createReward(familyId, childId, request);
    }

    @PostMapping("/rewards/{rewardId}/redeem")
    public RewardStoreView redeemReward(
            @PathVariable String rewardId,
            @Valid @RequestBody RedeemRewardRequest request) {
        return growthService.redeem(rewardId, request);
    }

    @PostMapping("/reward-redemptions/{redemptionId}/review")
    public RewardStoreView reviewReward(
            @PathVariable String redemptionId,
            @Valid @RequestBody ReviewRewardRequest request) {
        return growthService.review(redemptionId, request);
    }

    @PostMapping("/rewards/{rewardId}/equip")
    public RewardStoreView equipSkin(
            @PathVariable String rewardId,
            @Valid @RequestBody EquipSkinRequest request) {
        return growthService.equipSkin(rewardId, request);
    }
}
