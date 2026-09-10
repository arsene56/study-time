package com.studytime.ocr;

import com.studytime.api.ApiModels.RecognitionCapabilityView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OcrGateway {
    private final String configuredProvider;
    private final List<OcrProvider> providers;

    public OcrGateway(
            @Value("${app.ocr.provider:mock}") String configuredProvider,
            List<OcrProvider> providers) {
        this.configuredProvider = configuredProvider.trim().toLowerCase();
        this.providers = providers;
    }

    public RecognitionCapabilityView capability() {
        OcrProvider provider = selectedProvider();
        if (provider == null) {
            return new RecognitionCapabilityView(
                    configuredProvider, "本地演示模式", false,
                    "尚未启用真实 OCR；可使用预置示例，上传照片后也能手动录入");
        }
        if (!provider.available()) {
            return new RecognitionCapabilityView(
                    configuredProvider, provider.label(), false,
                    "已选择腾讯云 OCR，但本机密钥未配置；照片会转入人工录入");
        }
        return new RecognitionCapabilityView(
                configuredProvider, provider.label(), true,
                "真实 OCR 已就绪，识别结果仍需家长确认后再生成计划");
    }

    public OcrResult recognize(byte[] imageBytes) {
        OcrProvider provider = selectedProvider();
        if (provider == null) {
            throw new IllegalStateException("真实 OCR 尚未启用，请配置 OCR_PROVIDER=tencent 和本机密钥");
        }
        return provider.recognize(imageBytes);
    }

    public String configuredProvider() {
        return configuredProvider;
    }

    private OcrProvider selectedProvider() {
        return providers.stream()
                .filter(provider -> provider.id().equalsIgnoreCase(configuredProvider))
                .findFirst()
                .orElse(null);
    }
}
