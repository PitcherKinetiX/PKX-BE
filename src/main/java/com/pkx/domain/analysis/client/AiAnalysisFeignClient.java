package com.pkx.domain.analysis.client;

import com.pkx.domain.analysis.dto.AiAnalyzeRequest;
import com.pkx.domain.analysis.dto.AiAnalyzeResponse;
import com.pkx.domain.analysis.dto.AiAnalyzeStartResponse;
import com.pkx.domain.analysis.dto.AiAnalyzeStatusResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "ai-analysis",
        url = "${ai.server.url}"
)
public interface AiAnalysisFeignClient {

    /**
     * 비동기 분석 시작 — jobId를 즉시 반환 (짧은 호출).
     */
    @PostMapping("/api/analyze/start")
    AiAnalyzeStartResponse startAnalyze(@RequestBody AiAnalyzeRequest request);

    /**
     * 잡 상태/결과 폴링 (짧은 호출).
     */
    @GetMapping("/api/analyze/status/{jobId}")
    AiAnalyzeStatusResponse getStatus(@PathVariable("jobId") String jobId);

    /**
     * 동기 분석 (deprecated) — 하위호환용. 긴 영상은 Cloudflare 524 위험.
     */
    @PostMapping("/api/analyze")
    AiAnalyzeResponse analyze(@RequestBody AiAnalyzeRequest request);
}
