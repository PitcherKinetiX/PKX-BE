package com.pkx.domain.aimodel.client;

import com.pkx.domain.aimodel.dto.AiTrainRequest;
import com.pkx.domain.aimodel.dto.AiTrainStartResponse;
import com.pkx.domain.aimodel.dto.AiTrainStatusResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 개인화 모델 학습용 AI 서버 클라이언트.
 * 분석 클라이언트(ai-analysis)와 동일한 AI 서버를 가리킨다.
 */
@FeignClient(
        name = "ai-train",
        url = "${ai.server.url}"
)
public interface AiTrainFeignClient {

    /**
     * 비동기 학습 시작 — jobId를 즉시 반환 (짧은 호출).
     */
    @PostMapping("/api/train/start")
    AiTrainStartResponse startTrain(@RequestBody AiTrainRequest request);

    /**
     * 학습 잡 상태/결과 폴링 (짧은 호출).
     */
    @GetMapping("/api/train/status/{jobId}")
    AiTrainStatusResponse getStatus(@PathVariable("jobId") String jobId);
}
