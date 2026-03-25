package com.pkx.domain.analysis.repository;

import com.pkx.domain.analysis.entity.FeatureDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeatureDetailRepository extends JpaRepository<FeatureDetail, Long> {
    List<FeatureDetail> findByResult_ResultIdOrderByFeatureIndexAsc(Long resultId);
}
