package com.pkx.domain.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for video upload operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Video upload response")
public class UploadResponse {

    @Schema(description = "Analysis ID", example = "123")
    private Long analysisId;

    @Schema(description = "Original filename", example = "pitching_video.mp4")
    private String filename;

    @Schema(description = "File size in bytes", example = "52428800")
    private Long sizeBytes;

    @Schema(description = "Video duration in seconds", example = "15.5")
    private BigDecimal durationSeconds;

    @Schema(description = "Current analysis status", example = "PROCESSING")
    private String status;

    @Schema(description = "Upload timestamp")
    private LocalDateTime uploadedAt;

    @Schema(description = "Message", example = "Video uploaded successfully and analysis started")
    private String message;
}
