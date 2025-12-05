-- 랭킹 조회 시, 사건 참여 횟수가 1건 이상일 때만 나타나게 설정
SELECT
    u.user_id AS "userId",
    u.nickname AS "nickname",
    u.score AS "score",
    COUNT(p.part_id) AS "totalCases",
    COALESCE(
        AVG(CASE WHEN p.is_solved = TRUE THEN 1 ELSE 0 END) * 100,
        0
    ) AS "successRate"
FROM app_user u
LEFT JOIN case_participation p
    ON u.user_id = p.detective_id
WHERE u.role = '탐정'
GROUP BY u.user_id, u.nickname, u.score
ORDER BY u.score DESC;
