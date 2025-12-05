package me.junyi.repository;

import jakarta.annotation.PostConstruct;
import me.junyi.dto.AvailableCaseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StreamUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Repository
public class CaseRepository {

    private final JdbcTemplate jdbcTemplate;

    // 1. Spring의 @Value를 사용하여 파일을 Resource 객체로 주입받습니다.
    @Value("classpath:sql/available_cases.sql")
    private Resource availableCasesSqlFile;

    // 2. 파일에서 읽어온 SQL 쿼리를 저장할 String 변수
    private String availableCasesSql;

    public CaseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 3. 스프링 빈이 생성된 후 자동으로 이 메소드를 호출하여 파일을 읽습니다.
    @PostConstruct
    public void loadSqlFile() {
        try {
            // Resource의 InputStream을 읽어와 UTF-8 인코딩으로 String에 저장
            this.availableCasesSql = StreamUtils.copyToString(
                    availableCasesSqlFile.getInputStream(),
                    StandardCharsets.UTF_8
            ).trim();
        } catch (IOException e) {
            // 파일을 읽는 데 실패하면 예외 처리
            throw new RuntimeException("SQL 파일을 로드하는 데 실패했습니다: " + availableCasesSqlFile.getFilename(), e);
        }
    }

    // 4. 비즈니스 로직 메소드에서는 저장된 String 변수를 사용합니다.
    public List<AvailableCaseDto> getAvailableCasesForCulprit() {

        return jdbcTemplate.query(availableCasesSql, (rs, rowNum) ->
                AvailableCaseDto.builder()
                        .activeId(rs.getLong("active_id"))
                        .caseId(rs.getLong("case_id"))
                        .caseTitle(rs.getString("title"))
                        .caseDescription(rs.getString("content"))
                        .difficulty(rs.getInt("difficulty"))
                        .clientNickname(rs.getString("client_nickname"))
                        .build()
        );
    }
}