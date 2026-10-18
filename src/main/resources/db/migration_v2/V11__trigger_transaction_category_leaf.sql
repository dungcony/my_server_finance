-- =============================================================
-- Trigger danh mục giao dịch: chỉ gắn giao dịch vào danh mục lá chưa xoá
-- =============================================================

-- -------------------------------------------------------------
-- Java đã kiểm tra trước khi ghi, trigger chặn nốt trường hợp admin đổi cây danh mục cùng lúc.
-- FOR SHARE khoá dòng danh mục: admin đang sửa thì chờ admin commit rồi đọc giá trị mới,
-- trigger khoá trước thì admin phải chờ giao dịch commit xong.
-- Hibernate ghi lại mọi cột mỗi lần save nên UPDATE OF category_code chạy cả khi không đổi danh mục,
-- function so OLD/NEW để bỏ qua các lần đó.
-- Lỗi dùng check_violation để Spring dịch thành DataIntegrityViolationException (xem TriggerProperty).
-- -------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_transactions_category_leaf()
RETURNS TRIGGER AS $$
DECLARE
    v_is_leaf    BOOLEAN;
    v_is_deleted BOOLEAN;
BEGIN
    -- sửa giao dịch mà không đổi danh mục thì bỏ qua
    IF TG_OP = 'UPDATE' AND NEW.category_code IS NOT DISTINCT FROM OLD.category_code THEN
        RETURN NEW;
    END IF;

    SELECT is_leaf, is_deleted INTO v_is_leaf, v_is_deleted
    FROM categories
    WHERE code = NEW.category_code
    FOR SHARE;

    IF NOT FOUND OR v_is_deleted THEN
        RAISE EXCEPTION 'TRANSACTION_CATEGORY_DELETED' USING ERRCODE = 'check_violation';
    END IF;

    IF NOT v_is_leaf THEN
        RAISE EXCEPTION 'TRANSACTION_CATEGORY_NOT_LEAF' USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- giao dịch không có danh mục (vd. "Số dư ban đầu") thì không kiểm tra
CREATE TRIGGER trg_transactions_category_leaf
    BEFORE INSERT OR UPDATE OF category_code ON transactions
    FOR EACH ROW
    WHEN (NEW.category_code IS NOT NULL)
    EXECUTE FUNCTION fn_transactions_category_leaf();
