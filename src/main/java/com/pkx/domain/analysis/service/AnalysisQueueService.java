package com.pkx.domain.analysis.service;

import com.pkx.domain.analysis.entity.Analysis;
import com.pkx.domain.analysis.repository.AnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 분석 큐의 원자적 claim / 회수를 담당. (트랜잭션 경계 분리용 별도 빈)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisQueueService {

    private final AnalysisRepository analysisRepository;

    /**
     * 가장 오래된 QUEUED 건을 하나 집어 PROCESSING으로 전환하고 id를 반환.
     * 단일 백엔드 인스턴스 + 단일 워커 스레드 전제이므로 추가 락 없이 안전.
     */
    @Transactional
    public Optional<Long> claimNext() {
        return analysisRepository.findFirstByStatusOrderByCreatedAtAsc(Analysis.AnalysisStatus.QUEUED)
                .map(analysis -> {
                    analysis.markAsProcessing();
                    analysisRepository.save(analysis);
                    return analysis.getAnalysisId();
                });
    }

    /**
     * threshold보다 오래 PROCESSING에 멈춰 있는 건들을 다시 QUEUED로 회수.
     * (백엔드 재시작으로 처리 중 유실된 건 복구)
     */
    @Transactional
    public int reclaimStale(LocalDateTime threshold) {
        List<Analysis> stale = analysisRepository.findByStatusAndStartedAtBefore(
                Analysis.AnalysisStatus.PROCESSING, threshold);
        for (Analysis analysis : stale) {
            log.warn("Reclaiming stale PROCESSING analysis back to queue: {}", analysis.getAnalysisId());
            analysis.requeue();
            analysisRepository.save(analysis);
        }
        return stale.size();
    }
}
