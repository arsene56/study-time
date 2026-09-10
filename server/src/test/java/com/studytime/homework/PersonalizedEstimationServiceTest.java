package com.studytime.homework;

import com.studytime.domain.StudyTimeRepository;
import com.studytime.domain.StudyTimeRepository.HistorySample;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PersonalizedEstimationServiceTest {
    private final PersonalizedEstimationService service =
            new PersonalizedEstimationService(mock(StudyTimeRepository.class));

    @Test
    void usesRecentSamplesAndClampsExtremeOutliers() {
        List<HistorySample> samples = List.of(
                sample(20, 240),
                sample(20, 24),
                sample(20, 22),
                sample(20, 18));

        PersonalizedEstimationService.Estimate estimate = service.calculate(
                20, samples, "PERSONAL_HISTORY", 0.8, "测试");

        assertThat(estimate.minutes()).isBetween(20, 30);
        assertThat(estimate.source()).isEqualTo("PERSONAL_HISTORY");
        assertThat(estimate.sampleSize()).isEqualTo(4);
        assertThat(estimate.confidence()).isEqualTo("MEDIUM");
    }

    @Test
    void protectsBaselineWhileHistoryIsOnlySlightlyFaster() {
        List<HistorySample> samples = List.of(sample(30, 24), sample(30, 27));

        PersonalizedEstimationService.Estimate estimate = service.calculate(
                30, samples, "PERSONAL_HISTORY", 0.45, "测试");

        assertThat(estimate.minutes()).isEqualTo(30);
        assertThat(estimate.adjustmentPercent()).isBetween(-10, 0);
    }

    private HistorySample sample(int baselineMinutes, int actualMinutes) {
        return new HistorySample(
                "数学", "书写", baselineMinutes, actualMinutes * 60, LocalDateTime.now());
    }
}
