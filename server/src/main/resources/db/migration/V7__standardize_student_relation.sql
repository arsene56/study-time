UPDATE members
SET relation_name = '学生'
WHERE relation_name = '孩子';

UPDATE activity_log
SET actor_relation = '学生'
WHERE actor_relation = '孩子';

UPDATE weekly_comments
SET actor_relation = '学生'
WHERE actor_relation = '孩子';
