package com.datn.financeapp.category.repository;

import com.datn.financeapp.category.entity.WalletCategorySetting;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository cho {@code wallet_category_settings} (V17) — bật/tắt danh mục theo từng ví.
 *
 * <p><b>Vắng dòng = đang bật</b>, nên không có method nào trả "danh sách đang bật": cái đó phải
 * tính từ cây danh mục đầy đủ trừ đi những dòng {@code is_enabled = FALSE} ở đây.
 */
public interface WalletCategorySettingRepository
        extends JpaRepository<WalletCategorySetting, WalletCategorySetting.WalletCategorySettingId> {

    /** Mọi dòng lệch mặc định của một ví — dùng để phủ cờ lên cây danh mục khi trả về. */
    List<WalletCategorySetting> findAllByWalletId(UUID walletId);

    /**
     * Ghi trạng thái bật/tắt, chèn mới nếu chưa có dòng nào.
     *
     * <p>Dùng {@code ON CONFLICT} thay vì đọc-rồi-ghi ở tầng service: hai thiết bị cùng bật/tắt
     * một danh mục sẽ đâm nhau ở khoá chính, và cách này để CSDL tự xử lý thay vì bắt được
     * exception rồi thử lại.
     */
    @Modifying
    @Query(
            value = """
                    INSERT INTO wallet_category_settings (wallet_id, category_id, is_enabled)
                    VALUES (:walletId, :categoryId, :isEnabled)
                    ON CONFLICT (wallet_id, category_id)
                    DO UPDATE SET is_enabled = EXCLUDED.is_enabled
                    """,
            nativeQuery = true)
    void upsert(
            @Param("walletId") UUID walletId,
            @Param("categoryId") UUID categoryId,
            @Param("isEnabled") boolean isEnabled);

    /**
     * Ghi trạng thái cho một danh mục cha VÀ mọi con của nó trong cùng một câu lệnh.
     *
     * <p>Con hiện dưới cha trong cùng cây, nên để con bật lơ lửng dưới cha đã tắt là trạng thái
     * vô nghĩa với người dùng. Gộp vào một câu để không có khoảnh khắc nào cha tắt mà con còn bật.
     */
    @Modifying
    @Query(
            value = """
                    INSERT INTO wallet_category_settings (wallet_id, category_id, is_enabled)
                    SELECT :walletId, c.id, :isEnabled
                    FROM categories c
                    WHERE NOT c.is_deleted AND (c.id = :categoryId OR c.parent_category_id = :categoryId)
                    ON CONFLICT (wallet_id, category_id)
                    DO UPDATE SET is_enabled = EXCLUDED.is_enabled
                    """,
            nativeQuery = true)
    void upsertWithChildren(
            @Param("walletId") UUID walletId,
            @Param("categoryId") UUID categoryId,
            @Param("isEnabled") boolean isEnabled);
}
