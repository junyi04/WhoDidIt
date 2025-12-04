package me.junyi.service;

import me.junyi.domain.*;
import me.junyi.dto.*;
import me.junyi.repository.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map; // Map 추가
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class CaseService {

    private final CaseInfoRepository caseInfoRepository;
    private final CaseParticipationRepository participationRepository;
    private final CaseSuspectRepository caseSuspectRepository;
    private final OriginalEvidenceRepository originalEvidenceRepository;
    private final SubmittedEvidenceRepository submittedEvidenceRepository;
    private final ScoreLogRepository scoreLogRepository; // SCORE_LOG Repository
    private final AppUserRepository appUserRepository;
    private final JdbcTemplate jdbcTemplate; // Native Query를 위한 JdbcTemplate

    // 🚨 생성자 문법 수정 및 모든 필드 주입
    public CaseService(CaseInfoRepository caseInfoRepository, CaseParticipationRepository participationRepository,
                       OriginalEvidenceRepository originalEvidenceRepository, SubmittedEvidenceRepository submittedEvidenceRepository,
                       AppUserRepository appUserRepository, JdbcTemplate jdbcTemplate, ScoreLogRepository scoreLogRepository, CaseSuspectRepository caseSuspectRepository) {
        this.caseInfoRepository = caseInfoRepository;
        this.participationRepository = participationRepository;
        this.caseSuspectRepository = caseSuspectRepository;
        this.originalEvidenceRepository = originalEvidenceRepository;
        this.submittedEvidenceRepository = submittedEvidenceRepository;
        this.appUserRepository = appUserRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.scoreLogRepository = scoreLogRepository;
    } // 🚨 닫는 중괄호 추가!


    /** 1. 사건 목록 조회 (STATUS='등록') */
    public List<CaseInfo> getAvailableCases() {
        return caseInfoRepository.findAllByStatus("등록");
    }


    /** 2. 범인의 증거 조작 처리 (CRIMINAL_ID는 여기서 건드리지 않음) */
    @Transactional
    public CaseInfo handleCriminalAction(Long caseId, Long criminalId, String fakeEvidenceDescription) {

        // 🚨 1) fakeEvidenceDescription이 비었으면 절대 처리하지 않음
        if (fakeEvidenceDescription == null || fakeEvidenceDescription.isEmpty()) {
            throw new IllegalArgumentException("선택한 증거가 없습니다. 조작이 실행되지 않았습니다.");
        }

        // 2) 참여 정보 가져오기 (하지만 criminalId 저장 금지!)
        CaseParticipation participation = participationRepository.findByCaseId(caseId)
                .orElseThrow(() -> new IllegalArgumentException("참여 레코드가 없습니다."));


        // ⭐ criminalId를 사용하여 범인의 닉네임을 조회 (치환에 사용)
        AppUser criminal = appUserRepository.findById(criminalId)
                .orElseThrow(() -> new IllegalArgumentException("범인 사용자 정보를 찾을 수 없습니다."));
        String criminalNickname = criminal.getNickname();


        // 3) 제출된 증거 구성 (진짜 + 선택된 거짓)
        List<OriginalEvidence> trueEvidences =
                originalEvidenceRepository.findByCaseIdAndIsFakeCandidate(caseId, false);

        OriginalEvidence selectedFake =
                originalEvidenceRepository.findByCaseIdAndIsFakeCandidate(caseId, true).stream()
                        .filter(e -> e.getDescription().equals(fakeEvidenceDescription))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("선택한 거짓 증거를 찾을 수 없습니다."));

        // 기존 제출 증거 삭제
        submittedEvidenceRepository.deleteAll(submittedEvidenceRepository.findAllByCaseId(caseId));

        // 새 증거 목록 구성
        List<SubmittedEvidence> submittedList = trueEvidences.stream()
                .map(e -> new SubmittedEvidence(null, e.getCaseId(), e.getDescription(), true))
                .collect(Collectors.toList());


        // ⭐ 선택된 거짓 증거 설명의 플레이스홀더를 실제 닉네임으로 대체합니다.
        String processedFakeDescription = selectedFake.getDescription()
                .replace("{name}", criminalNickname);

        // ⭐ 대체된 설명으로 SubmittedEvidence를 생성합니다.
        submittedList.add(new SubmittedEvidence(
                null,
                selectedFake.getCaseId(),
                processedFakeDescription, // <--- 대체된 문자열 사용
                false
        ));

        submittedEvidenceRepository.saveAll(submittedList);

        // 4) 사건 상태 업데이트 → 조작 완료 시에만 변경
        CaseInfo caseInfo = caseInfoRepository.findById(caseId).orElseThrow();
        caseInfo.setStatus("조작");

        if (caseInfo.getTrueCriminalId() == null) {
            caseInfo.setTrueCriminalId(criminalId); // 범인을 true_criminal_id에 할당
            caseInfoRepository.save(caseInfo); // 변경사항 저장
        }
        return caseInfoRepository.save(caseInfo);
    }


    /** 3. 경찰의 탐정 배정 및 상태 변경 처리 (POLICE_ID, DETECTIVE_ID 등록, STATUS='배정') */
    @Transactional
    public CaseInfo handlePoliceAssignment(Long caseId, Long policeId, Long detectiveId) {
        // A. 참여 정보 업데이트 (경찰, 탐정 ID 등록 및 점수 부여)
        CaseParticipation participation = participationRepository.findByCaseId(caseId)
                .orElseThrow(() -> new IllegalArgumentException("참여 레코드를 찾을 수 없습니다."));

        participation.setPoliceId(policeId);
        participation.setDetectiveId(detectiveId);
        participationRepository.save(participation);

        // B. 경찰 점수 +2, 탐정 점수 +1 업데이트
        updateUserScore(policeId, 2, caseId, "경찰 배정 (초기 점수)");
        updateUserScore(detectiveId, 1, caseId, "탐정 배정 (초기 점수)");

        // C. 사건 상태 업데이트: '배정'
        CaseInfo caseInfo = caseInfoRepository.findById(caseId).orElseThrow();
        caseInfo.setStatus("배정");
        return caseInfoRepository.save(caseInfo);
    }

    // 헬퍼 메서드: 점수 업데이트 및 로그 기록 (SCORE_LOG 추가)
    private void updateUserScore(Long userId, int scoreChange, Long caseId, String reason) {
        AppUser user = appUserRepository.findById(userId).orElseThrow();
        user.setScore(user.getScore() + scoreChange);
        appUserRepository.save(user);

        // 🚨 SCORE_LOG 기록
        ScoreLog log = ScoreLog.builder()
                .userId(userId)
                .caseId(caseId)
                .scoreChange(scoreChange)
                .reason(reason)
                .build();
        scoreLogRepository.save(log);
    }

    /** 4. 탐정 - 배정된 사건 조회 (STATUS='배정') */
    public List<CaseDetectiveDto> getAssignedCasesByDetectiveId(Long detectiveId) {

        // 1) CaseParticipation 중 탐정 ID가 내가 맞는 참여 찾기
        List<CaseParticipation> participations =
                participationRepository.findAllByDetectiveId(detectiveId);

        return participations.stream()
                .map(p -> {
                    CaseInfo info = caseInfoRepository.findById(p.getCaseId()).orElse(null);
                    if (info == null) return null;

                    // STATUS='배정' 상태인 사건만 탐정에게 보여야 함
                    if (!"배정".equals(info.getStatus())) return null;

                    // 경찰/의뢰인 닉네임
                    String clientNickname = appUserRepository.findById(p.getClientId())
                            .map(AppUser::getNickname).orElse("미정");

                    String policeNickname = appUserRepository.findById(p.getPoliceId())
                            .map(AppUser::getNickname).orElse("미정");

                    // ⭐ 용의자 목록 조회
                    List<String> suspects = caseSuspectRepository.findAllByCaseId(info.getCaseId()).stream()
                            .map(CaseSuspect::getSuspectName)
                            .collect(Collectors.toList());

                    return CaseDetectiveDto.builder()
                            .activeId(p.getPartId())
                            .caseId(info.getCaseId())
                            .caseTitle(info.getTitle())
                            .caseDescription(info.getContent())
                            .difficulty(info.getDifficulty())
                            .clientNickname(clientNickname)
                            .policeNickname(policeNickname)
                            .status(info.getStatus())       // '배정'
                            .culpritGuess(null)            // 아직 추리 전
                            .result(null)                  // 결과 없음
                            .actualCulprit(null)           // 결과 없음
                            .suspects(suspects)            // ⭐ 용의자 목록 추가
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    /** 5. 탐정 - 완료된 사건 조회 (STATUS='결과 확인') */
    public List<CaseDetectiveDto> getCompletedCasesByDetectiveId(Long detectiveId) {

        List<CaseParticipation> participations =
                participationRepository.findAllByDetectiveId(detectiveId);

        return participations.stream()
                .map(p -> {
                    CaseInfo info = caseInfoRepository.findById(p.getCaseId()).orElse(null);
                    if (info == null) return null;

                    // 🚨 STATUS = '결과 확인'만
                    if (!"결과 확인".equals(info.getStatus())) return null;

                    String clientNickname = appUserRepository.findById(p.getClientId())
                            .map(AppUser::getNickname).orElse("미정");

                    String policeNickname = appUserRepository.findById(p.getPoliceId())
                            .map(AppUser::getNickname).orElse("미정");


                    // ⭐ 용의자 목록 조회
                    List<String> suspects = caseSuspectRepository.findAllByCaseId(info.getCaseId()).stream()
                            .map(CaseSuspect::getSuspectName)
                            .collect(Collectors.toList());

                    return CaseDetectiveDto.builder()
                            .activeId(p.getPartId())
                            .caseId(info.getCaseId())
                            .caseTitle(info.getTitle())
                            .caseDescription(info.getContent())
                            .difficulty(info.getDifficulty())
                            .clientNickname(clientNickname)
                            .policeNickname(policeNickname)
                            .status(info.getStatus())
                            .culpritGuess(p.getDetectiveGuessId() != null ?
                                    appUserRepository.findById(p.getDetectiveGuessId())
                                            .map(AppUser::getNickname).orElse("미정")
                                    : null)
                            .result(p.getIsSolved() != null ?
                                    (p.getIsSolved() ? "감사" : "부고") : null)
                            .actualCulprit(appUserRepository.findById(info.getTrueCriminalId())
                                    .map(AppUser::getNickname).orElse("미정"))
                            .suspects(suspects) // ⭐ 용의자 목록 추가
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    /** 6. 의뢰인 - 의뢰한 사건 조회 */
    public List<CaseClientDto> getCasesByClientId(Long clientId) {
        // 1. clientId로 CaseParticipation 목록 조회
        List<CaseParticipation> participations = participationRepository.findAllByClientId(clientId);

        // 2. 각 participation의 caseId를 사용하여 CaseInfo 조회 및 DTO 변환
        return participations.stream()
                .map(p -> {
                    Optional<CaseInfo> caseInfoOpt = caseInfoRepository.findById(p.getCaseId());
                    return caseInfoOpt.map(info -> {
                        // CaseInfo와 CaseParticipation의 데이터를 CaseClientDto로 조합
                        String status = info.getStatus();
                        String result = null; // CaseClientDto에 따라 CaseResult 도메인이 있다면 추가 조회가 필요함

                        if ("결과 확인".equals(status)) {
                            result = p.getIsSolved() != null ? (p.getIsSolved() ? "감사" : "부고") : "미정";
                        }

                        // 탐정 닉네임 조회 (DetectiveId가 있는 경우)
                        String detectiveNickname = (p.getDetectiveId() != null) ?
                                appUserRepository.findById(p.getDetectiveId()).map(AppUser::getNickname).orElse("미배정") : "미배정";

                        return CaseClientDto.builder()
                                .caseId(info.getCaseId())
                                .activeId(p.getPartId()) // 활성화된 참여 정보 ID (프론트엔드 key)
                                .caseTitle(info.getTitle())
                                .caseDescription(info.getContent())
                                .difficulty(info.getDifficulty())
                                .detectiveNickname(detectiveNickname)
                                .status(status)
                                .result(result)
                                .build();
                    }).orElse(null);
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** 7. 경찰 - 탐정 배정 대기 중인 사건 조회 (STATUS='조작') */

    public List<PendingCaseDto> getPendingCasesForPolice(Long policeId) {

        Iterable<CaseParticipation> iterable = participationRepository.findAll();

        List<CaseParticipation> participations =
                StreamSupport.stream(iterable.spliterator(), false)
                        .filter(p -> {
                            CaseInfo info = caseInfoRepository.findById(p.getCaseId()).orElse(null);
                            if (info == null) return false;

                            boolean isPending = "조작".equals(info.getStatus()) || "접수중".equals(info.getStatus());
                            boolean isMineOrUnassigned =
                                    p.getPoliceId() == null || p.getPoliceId().equals(policeId);

                            return isPending && isMineOrUnassigned;
                        })
                        .toList();

        return participations.stream()
                .map(p -> {
                    CaseInfo info = caseInfoRepository.findById(p.getCaseId()).orElse(null);
                    if (info == null) return null;

                    return PendingCaseDto.builder()
                            .activeId(p.getPartId())
                            .caseId(info.getCaseId())
                            .caseTitle(info.getTitle())
                            .caseDescription(info.getContent())
                            .difficulty(info.getDifficulty())
                            .status(info.getStatus())
                            .clientNickname(
                                    appUserRepository.findById(p.getClientId())
                                            .map(AppUser::getNickname)
                                            .orElse("미정")
                            )
                            .culpritNickname(
                                    appUserRepository.findById(p.getCriminalId())
                                            .map(AppUser::getNickname)
                                            .orElse("미정")
                            )
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
    }




    /** 8. 범인 - 조작 참여 가능 사건 조회 (STATUS='등록') */
    public List<AvailableCaseDto> getAvailableCasesForCulprit() {

        String sql = """
        SELECT 
            cp.part_id AS active_id,
            c.case_id,
            c.title,
            c.content,
            c.difficulty,
            u.nickname AS client_nickname
        FROM case_participation cp
        JOIN case_info c ON cp.case_id = c.case_id
        JOIN app_user u ON cp.client_id = u.user_id
        WHERE c.status = '등록'
        AND cp.criminal_id IS NULL
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) ->
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


    /** 9. 범인 - 참여한 사건 조회 */
    public List<MyCaseDto> getCulpritMyCases(Long culpritId) {

        List<CaseParticipation> participations =
                participationRepository.findAllByCriminalId(culpritId);

        return participations.stream()
                .map(p -> {
                    CaseInfo info = caseInfoRepository.findById(p.getCaseId()).orElse(null);
                    if (info == null) return null;

                    boolean fakeSelected = "조작".equals(info.getStatus());

                    return MyCaseDto.builder()
                            .caseId(info.getCaseId())
                            .activeId(p.getPartId())
                            .caseTitle(info.getTitle())
                            .caseDescription(info.getContent())
                            .clientNickname(
                                    appUserRepository.findById(p.getClientId())
                                            .map(AppUser::getNickname)
                                            .orElse("미정")
                            )
                            .difficulty(info.getDifficulty())
                            .status(info.getStatus())
                            .fakeEvidenceSelected(fakeSelected)
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    /** 10. 의뢰인 - 사건 의뢰 처리 (CaseParticipation 생성) */
    @Transactional
    public CaseInfo startCaseByClient(Long caseId, Long clientId) {
        // 1. CaseInfo 상태 확인 및 유효성 검사 (STATUS='등록' 상태의 사건만 의뢰 가능)
        CaseInfo caseInfo = caseInfoRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("사건을 찾을 수 없습니다."));

        if (!"등록".equals(caseInfo.getStatus())) {
            throw new IllegalStateException("이미 의뢰가 진행 중이거나 마감된 사건입니다.");
        }

        // 2. CaseParticipation 생성 및 저장 (clientId만 설정)
        CaseParticipation newParticipation = CaseParticipation.builder()
                .caseId(caseId)
                .clientId(clientId)
                .build();
        participationRepository.save(newParticipation);

        // 3. (옵션) 의뢰 시점에서 CaseInfo의 상태를 변경할 수도 있지만,
        //    대부분의 경우 '등록' 상태를 유지하고 범인/경찰 액션 시점에 상태가 변경됩니다.
        //    여기서는 상태 변경 없이 CaseInfo를 반환합니다.
        return caseInfo;
    }

    /** 11. 범인 - 사건 참여 처리 (CRIMINAL_ID 등록 및 점수 +1) */
    @Transactional
    public CaseInfo handleJoinCulprit(Long caseId, Long culpritId) {
        // 1. 참여 정보 업데이트 (CRIMINAL_ID 등록 및 점수 +1)
        CaseParticipation participation = participationRepository.findByCaseId(caseId)
                .orElseThrow(() -> new IllegalArgumentException("참여 레코드를 찾을 수 없습니다."));

        // 🚨 이미 범인이 지정된 경우 방지
        if (participation.getCriminalId() != null) {
            throw new IllegalStateException("이미 범인이 참여한 사건입니다.");
        }

        participation.setCriminalId(culpritId);
        participationRepository.save(participation);

        // 2. 범인 점수 +1 업데이트 및 로그 기록 (재사용 가능한 updateUserScore 헬퍼 메서드 사용)
        updateUserScore(culpritId, 1, caseId, "범인 참여 (초기 점수)");

        // 3. CaseInfo 상태 확인 (STATUS='등록' 상태를 유지. 범인 조작 후 '조작'으로 변경됨)
        CaseInfo caseInfo = caseInfoRepository.findById(caseId).orElseThrow();

        // 상태는 아직 '등록'을 유지하며, 증거 조작 완료 후 '조작'으로 변경됩니다.
        // caseInfo.setStatus("조작"); // 🚨 조작 완료 시점에 변경되므로 여기서는 변경하지 않습니다.

        return caseInfo;
    }

    /** 12. 범인 - 증거 조작용 사건 상세 및 증거 목록 조회 */
    @Transactional(readOnly = true)
    public Map<String, Object> getEvidenceDetailsForFabrication(Long caseId) {
        // A. CaseInfo 조회
        CaseInfo caseInfo = caseInfoRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("사건을 찾을 수 없습니다."));

        // B. OriginalEvidence 전체 목록 조회 (진짜 + 거짓 후보 모두 포함)
        List<OriginalEvidence> allEvidences = originalEvidenceRepository.findAllByCaseId(caseId);

        // C. 결과를 Map으로 구성하여 반환 (프론트엔드 기대 구조와 일치)
        return Map.of(
                "caseTitle", caseInfo.getTitle(),
                "caseDescription", caseInfo.getContent(),
                "originalEvidences", allEvidences
        );
    }

    @Transactional
    public CaseInfo handlePoliceAccept(Long caseId, Long policeId) {

        CaseParticipation participation = participationRepository.findByCaseId(caseId)
                .orElseThrow(() -> new IllegalArgumentException("참여 레코드를 찾을 수 없습니다."));

        // 경찰 ID 등록
        participation.setPoliceId(policeId);
        participationRepository.save(participation);

        // 상태 변경: 조작 → 접수중
        CaseInfo caseInfo = caseInfoRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("사건을 찾을 수 없습니다."));

        caseInfo.setStatus("접수중");
        return caseInfoRepository.save(caseInfo);
    }

    public List<PendingCaseDto> getPendingCasesForPoliceFull() {

        // 🔥 1) STATUS = '조작' 인 사건만 가져오기
        List<CaseInfo> caseInfos = caseInfoRepository.findAllByStatus("조작");

        return caseInfos.stream().map(info -> {

            CaseParticipation p = participationRepository.findByCaseId(info.getCaseId())
                    .orElse(null);

            String clientNickname = "알 수 없음";
            if (p != null && p.getClientId() != null) {
                clientNickname = appUserRepository.findById(p.getClientId())
                        .map(AppUser::getNickname)
                        .orElse("알 수 없음");
            }

            String culpritNickname = "미지정";
            if (p != null && p.getCriminalId() != null) {
                culpritNickname = appUserRepository.findById(p.getCriminalId())
                        .map(AppUser::getNickname)
                        .orElse("미지정");
            }

            return PendingCaseDto.builder()
                    .activeId(p != null ? p.getPartId() : null)
                    .caseId(info.getCaseId())
                    .caseTitle(info.getTitle())
                    .caseDescription(info.getContent())
                    .difficulty(info.getDifficulty())
                    .clientNickname(clientNickname)
                    .culpritNickname(culpritNickname)
                    .status(info.getStatus())
                    .build();

        }).toList();
    }

    public List<PendingCaseDto> getMyPoliceCases(Long policeId) {

        List<CaseParticipation> participations =
                StreamSupport.stream(participationRepository.findAll().spliterator(), false)
                        .filter(p -> policeId.equals(p.getPoliceId())) // 내가 맡은 사건만
                        .toList();

        return participations.stream()
                .map(p -> {
                    CaseInfo info = caseInfoRepository.findById(p.getCaseId()).orElse(null);
                    if (info == null) return null;

                    return PendingCaseDto.builder()
                            .activeId(p.getPartId())
                            .caseId(info.getCaseId())
                            .caseTitle(info.getTitle())
                            .caseDescription(info.getContent())
                            .difficulty(info.getDifficulty())
                            .status(info.getStatus())
                            .clientNickname(
                                    appUserRepository.findById(p.getClientId())
                                            .map(AppUser::getNickname)
                                            .orElse("미정")
                            )
                            .culpritNickname(
                                    appUserRepository.findById(p.getCriminalId())
                                            .map(AppUser::getNickname)
                                            .orElse("미정")
                            )
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
    }

    // 증거를 가져오는 서비스 메서드
    public List<SubmittedEvidence> getSubmittedEvidenceForCase(Long caseId) {
        // SubmittedEvidenceRepository에서 해당 사건에 제출된 증거들을 가져옴
        return submittedEvidenceRepository.findAllByCaseId(caseId);
    }

    public String getCulpritNameByCaseId(Long caseId) {
        // caseId로 사건 참여 조회
        Optional<CaseParticipation> participationOpt = participationRepository.findByCaseId(caseId);

        if (participationOpt.isPresent()) {
            CaseParticipation participation = participationOpt.get();

            // 범인 ID가 있을 경우
            if (participation.getCriminalId() != null) {
                // 범인 이름을 가져오기
                AppUser criminal = appUserRepository.findById(participation.getCriminalId())
                        .orElseThrow(() -> new RuntimeException("범인을 찾을 수 없습니다."));
                return criminal.getNickname();
            }
        }

        return "범인 정보 없음"; // 범인 정보가 없을 경우
    }


    public CaseInfo getCaseInfoById(Long caseId) {
        return caseInfoRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("사건을 찾을 수 없습니다."));
    }


    /**
     * ⭐ 15. 탐정의 추리를 처리하고 사건 해결 여부를 판단하여 점수를 정산합니다.
     */
    @Transactional
    public Map<String, Object> handleDetectiveGuessAndCheckResult(Long caseId, Long detectiveId, String culpritGuessNickname) {
        // 필요한 정보 조회
        CaseParticipation participation = participationRepository.findByCaseId(caseId)
                .orElseThrow(() -> new IllegalArgumentException("참여 레코드를 찾을 수 없습니다."));
        CaseInfo caseInfo = caseInfoRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("사건 정보를 찾을 수 없습니다."));

        // 탐정이 추측한 닉네임을 user_id로 변환
        AppUser guessedUser = appUserRepository.findByNickname(culpritGuessNickname)
                .orElseThrow(() -> new IllegalArgumentException("추측한 용의자 닉네임을 찾을 수 없습니다."));

        Long detectiveGuessId = guessedUser.getUserId();

        // 사건 해결 여부 판단
        boolean isSolved = false;
        if (caseInfo.getTrueCriminalId() != null) {
            isSolved = caseInfo.getTrueCriminalId().equals(detectiveGuessId);
        }

        // CaseParticipation 업데이트
        participation.setDetectiveGuessId(detectiveGuessId);
        participation.setIsSolved(isSolved);
        participationRepository.save(participation);

        // 점수 계산 및 부여
        int detectiveScoreChange = 0;
        int criminalScoreChange = 0;
        int baseScore = caseInfo.getDifficulty() * 10;

        if (isSolved) {
            detectiveScoreChange = baseScore;
            criminalScoreChange = 0;
        } else {
            criminalScoreChange = baseScore;
            detectiveScoreChange = 0;
        }

        // 점수 업데이트 및 로그 기록
        updateUserScore(detectiveId, detectiveScoreChange, caseId,
                isSolved ? "탐정: 사건 해결 성공" : "탐정: 사건 해결 실패");

        if (participation.getCriminalId() != null) {
            updateUserScore(participation.getCriminalId(), criminalScoreChange, caseId,
                    isSolved ? "범인: 사건 해결됨" : "범인: 탐정 추리 실패");
        }

        // CaseInfo 상태 업데이트
        caseInfo.setStatus("결과 확인");
        caseInfoRepository.save(caseInfo);

        // 실제 범인 닉네임 조회
        String actualCulpritNickname = appUserRepository.findById(caseInfo.getTrueCriminalId())
                .map(AppUser::getNickname).orElse("알 수 없음");

        return Map.of(
                "isSolved", isSolved,
                "detectiveScoreChange", detectiveScoreChange,
                "criminalScoreChange", criminalScoreChange,
                "actualCulpritNickname", actualCulpritNickname,
                "newStatus", "결과 확인"
        );
    }

}