package com.pkx.domain.analysis.service;

import com.pkx.common.exception.BusinessException;
import com.pkx.common.exception.ErrorCode;
import com.pkx.config.MinIOConfig;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Service for handling video file uploads to MinIO storage.
 * Validates file type and size, extracts video metadata, and stores files.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoUploadService {

    private final MinioClient minioClient;
    private final MinIOConfig minioConfig;

    // Allowed video MIME types
    private static final List<String> ALLOWED_VIDEO_TYPES = Arrays.asList(
            "video/mp4",
            "video/mpeg",
            "video/quicktime",
            "video/x-msvideo",
            "video/x-matroska"
    );

    // Maximum file size: 500MB
    private static final long MAX_FILE_SIZE = 500 * 1024 * 1024;

    /**
     * Upload video file to MinIO and extract metadata.
     *
     * @param file The multipart file to upload
     * @param userId The user ID for organizing storage
     * @return VideoUploadResult containing storage path and metadata
     * @throws BusinessException if validation fails or upload error occurs
     */
    public VideoUploadResult uploadVideo(MultipartFile file, Long userId) {
        log.info("Starting video upload for user: {}, filename: {}", userId, file.getOriginalFilename());

        // Validate file
        validateFile(file);

        try {
            // Generate unique storage path
            String storagePath = generateStoragePath(userId, file.getOriginalFilename());

            // Extract video metadata before upload
            VideoMetadata metadata = extractVideoMetadata(file);

            // Upload to MinIO
            uploadToMinIO(file, storagePath);

            log.info("Video uploaded successfully to: {}", storagePath);

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

    /**
     * Validate file type and size.
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "File is empty");
        }

        // Check file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    String.format("File size exceeds maximum allowed size of %d MB", MAX_FILE_SIZE / 1024 / 1024));
        }

        // Check file type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_VIDEO_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE,
                    "Only video files are allowed (mp4, mpeg, mov, avi, mkv)");
        }
    }

    /**
     * Extract video metadata (duration, resolution, etc.).
     */
    private VideoMetadata extractVideoMetadata(MultipartFile file) {
        try {
            // For now, return basic metadata
            // In production, you might want to use FFmpeg or similar tools
            // to extract actual video duration and properties

            // Basic estimation: assume 30 fps and calculate from file size
            // This is a rough estimation - real implementation should use FFmpeg
            long fileSizeBytes = file.getSize();

            // Rough estimation: 1 minute of HD video ≈ 10-20 MB
            // So duration ≈ (fileSize in MB) / 15 * 60 seconds
            double fileSizeMB = fileSizeBytes / (1024.0 * 1024.0);
            double estimatedDuration = (fileSizeMB / 15.0) * 60.0;

            // Cap at reasonable values
            if (estimatedDuration > 300) { // 5 minutes max for initial estimation
                estimatedDuration = 300;
            }
            if (estimatedDuration < 5) { // At least 5 seconds
                estimatedDuration = 5;
            }

            return VideoMetadata.builder()
                    .durationSeconds(BigDecimal.valueOf(estimatedDuration))
                    .sizeBytes(fileSizeBytes)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to extract video metadata, using defaults", e);
            // Return default values if metadata extraction fails
            return VideoMetadata.builder()
                    .durationSeconds(BigDecimal.valueOf(10.0))
                    .sizeBytes(file.getSize())
                    .build();
        }
    }

    /**
     * Upload file to MinIO storage.
     */
    private void uploadToMinIO(MultipartFile file, String storagePath) {
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(storagePath)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to upload to MinIO", e);
            throw new BusinessException(ErrorCode.MINIO_ERROR, "Failed to upload video to storage", e);
        }
    }

    /**
     * Generate unique storage path for the video.
     * Format: videos/{userId}/{timestamp}_{uuid}_{filename}
     */
    private String generateStoragePath(Long userId, String originalFilename) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String sanitizedFilename = sanitizeFilename(originalFilename);

        return String.format("videos/%d/%s_%s_%s", userId, timestamp, uuid, sanitizedFilename);
    }

    /**
     * Sanitize filename to prevent path traversal and special characters.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "video.mp4";
        }

        // Remove path separators and keep only safe characters
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Check if file exists in MinIO.
     */
    public boolean fileExists(String storagePath) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(storagePath)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Result object for video upload operation.
     */
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

    /**
     * Video metadata extracted from the file.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class VideoMetadata {
        private BigDecimal durationSeconds;
        private Long sizeBytes;
    }
}
