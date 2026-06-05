package com.pkx.domain.aimodel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 서버의 비동기 학습 잡 상태 조회 응답.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiTrainStatusResponse {

    private String jobId;
    private String status;        // PENDING | RUNNING | DONE | FAILED
    private Integer progress;     // 0~100
    private Double accuracy;
    private Integer sampleCount;
    private String error;
}
