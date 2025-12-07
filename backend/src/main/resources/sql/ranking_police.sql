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
    ON u.user_id = p.police_id
WHERE u.role = '경찰'
GROUP BY u.user_id, u.nickname, u.score
HAVING COUNT(p.part_id) > 0  -- 사건 참여 횟수가 1건 이상인 경우만
ORDER BY u.score DESC;
