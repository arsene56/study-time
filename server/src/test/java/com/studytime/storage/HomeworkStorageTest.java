package com.studytime.storage;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class HomeworkStorageTest {

    @Test
    void detectsRealImageSignatureInsteadOfTrustingFilename() {
        HomeworkStorage storage = new HomeworkStorage(mock(MinioClient.class), "test", 1024);
        MockMultipartFile png = new MockMultipartFile(
                "file", "homework.exe", "application/octet-stream",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1});

        HomeworkStorage.ImageType type = storage.detectImageType(png);

        assertThat(type.extension()).isEqualTo("png");
        assertThat(type.contentType()).isEqualTo("image/png");
    }

    @Test
    void rejectsContentThatIsNotAnAllowedImage() {
        HomeworkStorage storage = new HomeworkStorage(mock(MinioClient.class), "test", 1024);
        MockMultipartFile text = new MockMultipartFile(
                "file", "homework.jpg", "image/jpeg", "not an image".getBytes());

        assertThatThrownBy(() -> storage.store("demo-student-xiaoman", text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持 JPG、PNG 或 WebP");
    }

    @Test
    void rejectsOversizedUploadBeforeCallingObjectStorage() {
        HomeworkStorage storage = new HomeworkStorage(mock(MinioClient.class), "test", 4);
        MockMultipartFile file = new MockMultipartFile(
                "file", "homework.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2});

        assertThatThrownBy(() -> storage.store("demo-student-xiaoman", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能超过");
    }
}
