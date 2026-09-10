package com.studytime.homework;

import com.studytime.domain.StudyTimeRepository;
import com.studytime.domain.StudyTimeRepository.TaskRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PlanningServiceTest {

    @Test
    void ordersWarmupBeforeChallengeAndKeepsEasyFinish() {
        PlanningService service = new PlanningService(mock(StudyTimeRepository.class));
        List<TaskRow> ordered = service.orderTasks(List.of(
                task("math", 30, "CHALLENGE", 1),
                task("english", 15, "EASY", 2),
                task("read", 10, "EASY", 0),
                task("science", 20, "MODERATE", 3)));

        assertThat(ordered).extracting(TaskRow::id)
                .containsExactly("read", "math", "science", "english");
    }

    private TaskRow task(String id, int minutes, String difficulty, int sortOrder) {
        return new TaskRow(
                id, "batch", "child", "学科", id, "类型", "⭐", minutes,
                minutes, "GRADE_DEFAULT", 0, "LOW", "按年级规则", difficulty,
                "HIGH", "HIGH", 98D, false, "PENDING_CONFIRMATION", sortOrder);
    }
}
