package com.pkx.domain.analysis.client;

import com.pkx.domain.analysis.dto.AiAnalyzeRequest;
import com.pkx.domain.analysis.dto.AiAnalyzeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "ai-analysis",
        url = "${ai.server.url}"
)
public interface AiAnalysisFeignClient {

    @PostMapping("/api/analyze")
    AiAnalyzeResponse analyze(@RequestBody AiAnalyzeRequest request);
}
