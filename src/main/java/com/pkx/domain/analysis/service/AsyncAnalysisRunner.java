package com.pkx.domain.analysis.service;

import com.pkx.common.exception.BusinessException;
import com.pkx.common.exception.ErrorCode;
import com.pkx.domain.aimodel.service.AiModelService;
import com.pkx.domain.analysis.entity.Analysis;
import com.pkx.domain.analysis.entity.AnalysisResult;
import com.pkx.domain.analysis.repository.AnalysisRepository;
import com.pkx.domain.analysis.repository.AnalysisResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncAnalysisRunner {

    private final AnalysisRepository analysisRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AiAnalysisService aiAnalysisService;
    private final AiModelService aiModelService;

    /**
     * 큐 워커가 호출하는 동기 처리 메서드 (한 번에 하나씩 직렬 처리).
     * 호출 시점에 analysis는 이미 PROCESSING으로 claim된 상태.
     */
    @Transactional
    public void process(Long analysisId) {
        log.info("Starting analysis processing for analysisId: {}", analysisId);

        try {
            Analysis analysis = analysisRepository.findById(analysisId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

            AnalysisResult result = aiAnalysisService.analyzeVideo(analysisId);
            analysisResultRepository.save(result);

            analysis.markAsCompleted();
            analysisRepository.save(analysis);

            log.info("Analysis completed successfully for analysisId: {}", analysisId);

            // 분석 완료 영상으로 개인화 모델 백그라운드 증분 업데이트 (요구 3)
            try {
                aiModelService.updateModelIncrementally(analysis.getUser(), analysis.getVideoStoragePath());
            } catch (Exception e) {
                log.warn("Incremental model update trigger failed for analysisId {}: {}", analysisId, e.getMessage());
            }

        } catch (Exception e) {
            log.error("Analysis failed for analysisId: {}", analysisId, e);

            Analysis analysis = analysisRepository.findById(analysisId).orElse(null);
            if (analysis != null) {
                analysis.markAsFailed(e.getMessage());
                analysisRepository.save(analysis);
            }
        }
    }
}
