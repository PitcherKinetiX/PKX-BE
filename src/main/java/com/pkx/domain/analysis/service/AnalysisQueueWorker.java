package com.pkx.domain.analysis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 분석 큐를 주기적으로 비우는 워커.
 * GPU가 1개라 AI가 직렬 처리하므로, 백엔드도 한 번에 1건씩 직렬로 처리한다.
 * (단일 스케줄러 스레드 + fixedDelay → 중첩 실행 없음 → DB 커넥션/스레드를 1개만 점유)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisQueueWorker {

    private final AnalysisQueueService queueService;
    private final AsyncAnalysisRunner analysisRunner;

    /** PROCESSING이 이 시간보다 오래되면 유실로 보고 큐로 회수 (폴링 최대 10분 + 여유) */
    private static final long STALE_THRESHOLD_MINUTES = 15;

    @Scheduled(fixedDelayString = "${analysis.queue.poll-interval-ms:5000}")
    public void drain() {
        // 1) 재시작 등으로 멈춘 PROCESSING 건 회수
        int reclaimed = queueService.reclaimStale(LocalDateTime.now().minusMinutes(STALE_THRESHOLD_MINUTES));
        if (reclaimed > 0) {
            log.info("Reclaimed {} stale analyses back to queue", reclaimed);
        }

        // 2) 대기 큐를 하나씩 직렬 처리
        Optional<Long> claimed;
        while ((claimed = queueService.claimNext()).isPresent()) {
            Long analysisId = claimed.get();
            try {
                analysisRunner.process(analysisId);
            } catch (Exception e) {
                // process() 내부에서 FAILED 처리하지만, 혹시 모를 누락 대비 안전망
                log.error("Queue worker failed processing analysisId: {}", analysisId, e);
            }
        }
    }
}
