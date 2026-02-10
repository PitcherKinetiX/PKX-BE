package com.pkx.domain.analysis.repository;

import com.pkx.domain.analysis.entity.JointMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JointMetricsRepository extends JpaRepository<JointMetrics, Long> {
}
