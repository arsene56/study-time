package com.studytime.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class HomeworkStorage {
    private static final Pattern SAFE_STUDENT_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private final MinioClient client;
    private final String bucket;
    private final long maxUploadBytes;

    public HomeworkStorage(
            MinioClient client,
            @Value("${app.storage.bucket}") String bucket,
            @Value("${app.storage.max-upload-bytes:10485760}") long maxUploadBytes) {
        this.client = client;
        this.bucket = bucket;
        this.maxUploadBytes = maxUploadBytes;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(0)
    public void ensureBucket() throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    public String store(String studentId, MultipartFile file) {
        if (studentId == null || !SAFE_STUDENT_ID.matcher(studentId).matches()) {
            throw new IllegalArgumentException("学生标识格式不正确");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请先选择一张作业照片");
        }
        if (file.getSize() > maxUploadBytes) {
            throw new IllegalArgumentException("作业照片不能超过 " + Math.max(1, maxUploadBytes / 1024 / 1024) + "MB");
        }
        ImageType imageType = detectImageType(file);
        String objectKey = studentId + "/" + UUID.randomUUID() + "." + imageType.extension();
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .contentType(imageType.contentType())
                    .stream(file.getInputStream(), file.getSize(), -1L)
                    .build());
            return objectKey;
        } catch (Exception exception) {
            throw new IllegalStateException("作业照片保存失败", exception);
        }
    }

    ImageType detectImageType(MultipartFile file) {
        byte[] header;
        try (InputStream input = file.getInputStream()) {
            header = input.readNBytes(12);
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法读取作业照片", exception);
        }
        if (startsWith(header, new int[]{0xFF, 0xD8, 0xFF})) {
            return new ImageType("jpg", "image/jpeg");
        }
        if (startsWith(header, new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            return new ImageType("png", "image/png");
        }
        if (header.length >= 12
                && Arrays.equals(Arrays.copyOfRange(header, 0, 4), new byte[]{'R', 'I', 'F', 'F'})
                && Arrays.equals(Arrays.copyOfRange(header, 8, 12), new byte[]{'W', 'E', 'B', 'P'})) {
            return new ImageType("webp", "image/webp");
        }
        throw new IllegalArgumentException("仅支持 JPG、PNG 或 WebP 格式的作业照片");
    }

    private boolean startsWith(byte[] actual, int[] expected) {
        if (actual.length < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (Byte.toUnsignedInt(actual[index]) != expected[index]) {
                return false;
            }
        }
        return true;
    }

    record ImageType(String extension, String contentType) {
    }
}
