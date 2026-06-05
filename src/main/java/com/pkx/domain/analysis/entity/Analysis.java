package com.pkx.domain.analysis.entity;

import com.pkx.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "analyses", indexes = {
    @Index(name = "idx_analyses_user", columnList = "user_id"),
    @Index(name = "idx_analyses_status", columnList = "status"),
    @Index(name = "idx_analyses_created", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "analysis_id")
    private Long analysisId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "video_filename", nullable = false, length = 255)
    private String videoFilename;

    @Column(name = "video_storage_path", nullable = false, length = 500)
    private String videoStoragePath;

    @Column(name = "video_size_bytes")
    private Long videoSizeBytes;

    @Column(name = "video_duration_seconds", precision = 10, scale = 2)
    private BigDecimal videoDurationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private AnalysisStatus status = AnalysisStatus.UPLOADING;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @OneToOne(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    private AnalysisResult result;

    public void markAsQueued() {
        this.status = AnalysisStatus.QUEUED;
    }

    public void markAsProcessing() {
        this.status = AnalysisStatus.PROCESSING;
        this.startedAt = LocalDateTime.now();
    }

    public void requeue() {
        this.status = AnalysisStatus.QUEUED;
        this.startedAt = null;
    }

    public void markAsCompleted() {
        this.status = AnalysisStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void markAsFailed(String errorMessage) {
        this.status = AnalysisStatus.FAILED;
        this.completedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }

    public enum AnalysisStatus {
        UPLOADING,
        QUEUED,
        PROCESSING,
        COMPLETED,
        FAILED
    }

}
