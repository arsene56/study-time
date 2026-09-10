package com.studytime.homework;

import java.util.Locale;

public final class GradeTaskRules {
    private GradeTaskRules() {
    }

    public static String taskType(String title) {
        String text = title.toLowerCase(Locale.ROOT);
        if (containsAny(text, "听写", "默写", "背诵", "背会", "背熟")) {
            return "背诵/听写";
        }
        if (containsAny(text, "跟读", "口语", "录音", "对话")) {
            return "口语";
        }
        if (containsAny(text, "朗读", "阅读", "读书", "预习")) {
            return "朗读/阅读";
        }
        if (containsAny(text, "观察", "实验", "制作", "实践", "调查")) {
            return "实践";
        }
        if (containsAny(text, "画", "绘画", "涂色")) {
            return "绘画";
        }
        if (containsAny(text, "劳动", "整理", "打扫", "洗碗", "做饭")) {
            return "劳动";
        }
        return "书写";
    }

    public static String difficulty(String title, String taskType) {
        if (containsAny(title, "作文", "试卷", "拓展", "订正", "思考题", "应用题", "小报")) {
            return "CHALLENGE";
        }
        if ("朗读/阅读".equals(taskType) || "口语".equals(taskType)
                || ("实践".equals(taskType) && containsAny(title, "观察"))) {
            return "EASY";
        }
        return "MODERATE";
    }

    public static String eyeLoad(String taskType) {
        return switch (taskType) {
            case "口语", "劳动", "实践" -> "LOW";
            default -> "HIGH";
        };
    }

    public static int baselineMinutes(int grade, String title, String taskType) {
        int safeGrade = Math.max(1, Math.min(6, grade));
        int minutes = switch (taskType) {
            case "背诵/听写" -> 10 + safeGrade * 2;
            case "口语" -> 8 + safeGrade;
            case "朗读/阅读" -> 8 + safeGrade * 2;
            case "实践" -> 15 + safeGrade * 2;
            case "绘画" -> 20 + safeGrade * 3;
            case "劳动" -> 15 + safeGrade;
            default -> 12 + safeGrade * 5;
        };
        if (containsAny(title, "作文", "小报")) {
            minutes += 15;
        } else if (containsAny(title, "试卷", "整张", "一套")) {
            minutes += 10;
        } else if (containsAny(title, "两遍", "2遍", "二遍", "三遍", "3遍")) {
            minutes += 4;
        }
        return Math.max(5, Math.min(60, roundToFive(minutes)));
    }

    public static String icon(String subject, String taskType) {
        if ("口语".equals(taskType)) return "🎧";
        if ("实践".equals(taskType)) return "🔍";
        if ("绘画".equals(taskType)) return "🎨";
        if ("劳动".equals(taskType)) return "🧹";
        return switch (subject) {
            case "语文" -> "📖";
            case "数学" -> "✏️";
            case "英语" -> "🔤";
            case "科学" -> "🧪";
            case "美术" -> "🎨";
            case "劳动" -> "🧹";
            default -> "⭐";
        };
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private static int roundToFive(int value) {
        return Math.max(5, (int) Math.round(value / 5.0) * 5);
    }
}
