package com.studytime.homework;

import com.studytime.api.ApiModels.PersonalizationProfileView;
import com.studytime.api.ApiModels.SubjectPersonalizationView;
import com.studytime.domain.StudyTimeRepository;
import com.studytime.domain.StudyTimeRepository.HistorySample;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PersonalizedEstimationService {
    private static final double RECENCY_DECAY = 0.88;
    private final StudyTimeRepository repository;

    public PersonalizedEstimationService(StudyTimeRepository repository) {
        this.repository = repository;
    }

    public Estimate estimate(String childId, String subject, String taskType, int baselineMinutes) {
        List<HistorySample> exact = repository.findRecentExactHistory(childId, subject, taskType, 20);
        if (exact.size() >= 2) {
            double blend = Math.min(0.8, 0.25 + exact.size() * 0.1);
            return calculate(baselineMinutes, exact, "PERSONAL_HISTORY", blend,
                    "参考最近 " + exact.size() + " 次同类作业，已做异常值保护");
        }

        List<HistorySample> subjectHistory = repository.findRecentSubjectHistory(childId, subject, 20);
        if (subjectHistory.size() >= 4) {
            double blend = Math.min(0.6, 0.2 + subjectHistory.size() * 0.05);
            return calculate(baselineMinutes, subjectHistory, "SUBJECT_HISTORY", blend,
                    "同类样本较少，参考最近 " + subjectHistory.size() + " 次" + subject + "作业");
        }

        return new Estimate(
                baselineMinutes, baselineMinutes, "GRADE_DEFAULT", 0, "LOW",
                "历史样本不足，暂按年级通用规则估算", 0);
    }

    Estimate calculate(
            int baselineMinutes,
            List<HistorySample> samples,
            String source,
            double blend,
            String reason) {
        double pace = weightedPace(samples);
        double adjustedPace = 1 + (pace - 1) * blend;
        int minutes = roundToFive((int) Math.round(baselineMinutes * adjustedPace));
        String confidence = samples.size() >= 8 ? "HIGH" : samples.size() >= 4 ? "MEDIUM" : "LOW";
        int adjustmentPercent = (int) Math.round((adjustedPace - 1) * 100);
        return new Estimate(
                minutes, baselineMinutes, source, samples.size(), confidence, reason, adjustmentPercent);
    }

    public PersonalizationProfileView profile(String childId) {
        repository.requireChild(childId);
        List<HistorySample> samples = repository.findRecentChildHistory(childId, 200);
        int total = samples.size();
        int overallPace = total == 0 ? 100 : (int) Math.round(weightedPace(samples) * 100);
        String level = total >= 12 ? "STABLE" : total >= 4 ? "LEARNING" : "STARTING";
        String confidence = total >= 12 ? "HIGH" : total >= 4 ? "MEDIUM" : "LOW";
        String summary = switch (level) {
            case "STABLE" -> "嘀嘀已形成较稳定的个人时间曲线，后续会持续滚动更新";
            case "LEARNING" -> "嘀嘀正在学习孩子的节奏，再完成几项作业会更准确";
            default -> "刚开始积累样本，当前计划主要采用年级通用规则";
        };

        Map<String, List<HistorySample>> grouped = new LinkedHashMap<>();
        for (HistorySample sample : samples) {
            grouped.computeIfAbsent(sample.subject(), ignored -> new ArrayList<>()).add(sample);
        }
        List<SubjectPersonalizationView> subjects = grouped.entrySet().stream()
                .map(entry -> subjectView(entry.getKey(), entry.getValue()))
                .toList();
        return new PersonalizationProfileView(
                childId, total, level, confidence, overallPace, summary, subjects);
    }

    private SubjectPersonalizationView subjectView(String subject, List<HistorySample> samples) {
        int estimated = (int) Math.round(samples.stream()
                .mapToInt(HistorySample::estimatedMinutes).average().orElse(0));
        int actual = (int) Math.round(samples.stream()
                .mapToDouble(sample -> sample.actualSeconds() / 60.0).average().orElse(0));
        int pace = (int) Math.round(weightedPace(samples) * 100);
        String trend = pace > 110 ? "通常需要更多时间" : pace < 90 ? "通常完成得更快" : "与通用估时接近";
        String suggestion = samples.size() < 4
                ? "继续积累同学科样本"
                : pace > 110 ? "计划会逐步留出更充裕时间" : pace < 90 ? "计划会适度缩短，但保留缓冲" : "保持当前节奏";
        return new SubjectPersonalizationView(
                subject, samples.size(), estimated, actual, pace, trend, suggestion);
    }

    private double weightedPace(List<HistorySample> samples) {
        if (samples.isEmpty()) return 1D;
        double weighted = 0D;
        double totalWeight = 0D;
        for (int index = 0; index < samples.size(); index++) {
            HistorySample sample = samples.get(index);
            double baseline = Math.max(1D, sample.estimatedMinutes());
            double actualMinutes = sample.actualSeconds() / 60D;
            double protectedPace = Math.max(0.5, Math.min(2.0, actualMinutes / baseline));
            double weight = Math.pow(RECENCY_DECAY, index);
            weighted += protectedPace * weight;
            totalWeight += weight;
        }
        return weighted / totalWeight;
    }

    private int roundToFive(int value) {
        return Math.max(5, (int) Math.round(value / 5D) * 5);
    }

    public record Estimate(
            int minutes,
            int baseMinutes,
            String source,
            int sampleSize,
            String confidence,
            String reason,
            int adjustmentPercent) {
    }
}
