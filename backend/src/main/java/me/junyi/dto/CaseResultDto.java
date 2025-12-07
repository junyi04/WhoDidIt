package me.junyi.dto;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CaseResultDto {
    private Long activeId;
    private Long caseId;
    private String caseTitle;
    private String caseDescription;
    private String culpritGuess;  // 사용자가 추리한 범인
    private String actualCulprit; // 실제 범인
    private String result; // 결과 ('감사' 또는 '부고')
    private String detectiveNickname; // 탐정의 닉네임
    private Integer difficulty; // 사건 난이도
}

