package com.studytime.ocr;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.ocr.v20181119.OcrClient;
import com.tencentcloudapi.ocr.v20181119.models.GeneralAccurateOCRRequest;
import com.tencentcloudapi.ocr.v20181119.models.GeneralAccurateOCRResponse;
import com.tencentcloudapi.ocr.v20181119.models.TextDetection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Component
public class TencentCloudOcrProvider implements OcrProvider {
    private final String secretId;
    private final String secretKey;
    private final String region;

    public TencentCloudOcrProvider(
            @Value("${app.ocr.tencent.secret-id:}") String secretId,
            @Value("${app.ocr.tencent.secret-key:}") String secretKey,
            @Value("${app.ocr.tencent.region:ap-guangzhou}") String region) {
        this.secretId = secretId;
        this.secretKey = secretKey;
        this.region = region;
    }

    @Override
    public String id() {
        return "tencent";
    }

    @Override
    public String label() {
        return "腾讯云高精度通用文字识别";
    }

    @Override
    public boolean available() {
        return !secretId.isBlank() && !secretKey.isBlank();
    }

    @Override
    public OcrResult recognize(byte[] imageBytes) {
        if (!available()) {
            throw new IllegalStateException("腾讯云 OCR 尚未配置，请在本机环境变量中填写密钥后重启服务");
        }
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("作业照片不能为空");
        }

        try {
            Credential credential = new Credential(secretId, secretKey);
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("ocr.tencentcloudapi.com");
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            OcrClient client = new OcrClient(credential, region, clientProfile);

            GeneralAccurateOCRRequest request = new GeneralAccurateOCRRequest();
            request.setImageBase64(Base64.getEncoder().encodeToString(imageBytes));
            GeneralAccurateOCRResponse response = client.GeneralAccurateOCR(request);
            TextDetection[] detections = response.getTextDetections();
            List<OcrResult.OcrLine> lines = detections == null
                    ? List.of()
                    : Arrays.stream(detections)
                            .map(item -> new OcrResult.OcrLine(
                                    item.getDetectedText(),
                                    item.getConfidence() == null ? 0D : item.getConfidence().doubleValue()))
                            .toList();
            double average = lines.stream().mapToDouble(OcrResult.OcrLine::confidence).average().orElse(0D);
            return new OcrResult(id(), response.getRequestId(), lines, average);
        } catch (TencentCloudSDKException exception) {
            throw new IllegalStateException("腾讯云 OCR 调用失败：" + exception.getMessage(), exception);
        }
    }
}
