package com.studytime.ocr;

import java.util.List;

public record OcrResult(
        String provider,
        String requestId,
        List<OcrLine> lines,
        double averageConfidence) {

    public String rawText() {
        return lines.stream()
                .map(OcrLine::text)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    public record OcrLine(String text, double confidence) {
    }
}
