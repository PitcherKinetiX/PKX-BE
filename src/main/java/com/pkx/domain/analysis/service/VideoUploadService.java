package com.pkx.domain.analysis.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import com.pkx.common.exception.BusinessException;
import com.pkx.common.exception.ErrorCode;
import com.pkx.config.GcsConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoUploadService {

    private final Storage storage;
    private final GcsConfig gcsConfig;

    private static final List<String> ALLOWED_VIDEO_TYPES = Arrays.asList(
            "video/mp4",
            "video/mpeg",
            "video/quicktime",
            "video/x-msvideo",
            "video/x-matroska"
    );

    private static final long MAX_FILE_SIZE = 500 * 1024 * 1024; // 500MB

    public VideoUploadResult uploadVideo(MultipartFile file, Long userId) {
        return uploadVideo(file, userId, "videos");
    }

    public VideoUploadResult uploadVideo(MultipartFile file, Long userId, String prefix) {
        log.info("Starting video upload for user: {}, filename: {}, prefix: {}",
                userId, file.getOriginalFilename(), prefix);

        validateFile(file);

        try {
            String storagePath = generateStoragePath(userId, file.getOriginalFilename(), prefix);
            VideoMetadata metadata = extractVideoMetadata(file);

            // Upload to GCS
            uploadToGcs(file, storagePath);

            log.info("Video uploaded successfully to GCS: {}", storagePath);

            return VideoUploadResult.builder()
                    .storagePath(storagePath)
                    .filename(file.getOriginalFilename())
                    .sizeBytes(file.getSize())
                    .durationSeconds(metadata.getDurationSeconds())
                    .build();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to upload video", e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, e.getMessage(), e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    String.format("File size exceeds maximum allowed size of %d MB", MAX_FILE_SIZE / 1024 / 1024));
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_VIDEO_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE,
                    "Only video files are allowed (mp4, mpeg, mov, avi, mkv)");
        }
    }

    private VideoMetadata extractVideoMetadata(MultipartFile file) {
        try {
            long fileSizeBytes = file.getSize();
            double fileSizeMB = fileSizeBytes / (1024.0 * 1024.0);
            double estimatedDuration = (fileSizeMB / 15.0) * 60.0;

            if (estimatedDuration > 300) estimatedDuration = 300;
            if (estimatedDuration < 5) estimatedDuration = 5;

            return VideoMetadata.builder()
                    .durationSeconds(BigDecimal.valueOf(estimatedDuration))
                    .sizeBytes(fileSizeBytes)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to extract video metadata, using defaults", e);
            return VideoMetadata.builder()
                    .durationSeconds(BigDecimal.valueOf(10.0))
                    .sizeBytes(file.getSize())
                    .build();
        }
    }

    private void uploadToGcs(MultipartFile file, String storagePath) {
        try {
            BlobId blobId = BlobId.of(gcsConfig.getBucketName(), storagePath);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(file.getContentType())
                    .build();

            storage.create(blobInfo, file.getBytes());
        } catch (Exception e) {
            log.error("Failed to upload to GCS", e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, "Failed to upload video to GCS", e);
        }
    }

    public String generateSignedUrl(String storagePath) {
        return generateSignedUrl(storagePath, 15);
    }

    public String generateSignedUrl(String storagePath, long expiryMinutes) {
        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(gcsConfig.getBucketName(), storagePath).build();
            URL signedUrl = storage.signUrl(
                    blobInfo,
                    expiryMinutes, TimeUnit.MINUTES,
                    Storage.SignUrlOption.withV4Signature()
            );
            return signedUrl.toString();
        } catch (Exception e) {
            log.error("Failed to generate signed URL for: {}", storagePath, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, "Signed URL 생성 실패: " + e.getMessage());
        }
    }

    /**
     * 업로드(PUT)용 V4 Signed URL 생성. AI 서버가 학습 산출물(pth/pkl)을 직접 업로드할 때 사용.
     * 호출 측은 PUT 요청에 동일한 Content-Type 헤더를 포함해야 한다.
     */
    public String generateUploadSignedUrl(String storagePath, String contentType, long expiryMinutes) {
        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(gcsConfig.getBucketName(), storagePath)
                    .setContentType(contentType)
                    .build();
            URL signedUrl = storage.signUrl(
                    blobInfo,
                    expiryMinutes, TimeUnit.MINUTES,
                    Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                    Storage.SignUrlOption.withContentType(),
                    Storage.SignUrlOption.withV4Signature()
            );
            return signedUrl.toString();
        } catch (Exception e) {
            log.error("Failed to generate upload signed URL for: {}", storagePath, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, "Upload Signed URL 생성 실패: " + e.getMessage());
        }
    }

    public boolean fileExists(String storagePath) {
        try {
            BlobId blobId = BlobId.of(gcsConfig.getBucketName(), storagePath);
            return storage.get(blobId) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public void deleteFile(String storagePath) {
        try {
            BlobId blobId = BlobId.of(gcsConfig.getBucketName(), storagePath);
            storage.delete(blobId);
            log.info("Deleted file from GCS: {}", storagePath);
        } catch (Exception e) {
            log.warn("Failed to delete file from GCS: {}", storagePath, e);
        }
    }

    private String generateStoragePath(Long userId, String originalFilename, String prefix) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String sanitizedFilename = sanitizeFilename(originalFilename);
        return String.format("%s/%d/%s_%s_%s", prefix, userId, timestamp, uuid, sanitizedFilename);
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "video.mp4";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VideoUploadResult {
        private String storagePath;
        private String filename;
        private Long sizeBytes;
        private BigDecimal durationSeconds;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class VideoMetadata {
        private BigDecimal durationSeconds;
        private Long sizeBytes;
    }
}
