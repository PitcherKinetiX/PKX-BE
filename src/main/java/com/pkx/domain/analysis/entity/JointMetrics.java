package com.pkx.domain.analysis.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "joint_metrics", indexes = {
    @Index(name = "idx_joint_metrics_result", columnList = "result_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JointMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "metric_id")
    private Long metricId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_id", nullable = false, unique = true)
    private AnalysisResult result;

    // 6개 관절 카테고리 점수 (0-100)
    @Column(name = "shoulder_stress", nullable = false)
    private Integer shoulderStress;

    @Column(name = "elbow_load", nullable = false)
    private Integer elbowLoad;

    @Column(name = "wrist_load", nullable = false)
    private Integer wristLoad;

    @Column(name = "spine_angle", nullable = false)
    private Integer spineAngle;

    @Column(name = "knee_stability", nullable = false)
    private Integer kneeStability;

    @Column(name = "hip_rotation", nullable = false)
    private Integer hipRotation;

    // 레이더 차트용 원본 데이터 (JSON)
    @Column(name = "radar_data", columnDefinition = "jsonb")
    private String radarData;

    // 시계열 데이터 (프레임별 에러)
    @Column(name = "temporal_error_data", columnDefinition = "jsonb")
    private String temporalErrorData;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

}
