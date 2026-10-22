CREATE UNIQUE INDEX uq_wallets_user_name_type
ON wallets (user_id, lower(name), type)
WHERE NOT is_deleted;