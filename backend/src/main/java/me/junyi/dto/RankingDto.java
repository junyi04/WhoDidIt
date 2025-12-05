package me.junyi.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankingDto {
    private Long userId;

    private String nickname;
    private String role;
    private Long score;
    private Long totalCases;
    private Double successRate;
}
