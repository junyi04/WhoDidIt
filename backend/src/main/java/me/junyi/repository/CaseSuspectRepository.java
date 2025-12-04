package me.junyi.repository;

import me.junyi.domain.CaseSuspect;
import org.springframework.data.repository.CrudRepository;
import java.util.List;

// 6. 사건 용의자 리포지토리
public interface CaseSuspectRepository extends CrudRepository<CaseSuspect, Long> {

    // 특정 사건(caseId)의 모든 용의자 목록을 조회할 때 사용
    List<CaseSuspect> findAllByCaseId(Long caseId);
}