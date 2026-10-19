-- =============================================================
-- MASTER SEED SCRIPT (seed_all.sql)
-- Chạy toàn bộ dữ liệu mẫu cho hệ thống theo schema migration_v2
-- Quy mô: 100.000 User, 10.000 Nhóm, 1 Nhóm Siêu Lớn, Giao dịch cá nhân & Giao dịch nhóm
-- Khuyên dùng: Chạy trên DataGrip, DBeaver hoặc psql CLI
-- =============================================================

\ir 01_seed_users_and_auth.sql
\ir 02_seed_wallets_and_personal_txns.sql
\ir 03_seed_recurring_transactions.sql
\ir 04_seed_groups_and_members.sql
\ir 05_seed_group_transactions.sql
\ir 06_rebuild_balances_and_analyze.sql
