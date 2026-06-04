package com.pkx.domain.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 서버의 비동기 분석 잡 상태 조회 응답.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalyzeStatusResponse {

    private String jobId;
    private String status;          // PENDING | RUNNING | DONE | FAILED
    private AiAnalyzeResponse result;
    private String error;
}
