package com.pkx.domain.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalyzeRequest {

    private String fileId;
    private Long userId;
    private Long analysisId;
    private String modelType;
    private String videoUrl;
}
