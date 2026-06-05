package com.pkx.domain.aimodel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 서버의 비동기 학습 시작 응답.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiTrainStartResponse {

    private String jobId;
    private String status;   // 항상 "PENDING"
}
