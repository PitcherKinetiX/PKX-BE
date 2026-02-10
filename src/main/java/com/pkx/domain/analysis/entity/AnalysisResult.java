package com.pkx.domain.analysis.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_results", indexes = {
    @Index(name = "idx_results_analysis", columnList = "analysis_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "result_id")
    private Long resultId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_id", nullable = false, unique = true)
    private Analysis analysis;

    @Column(name = "overall_risk_score", nullable = false)
    private Integer overallRiskScore;

    @Column(name = "consistency_score", nullable = false)
    private Integer consistencyScore;

    @Column(name = "medical_risk_score", nullable = false)
    private Integer medicalRiskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_grade", nullable = false, length = 20)
    private RiskGrade riskGrade;

    @Enumerated(EnumType.STRING)
    @Column(name = "model_type", nullable = false, length = 50)
    private ModelType modelType;

    @Column(name = "model_accuracy", precision = 5, scale = 2)
    private BigDecimal modelAccuracy;

    @Column(name = "risk_summary", columnDefinition = "TEXT")
    private String riskSummary;

    @Column(name = "recommendations", columnDefinition = "TEXT")
    private String recommendations;

    @Column(name = "critical_zone_detected")
    @Builder.Default
    private Boolean criticalZoneDetected = false;

    @Column(name = "critical_zone_description", columnDefinition = "TEXT")
    private String criticalZoneDescription;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToOne(mappedBy = "result", cascade = CascadeType.ALL, orphanRemoval = true)
    private JointMetrics jointMetrics;

    public enum RiskGrade {
        GOOD,
        NORMAL,
        CAUTION,
        DANGER
    }

    public enum ModelType {
        GENERAL,
        USER_SPECIFIC
    }

}
