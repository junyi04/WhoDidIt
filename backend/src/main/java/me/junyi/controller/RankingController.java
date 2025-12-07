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

    @Value("classpath:sql/ranking_culprits.sql")
    private Resource rankingCulpritsSqlFile;
    private String rankingCulpritsSql;

    @Value("classpath:sql/ranking_clients.sql")
    private Resource rankingClientsSqlFile;
    private String rankingClientsSql;

    @Value("classpath:sql/ranking_police.sql")
    private Resource rankingPoliceSqlFile;
    private String rankingPoliceSql;

    public RankingController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void loadSqlFiles() {
        try {
            this.rankingDetectivesSql = StreamUtils.copyToString(
                    rankingDetectivesSqlFile.getInputStream(),
                    StandardCharsets.UTF_8
            ).trim();

            this.rankingCulpritsSql = StreamUtils.copyToString(
                    rankingCulpritsSqlFile.getInputStream(),
                    StandardCharsets.UTF_8
            ).trim();

            this.rankingClientsSql = StreamUtils.copyToString(
                    rankingClientsSqlFile.getInputStream(),
                    StandardCharsets.UTF_8
            ).trim();

            this.rankingPoliceSql = StreamUtils.copyToString(
                    rankingPoliceSqlFile.getInputStream(),
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

    // 범인 목록 조회 (범인 랭킹)
    @GetMapping("/culprits")
    public List<Map<String, Object>> getCulprits() {
        String sql = this.rankingCulpritsSql;
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);

        // 순위 계산
        int rank = 1;
        for (Map<String, Object> row : list) {
            row.put("rank", rank++);
        }

        return list;
    }

    // 의뢰인 목록 조회 (의뢰인 랭킹)
    @GetMapping("/clients")
    public List<Map<String, Object>> getClients() {
        String sql = this.rankingClientsSql;
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);

        // 순위 계산
        int rank = 1;
        for (Map<String, Object> row : list) {
            row.put("rank", rank++);
        }

        return list;
    }

    // 경찰 목록 조회 (경찰 랭킹)
    @GetMapping("/police")
    public List<Map<String, Object>> getPolice() {
        String sql = this.rankingPoliceSql;
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);

        // 순위 계산
        int rank = 1;
        for (Map<String, Object> row : list) {
            row.put("rank", rank++);
        }

        return list;
    }
}
