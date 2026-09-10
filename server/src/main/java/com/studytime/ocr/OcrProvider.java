package com.studytime.ocr;

public interface OcrProvider {
    String id();

    String label();

    boolean available();

    OcrResult recognize(byte[] imageBytes);
}
