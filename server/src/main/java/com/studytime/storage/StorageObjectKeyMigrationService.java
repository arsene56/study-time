package com.studytime.storage;

import io.minio.CopyObjectArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.SourceObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StorageObjectKeyMigrationService {
    private static final Logger log = LoggerFactory.getLogger(StorageObjectKeyMigrationService.class);
    private static final String PENDING = "PENDING";
    private static final String REFERENCE_UPDATED = "REFERENCE_UPDATED";
    private static final int MAX_ERROR_LENGTH = 950;

    private final MinioClient minioClient;
    private final JdbcClient jdbc;
    private final String bucket;

    public StorageObjectKeyMigrationService(
            MinioClient minioClient,
            JdbcClient jdbc,
            @Value("${app.storage.bucket}") String bucket) {
        this.minioClient = minioClient;
        this.jdbc = jdbc;
        this.bucket = bucket;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(100)
    public void migrateQueuedObjectKeys() {
        List<MigrationRow> migrations = jdbc.sql("""
                        SELECT id, batch_id, source_object_key, target_object_key, status
                        FROM storage_key_migrations
                        WHERE status IN ('PENDING', 'REFERENCE_UPDATED')
                        ORDER BY id
                        """)
                .query((rs, rowNum) -> new MigrationRow(
                        rs.getLong("id"),
                        rs.getString("batch_id"),
                        rs.getString("source_object_key"),
                        rs.getString("target_object_key"),
                        rs.getString("status")))
                .list();

        for (MigrationRow migration : migrations) {
            migrate(migration);
        }
    }

    private void migrate(MigrationRow migration) {
        String status = migration.status();
        try {
            if (PENDING.equals(status)) {
                copyObject(migration);
                switchDatabaseReference(migration);
                status = REFERENCE_UPDATED;
            }

            if (REFERENCE_UPDATED.equals(status)) {
                removeSourceObject(migration);
                jdbc.sql("DELETE FROM storage_key_migrations WHERE id = :id")
                        .param("id", migration.id())
                        .update();
                log.info("对象路径已迁移：{} -> {}", migration.sourceObjectKey(), migration.targetObjectKey());
            }
        } catch (Exception exception) {
            recordFailure(migration.id(), exception);
            log.error("对象路径迁移失败，后续启动会自动重试：{} -> {}",
                    migration.sourceObjectKey(), migration.targetObjectKey(), exception);
        }
    }

    private void copyObject(MigrationRow migration) throws Exception {
        minioClient.copyObject(CopyObjectArgs.builder()
                .bucket(bucket)
                .object(migration.targetObjectKey())
                .source(SourceObject.builder()
                        .bucket(bucket)
                        .object(migration.sourceObjectKey())
                        .build())
                .build());
    }

    private void switchDatabaseReference(MigrationRow migration) {
        int updated = jdbc.sql("""
                        UPDATE homework_batches
                        SET source_object_key = :targetObjectKey
                        WHERE id = :batchId AND source_object_key = :sourceObjectKey
                        """)
                .param("targetObjectKey", migration.targetObjectKey())
                .param("batchId", migration.batchId())
                .param("sourceObjectKey", migration.sourceObjectKey())
                .update();

        if (updated == 0) {
            String currentKey = jdbc.sql("SELECT source_object_key FROM homework_batches WHERE id = :batchId")
                    .param("batchId", migration.batchId())
                    .query(String.class)
                    .optional()
                    .orElse(null);
            if (!migration.targetObjectKey().equals(currentKey)) {
                throw new IllegalStateException("作业批次的对象引用已发生其他变更");
            }
        }

        jdbc.sql("""
                        UPDATE storage_key_migrations
                        SET status = 'REFERENCE_UPDATED', last_error = NULL
                        WHERE id = :id
                        """)
                .param("id", migration.id())
                .update();
    }

    private void removeSourceObject(MigrationRow migration) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(migration.sourceObjectKey())
                .build());
    }

    private void recordFailure(long id, Exception exception) {
        String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        if (message.length() > MAX_ERROR_LENGTH) {
            message = message.substring(0, MAX_ERROR_LENGTH);
        }
        jdbc.sql("""
                        UPDATE storage_key_migrations
                        SET attempts = attempts + 1, last_error = :message
                        WHERE id = :id
                        """)
                .param("message", message)
                .param("id", id)
                .update();
    }

    private record MigrationRow(
            long id,
            String batchId,
            String sourceObjectKey,
            String targetObjectKey,
            String status) {
    }
}
