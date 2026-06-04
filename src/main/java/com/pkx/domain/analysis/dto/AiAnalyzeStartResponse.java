package com.pkx.domain.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 서버의 비동기 분석 시작 응답.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalyzeStartResponse {

    private String jobId;
    private String status;   // 항상 "PENDING"
}
