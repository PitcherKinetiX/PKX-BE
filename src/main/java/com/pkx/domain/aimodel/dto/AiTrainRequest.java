package com.pkx.domain.aimodel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 서버(/api/train/start)로 보내는 개인화 모델 학습 요청.
 * 필드명은 FastAPI pydantic TrainRequest 와 1:1 매칭되어야 한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTrainRequest {

    private Long userId;
    private List<String> videoUrls;      // 학습 영상 Signed GET URL 목록
    private String baseModelUrl;         // 증분 학습 시 기존 사용자 모델 GET URL, 전체 재학습이면 null
    private String modelUploadUrl;       // 새 pth 업로드용 Signed PUT URL
    private String statsUploadUrl;       // 새 stats(pkl) 업로드용 Signed PUT URL
    private boolean incremental;
}
