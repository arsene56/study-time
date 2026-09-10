package com.studytime.homework;

import com.studytime.ocr.OcrResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HomeworkTextParser {
    private static final Pattern LEADING_NUMBER = Pattern.compile(
            "^\\s*(?:[（(]?[0-9一二三四五六七八九十]+[）).、:]|[①②③④⑤⑥⑦⑧⑨⑩])\\s*");
    private static final Pattern SUBJECT = Pattern.compile(
            "^(语文|数学|英语|科学|劳动|美术|道法|体育|音乐|其他)(?:作业)?\\s*[：:]?\\s*(.*)$");
    private static final Pattern INLINE_NUMBER = Pattern.compile(
            "\\s+(?=(?:[0-9]+[.、）)]|[①②③④⑤⑥⑦⑧⑨⑩]))");
    private static final Map<String, String> SUBJECT_ALIASES = Map.of(
            "道法", "其他", "体育", "其他", "音乐", "其他");

    public List<ParsedTask> parse(OcrResult result, int grade) {
        List<ParsedTask> tasks = new ArrayList<>();
        String currentSubject = "其他";
        for (OcrResult.OcrLine ocrLine : result.lines()) {
            String line = normalize(ocrLine.text());
            if (line.isBlank() || isNoise(line)) continue;

            line = LEADING_NUMBER.matcher(line).replaceFirst("");
            Matcher subjectMatcher = SUBJECT.matcher(line);
            if (subjectMatcher.matches()) {
                currentSubject = normalizeSubject(subjectMatcher.group(1));
                line = subjectMatcher.group(2).trim();
                if (line.isBlank()) continue;
            }

            for (String segment : splitTasks(line)) {
                String title = LEADING_NUMBER.matcher(segment.trim()).replaceFirst("").trim();
                if (title.length() < 2 || isNoise(title)) continue;
                String taskType = GradeTaskRules.taskType(title);
                tasks.add(new ParsedTask(
                        currentSubject,
                        title,
                        taskType,
                        GradeTaskRules.icon(currentSubject, taskType),
                        GradeTaskRules.baselineMinutes(grade, title, taskType),
                        GradeTaskRules.difficulty(title, taskType),
                        GradeTaskRules.eyeLoad(taskType),
                        confidenceLevel(ocrLine.confidence()),
                        roundConfidence(ocrLine.confidence())));
            }
        }
        return tasks;
    }

    private List<String> splitTasks(String line) {
        List<String> result = new ArrayList<>();
        for (String sentence : line.split("[；;]")) {
            String[] numbered = INLINE_NUMBER.split(sentence.trim());
            for (String item : numbered) {
                if (!item.isBlank()) result.add(item);
            }
        }
        return result;
    }

    private String normalize(String source) {
        if (source == null) return "";
        return source.replace('\u00A0', ' ')
                .replaceAll("[\\t\\r]+", " ")
                .replaceAll(" {2,}", " ")
                .trim();
    }

    private String normalizeSubject(String subject) {
        return SUBJECT_ALIASES.getOrDefault(subject, subject);
    }

    private boolean isNoise(String line) {
        return line.matches("^(作业|今日作业|家庭作业|日期|星期[一二三四五六日天])[:：]?$");
    }

    private String confidenceLevel(double confidence) {
        if (confidence >= 90) return "HIGH";
        if (confidence >= 75) return "MEDIUM";
        return "LOW";
    }

    private double roundConfidence(double confidence) {
        return Math.round(Math.max(0D, Math.min(100D, confidence)) * 100D) / 100D;
    }

    public record ParsedTask(
            String subject,
            String title,
            String taskType,
            String icon,
            int baseMinutes,
            String difficulty,
            String eyeLoad,
            String confidence,
            double ocrConfidence) {
    }
}
