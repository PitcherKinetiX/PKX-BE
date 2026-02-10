package com.pkx.domain.comparison.repository;

import com.pkx.domain.comparison.entity.AnalysisComparison;
import com.pkx.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ComparisonRepository extends JpaRepository<AnalysisComparison, Long> {

    Page<AnalysisComparison> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

}
