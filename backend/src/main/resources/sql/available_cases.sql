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
AND cp.criminal_id IS NULL;