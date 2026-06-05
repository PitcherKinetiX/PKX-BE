package com.pkx.domain.analysis.repository;

import com.pkx.domain.analysis.entity.Analysis;
import com.pkx.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    Page<Analysis> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    List<Analysis> findByUser(User user);

    /** 큐에서 가장 오래된 대기 건 1개 (FIFO) */
    Optional<Analysis> findFirstByStatusOrderByCreatedAtAsc(Analysis.AnalysisStatus status);

    /** 재시작 등으로 멈춘 채 남은 PROCESSING 건 회수용 */
    List<Analysis> findByStatusAndStartedAtBefore(Analysis.AnalysisStatus status, LocalDateTime threshold);

    Page<Analysis> findByUser_UserId(Long userId, Pageable pageable);

    @Query("SELECT a FROM Analysis a LEFT JOIN FETCH a.result r LEFT JOIN FETCH r.jointMetrics WHERE a.analysisId = :analysisId")
    Optional<Analysis> findByIdWithResult(Long analysisId);

    Optional<Analysis> findByAnalysisIdAndUser(Long analysisId, User user);

}
