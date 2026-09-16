-- 用 root 执行一次：建库 + 建应用专用账号。
-- 应用只需要 exam_system 一个库的权限，不必持有实例级 root，误操作也伤不到别的库。
-- 执行：mysql -uroot -p < sql/00_init_account.sql

-- 1. 建库（utf8mb4 才能存 emoji 和生僻字，utf8 不行）
CREATE DATABASE IF NOT EXISTS `exam_system`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

-- 2. 建应用专用账号。把 <你的密码> 换成自己的，尖括号一起去掉，
--    然后填到 application-local.yml 里（不是 application.yml，那个要提交）
CREATE USER IF NOT EXISTS 'exam_app'@'localhost' IDENTIFIED BY '<你的密码>';

-- 3. 只授权 exam_system 这一个库
GRANT ALL PRIVILEGES ON `exam_system`.* TO 'exam_app'@'localhost';

FLUSH PRIVILEGES;

-- 4. 验证：应能看到一行 exam_app | localhost
SELECT user, host FROM mysql.user WHERE user = 'exam_app';
