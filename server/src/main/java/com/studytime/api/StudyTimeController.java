package com.studytime.api;

import com.studytime.api.ApiModels.ActivityView;
import com.studytime.api.ApiModels.CompletePlanItemRequest;
import com.studytime.api.ApiModels.CreatePlanRequest;
import com.studytime.api.ApiModels.DemoContextView;
import com.studytime.api.ApiModels.HomeworkBatchView;
import com.studytime.api.ApiModels.PlanView;
import com.studytime.domain.StudyTimeRepository;
import com.studytime.homework.HomeworkService;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class StudyTimeController {
    private final HomeworkService homeworkService;
    private final StudyTimeRepository repository;

    public StudyTimeController(HomeworkService homeworkService, StudyTimeRepository repository) {
        this.homeworkService = homeworkService;
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

    @PostMapping("/plan-items/{itemId}/complete")
    public PlanView completePlanItem(
            @PathVariable String itemId,
            @RequestBody(required = false) CompletePlanItemRequest request) {
        return homeworkService.completeItem(itemId, request);
    }
}
