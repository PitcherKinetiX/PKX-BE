package com.pkx.domain.comparison.entity;

import com.pkx.domain.analysis.entity.Analysis;
import com.pkx.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_comparisons", indexes = {
    @Index(name = "idx_comparisons_user", columnList = "user_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisComparison {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comparison_id")
    private Long comparisonId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_analysis_id", nullable = false)
    private Analysis previousAnalysis;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recent_analysis_id", nullable = false)
    private Analysis recentAnalysis;

    @Column(name = "improvement_percentage", precision = 5, scale = 2)
    private BigDecimal improvementPercentage;

    @Column(name = "comparison_summary", columnDefinition = "TEXT")
    private String comparisonSummary;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

}
