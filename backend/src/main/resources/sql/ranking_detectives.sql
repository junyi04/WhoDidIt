SELECT
    u.user_id AS "userId",
    u.nickname AS "nickname",
    u.score AS "score",
    (SELECT COUNT(p.part_id)
     FROM case_participation p
     WHERE p.detective_id = u.user_id) AS "totalCases",
    COALESCE(
        (SELECT AVG(CASE WHEN p.is_solved = TRUE THEN 1 ELSE 0 END) * 100
         FROM case_participation p
         WHERE p.detective_id = u.user_id), 0
    ) AS "successRate"
FROM app_user u
WHERE u.role = '탐정'
ORDER BY u.score DESC;
