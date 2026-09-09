package com.studytime.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class HomeworkStorage {
    private final MinioClient client;
    private final String bucket;

    public HomeworkStorage(MinioClient client, @Value("${app.storage.bucket}") String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucket() throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    public String store(String childId, MultipartFile file) {
        String originalName = file.getOriginalFilename() == null ? "homework.jpg" : file.getOriginalFilename();
        String safeName = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String objectKey = childId + "/" + UUID.randomUUID() + "-" + safeName;
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .contentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                    .stream(file.getInputStream(), file.getSize(), -1L)
                    .build());
            return objectKey;
        } catch (Exception exception) {
            throw new IllegalStateException("作业照片保存失败", exception);
        }
    }
}
