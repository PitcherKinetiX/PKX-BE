package com.pkx.domain.analysis.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "feature_details", indexes = {
    @Index(name = "idx_feature_details_result", columnList = "result_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feature_id")
    private Long featureId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_id", nullable = false)
    private AnalysisResult result;

    @Column(name = "feature_index", nullable = false)
    private Integer featureIndex;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "type", nullable = false, length = 20)
    private String type; // "angle" | "velocity"

    @Column(name = "user_error", nullable = false)
    private Double userError;

    @Column(name = "general_error", nullable = false)
    private Double generalError;

    @Column(name = "level", nullable = false, length = 10)
    private String level; // "정상" | "양호" | "주의" | "위험"

    // velocity 전용 필드 (angle 타입이면 null)
    @Column(name = "peak_value")
    private Double peakValue;

    @Column(name = "danger_ratio")
    private Double dangerRatio;

    @Column(name = "medical_score")
    private Integer medicalScore;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
