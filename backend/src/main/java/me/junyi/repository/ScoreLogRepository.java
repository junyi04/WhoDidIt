package me.junyi.repository;

import me.junyi.domain.ScoreLog;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ScoreLogRepository extends CrudRepository<ScoreLog, Long> {
    // 특정 사용자(userId)의 점수 변경 기록을 최신 순으로 조회
    List<ScoreLog> findAllByUserIdOrderByLogTimeDesc(Long userId);
}