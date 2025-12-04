package me.junyi.controller;

import me.junyi.domain.CaseInfo;
import me.junyi.domain.OriginalEvidence;
import me.junyi.domain.SubmittedEvidence;
import me.junyi.dto.*;
import me.junyi.service.CaseService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cases")
public class CaseController {

    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    // 1. 등록 상태 사건 목록
    @GetMapping("/available")
    public List<CaseInfo> getAvailableCases() {
        return caseService.getAvailableCases();
    }

    // 2. 범인 - 내가 참여한 사건 목록 (MyCaseDto)
    @GetMapping("/culprit/{userId}")
    public List<MyCaseDto> getCulpritMyCases(@PathVariable Long userId) {
        return caseService.getCulpritMyCases(userId);
    }

    // 3. 범인 - 증거 조작 API
    @PostMapping("/fabricate")
    public ResponseEntity<?> fabricateEvidence(@RequestBody Map<String, Object> request) {

        Long caseId = ((Number) request.get("caseId")).longValue();
        Long criminalId = ((Number) request.get("criminalId")).longValue();

        List<String> fakeEvidenceList = (List<String>) request.get("fakeEvidence");
        if (fakeEvidenceList == null || fakeEvidenceList.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "증거가 선택되지 않았습니다."));
        }

        String fakeEvidenceDescription = fakeEvidenceList.get(0);

        try {
            CaseInfo updatedCase = caseService.handleCriminalAction(
                    caseId, criminalId, fakeEvidenceDescription);

            return ResponseEntity.ok(Map.of(
                    "message", "증거 조작 성공",
                    "newStatus", updatedCase.getStatus()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // 4. 경찰 - 탐정 배정
    @PostMapping("/assign")
    public ResponseEntity<?> assignDetective(@RequestBody Map<String, Long> request) {
        Long caseId = request.get("caseId");
        Long policeId = request.get("policeId");
        Long detectiveId = request.get("detectiveId");

        try {
            CaseInfo updatedCase = caseService.handlePoliceAssignment(caseId, policeId, detectiveId);
            return ResponseEntity.ok(Map.of("newStatus", updatedCase.getStatus()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // 5. 경찰 - 배정 대기 사건 조회
    @GetMapping("/police/pending/{policeId}")
    public List<PendingCaseDto> getPendingCases(@PathVariable Long policeId) {
        return caseService.getPendingCasesForPoliceFull();
    }



    // 6. 의뢰인 - 참여 사건 조회
    @GetMapping("/client/{userId}")
    public List<CaseClientDto> getCasesByClient(@PathVariable Long userId) {
        return caseService.getCasesByClientId(userId);
    }

    // 7. 범인 - 참여 가능한 사건 목록
    @GetMapping("/culprit/available")
    public List<AvailableCaseDto> getAvailableCasesForCulprit() {
        return caseService.getAvailableCasesForCulprit();
    }


    // 8. 증거 조작 모달용 상세 조회
    @GetMapping("/culprit/fabricate/details/{caseId}")
    public ResponseEntity<?> getFabricationDetails(@PathVariable Long caseId) {
        try {
            Map<String, Object> details = caseService.getEvidenceDetailsForFabrication(caseId);
            return ResponseEntity.ok(details);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // 9
    @PostMapping("/culprit/join")
    public ResponseEntity<?> joinCaseAsCulprit(@RequestBody Map<String, Long> request) {
        Long caseId = request.get("caseId");
        Long culpritId = request.get("culpritId");

        if (caseId == null || culpritId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Case ID와 Culprit ID는 필수입니다."));
        }

        try {
            caseService.handleJoinCulprit(caseId, culpritId);
            return ResponseEntity.ok(Map.of("message", "범인으로 사건에 참여했습니다."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "참여 중 DB 오류: " + e.getMessage()));
        }
    }

    // 10
    @PostMapping("/police/accept")
    public ResponseEntity<?> acceptCase(@RequestBody Map<String, Long> request) {
        Long caseId = request.get("caseId");
        Long policeId = request.get("policeId");

        if (caseId == null || policeId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "caseId와 policeId는 필수입니다."));
        }

        try {
            CaseInfo updatedCase = caseService.handlePoliceAccept(caseId, policeId);
            return ResponseEntity.ok(Map.of("newStatus", updatedCase.getStatus()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
    
    // 11. 내가 맡은 사건
    @GetMapping("/police/my/{policeId}")
    public List<PendingCaseDto> getMyCases(@PathVariable Long policeId) {
        return caseService.getMyPoliceCases(policeId);
    }


    // 12
    @GetMapping("/detective/{userId}")
    public List<CaseDetectiveDto> getAssignedCasesForDetective(@PathVariable Long userId) {
        return caseService.getAssignedCasesByDetectiveId(userId);
    }

    // 13
    @GetMapping("/detective/result/{userId}")
    public List<CaseDetectiveDto> getCompletedCasesForDetective(@PathVariable Long userId) {
        return caseService.getCompletedCasesByDetectiveId(userId);
    }

    // 14. 사건 세부 정보 조회
    @GetMapping("/{caseId}/details")
    public ResponseEntity<Map<String, Object>> getCaseDetails(@PathVariable Long caseId) {
        try {
            // 사건 정보 가져오기
            CaseInfo caseInfo = caseService.getCaseInfoById(caseId);

            // 제출된 증거 목록 가져오기
            List<SubmittedEvidence> evidenceList = caseService.getSubmittedEvidenceForCase(caseId);

            // 범인 이름 가져오기
            String culpritName = caseService.getCulpritNameByCaseId(caseId);

            // 반환할 데이터
            Map<String, Object> response = Map.of(
                    "culpritName", culpritName,
                    "evidence", evidenceList
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "사건 세부 정보를 불러오는 데 실패했습니다."));
        }
    }

    // ⭐ 15. 탐정 - 추리 제출 및 결과 처리 API
    @PostMapping("/detective/guess/{caseId}")
    public ResponseEntity<?> submitDetectiveGuess(@PathVariable Long caseId, @RequestBody Map<String, Object> request) {

        Long detectiveId = ((Number) request.get("detectiveId")).longValue();
        String culpritGuessNickname = (String) request.get("culpritGuessNickname"); // 프론트에서 받은 추측 닉네임

        if (culpritGuessNickname == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "추리할 용의자 닉네임이 필요합니다."));
        }

        try {
            // CaseService에 새로운 메서드를 호출하여 추리 제출과 결과 처리를 동시에 수행합니다.
            Map<String, Object> result = caseService.handleDetectiveGuessAndCheckResult(
                    caseId,
                    detectiveId,
                    culpritGuessNickname
            );

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "추리 처리 중 오류: " + e.getMessage()));
        }
    }
}
