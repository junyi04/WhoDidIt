package me.junyi.controller;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ranking")
public class RankingController {
    private final JdbcTemplate jdbcTemplate;

    @Value("classpath:sql/ranking_detectives.sql")
    private Resource rankingDetectivesSqlFile;
    private String rankingDetectivesSql;

    public RankingController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void loadSqlFile() {
        try {
            this.rankingDetectivesSql = StreamUtils.copyToString(
                    rankingDetectivesSqlFile.getInputStream(),
                    StandardCharsets.UTF_8
            ).trim();
        } catch (IOException e) {
            throw new RuntimeException("랭킹 SQL 파일을 로드하는 데 실패했습니다.", e);
        }
    }

    // 탐정 목록 조회 (탐정 랭킹)
    @GetMapping("/detectives")
    public List<Map<String, Object>> getDetectives() {

        String sql = this.rankingDetectivesSql;

        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);

        // 순위 계산
        int rank = 1;
        for (Map<String, Object> row : list) {
            row.put("rank", rank++);
        }

        return list;
    }
}
