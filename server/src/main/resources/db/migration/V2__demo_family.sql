INSERT INTO families (id, name) VALUES ('demo-family', '林家的作业时光');

INSERT INTO members (id, family_id, display_name, relation_name, role) VALUES
('demo-parent-mom', 'demo-family', '林妈妈', '妈妈', 'PARENT'),
('demo-parent-dad', 'demo-family', '林爸爸', '爸爸', 'PARENT'),
('demo-child-member-xiaoman', 'demo-family', '林小满', '孩子', 'CHILD'),
('demo-child-member-keke', 'demo-family', '林可可', '孩子', 'CHILD');

INSERT INTO children (id, family_id, name, grade, bedtime, stars) VALUES
('demo-child-xiaoman', 'demo-family', '林小满', 3, '21:30:00', 86),
('demo-child-keke', 'demo-family', '林可可', 1, '20:45:00', 42);
