package com.studytime.homework;

import com.studytime.ocr.OcrResult;
import com.studytime.ocr.OcrResult.OcrLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HomeworkTextParserTest {
    private final HomeworkTextParser parser = new HomeworkTextParser();

    @Test
    void splitsSubjectsAndNumberedHomeworkItems() {
        OcrResult result = new OcrResult("test", "request-1", List.of(
                new OcrLine("今日作业", 99),
                new OcrLine("语文：1. 朗读第8课两遍 2. 背诵古诗", 96),
                new OcrLine("数学作业：练习册第32-33页；订正试卷", 88),
                new OcrLine("科学", 91),
                new OcrLine("观察一株植物并记录", 91)), 93);

        List<HomeworkTextParser.ParsedTask> tasks = parser.parse(result, 3);

        assertThat(tasks).hasSize(5);
        assertThat(tasks).extracting(HomeworkTextParser.ParsedTask::subject)
                .containsExactly("语文", "语文", "数学", "数学", "科学");
        assertThat(tasks.get(0).taskType()).isEqualTo("朗读/阅读");
        assertThat(tasks.get(1).taskType()).isEqualTo("背诵/听写");
        assertThat(tasks.get(3).difficulty()).isEqualTo("CHALLENGE");
        assertThat(tasks.get(4).eyeLoad()).isEqualTo("LOW");
    }

    @Test
    void keepsUnknownSubjectAsOtherAndMarksLowConfidence() {
        OcrResult result = new OcrResult(
                "test", "request-2", List.of(new OcrLine("音乐：练习竖笛", 62)), 62);

        HomeworkTextParser.ParsedTask task = parser.parse(result, 1).getFirst();

        assertThat(task.subject()).isEqualTo("其他");
        assertThat(task.confidence()).isEqualTo("LOW");
    }
}
