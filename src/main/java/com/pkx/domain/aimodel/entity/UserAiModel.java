package com.pkx.domain.aimodel.entity;

import com.pkx.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_ai_models", indexes = {
    @Index(name = "idx_user_models_user", columnList = "user_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAiModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "model_id")
    private Long modelId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "training_data_count")
    @Builder.Default
    private Integer trainingDataCount = 0;

    @Column(name = "model_accuracy", precision = 5, scale = 2)
    private BigDecimal modelAccuracy;

    @Column(name = "training_progress")
    @Builder.Default
    private Integer trainingProgress = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private ModelStatus status = ModelStatus.NOT_TRAINED;

    @Column(name = "model_storage_path", length = 500)
    private String modelStoragePath;

    @Column(name = "last_trained_at")
    private LocalDateTime lastTrainedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void markAsTraining() {
        this.status = ModelStatus.TRAINING;
        this.trainingProgress = 0;
    }

    public void markAsReady(BigDecimal accuracy) {
        this.status = ModelStatus.READY;
        this.trainingProgress = 100;
        this.modelAccuracy = accuracy;
        this.lastTrainedAt = LocalDateTime.now();
    }

    public enum ModelStatus {
        NOT_TRAINED,
        TRAINING,
        READY
    }

}
