-- 迁移脚本：sys_user 去掉逻辑删除列。只对已建过库的环境执行一次，
-- 全新库直接跑 01_schema.sql 就行，原因见那里 sys_user 的建表注释。

USE `exam_system`;

-- 顺序不能反：先删列的话 deleted 条件就没了，被标记删除的行会当成正常用户冒出来
DELETE FROM `sys_user` WHERE `deleted` = 1;

ALTER TABLE `sys_user` DROP COLUMN `deleted`;

-- 验证：列里应该不再有 deleted
-- SHOW COLUMNS FROM `sys_user`;
