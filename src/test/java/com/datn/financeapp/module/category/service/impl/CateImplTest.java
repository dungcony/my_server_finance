package com.datn.financeapp.module.category.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.category.dto.request.CategoryCreateReq;
import com.datn.financeapp.module.category.dto.request.CategoryUpdateReq;
import com.datn.financeapp.module.category.dto.response.CategoryRes;
import com.datn.financeapp.module.category.entity.Category;
import com.datn.financeapp.module.category.helper.CateCache;
import com.datn.financeapp.module.category.mapper.CateMapper;
import com.datn.financeapp.module.category.repository.CategoryRepository;
import com.datn.financeapp.module.icon.dto.response.IconResponse;
import com.datn.financeapp.module.icon.service.IconService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CateImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private IconService iconService;

    @Mock
    private CateMapper cateMapper;

    @Mock
    private CateCache cateCache;

    private CateImpl cateImpl;

    @BeforeEach
    void setUp() {
        cateImpl = new CateImpl(categoryRepository, iconService, cateMapper, cateCache);
        lenient().when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(cateMapper.toResponse(any(Category.class), any())).thenAnswer(invocation -> toResponse(invocation.getArgument(0)));
        lenient().when(iconService.getActive(any(String.class))).thenAnswer(invocation -> icon(invocation.getArgument(0)));
        lenient().when(iconService.getActiveById(any(UUID.class))).thenAnswer(invocation -> icon("test"));
        lenient().when(iconService.getActiveByIds(any())).thenReturn(java.util.Collections.emptyMap());
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ---------------------------------------------------------------- create

    @Test
    @DisplayName("Tạo danh mục gốc: level 0, path bằng code, là lá")
    void create_root_setsLevelZeroAndPathEqualsCode() {
        when(iconService.getActive("ca_phe")).thenReturn(icon("ca_phe"));

        cateImpl.create(createReq("an_uong", "Ăn uống", null));

        Category saved = savedCategory();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCode()).isEqualTo("an_uong");
        assertThat(saved.getName()).isEqualTo("Ăn uống");
        assertThat(saved.getParent()).isNull();
        assertThat(saved.getLevel()).isZero();
        assertThat(saved.getPath()).isEqualTo("an_uong");
        assertThat(saved.getIsLeaf()).isTrue();
        assertThat(saved.getDeleted()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Tạo danh mục con: level và path nối từ cha, cha thôi là lá")
    void create_child_buildsPathFromParentAndParentStopsBeingLeaf() {
        Category parent = category("an_uong", null);
        when(iconService.getActive("ca_phe")).thenReturn(icon("ca_phe"));
        when(categoryRepository.findByCodeAndDeletedFalse("an_uong")).thenReturn(Optional.of(parent));

        cateImpl.create(createReq("ca_phe", "Cà phê", "an_uong"));

        Category saved = savedCategory();
        assertThat(saved.getParent()).isSameAs(parent);
        assertThat(saved.getLevel()).isEqualTo(1);
        assertThat(saved.getPath()).isEqualTo("an_uong/ca_phe");
        assertThat(saved.getIsLeaf()).isTrue();
        assertThat(parent.getIsLeaf()).isFalse();
    }

    @Test
    @DisplayName("Tạo danh mục trùng code thì báo CATEGORY_CODE_EXISTS và không lưu")
    void create_duplicateCode_throws() {
        when(cateCache.existsCode("an_uong")).thenReturn(true);

        assertThatThrownBy(() -> cateImpl.create(createReq("an_uong", "Ăn uống", null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CATEGORY_CODE_EXISTS.getCode());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("Tra id theo code lấy từ cache, không query DB")
    void findIdByCode_inCache_returnsId() {
        UUID id = UUID.randomUUID();
        when(cateCache.findActiveId("an_uong")).thenReturn(Optional.of(id));

        assertThat(cateImpl.findIdByCode("an_uong")).isEqualTo(id);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    @DisplayName("Tra id theo code không có trong cache hoặc đã xóa mềm thì báo CATEGORY_NOT_FOUND")
    void findIdByCode_missing_throws() {
        when(cateCache.findActiveId("khong_co")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cateImpl.findIdByCode("khong_co"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CATEGORY_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("Tra nhiều code theo id từ cache, bỏ id không có")
    void findCodesByIds_skipsUnknownIds() {
        UUID known = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        when(cateCache.findCode(known)).thenReturn(Optional.of("an_uong"));
        when(cateCache.findCode(unknown)).thenReturn(Optional.empty());

        assertThat(cateImpl.findCodesByIds(List.of(known, unknown))).containsOnly(entry(known, "an_uong"));
        verifyNoInteractions(categoryRepository);
    }

    @Test
    @DisplayName("Tra nhiều code với danh sách null hoặc rỗng trả map rỗng")
    void findCodesByIds_emptyInput_returnsEmpty() {
        assertThat(cateImpl.findCodesByIds(null)).isEmpty();
        assertThat(cateImpl.findCodesByIds(List.of())).isEmpty();
    }

    @Test
    @DisplayName("Tạo danh mục với biểu tượng không tồn tại hoặc đã ngừng dùng thì báo ICON_NOT_FOUND")
    void create_iconInvalid_throws() {
        when(iconService.getActive("ca_phe")).thenThrow(new BusinessException(ErrorCode.ICON_NOT_FOUND));

        assertThatThrownBy(() -> cateImpl.create(createReq("ca_phe", "Cà phê", null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ICON_NOT_FOUND.getCode());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("Tạo danh mục con của cha không tồn tại thì báo CATEGORY_NOT_FOUND")
    void create_parentNotFound_throws() {
        when(iconService.getActive("ca_phe")).thenReturn(icon("ca_phe"));

        assertThatThrownBy(() -> cateImpl.create(createReq("ca_phe", "Cà phê", "khong_co")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CATEGORY_NOT_FOUND.getCode());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("Tạo danh mục trùng tên với danh mục khác trong cây thì báo CATEGORY_NAME_EXISTS")
    void create_duplicateName_throws() {
        Category parent = category("an_uong", null);
        when(iconService.getActive("ca_phe")).thenReturn(icon("ca_phe"));
        when(categoryRepository.findByCodeAndDeletedFalse("an_uong")).thenReturn(Optional.of(parent));
        when(categoryRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot(eq("Cà phê"), any(UUID.class)))
                .thenReturn(true);

        assertThatThrownBy(() -> cateImpl.create(createReq("ca_phe", "Cà phê", "an_uong")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CATEGORY_NAME_EXISTS.getCode());
        verify(categoryRepository, never()).save(any(Category.class));
        assertThat(parent.getIsLeaf()).isTrue();
    }

    @Test
    @DisplayName("Code chỉ được ghi vào cache sau khi transaction commit")
    void create_registersCodeInCacheOnlyAfterCommit() {
        when(iconService.getActive("ca_phe")).thenReturn(icon("ca_phe"));

        cateImpl.create(createReq("an_uong", "Ăn uống", null));

        verify(cateCache, never()).register(any(), any());
        Category saved = savedCategory();
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(cateCache).register(saved.getId(), "an_uong");
    }

    // ------------------------------------------------------------------ read

    @Test
    @DisplayName("Lấy danh mục gốc trả đúng danh sách đã map")
    void getMasters_returnsMappedRoots() {
        Category food = category("an_uong", null);
        Category salary = category("luong", null);
        when(categoryRepository.findByParentIsNullAndDeletedFalseOrderByNameAsc()).thenReturn(List.of(food, salary));

        List<CategoryRes> result = cateImpl.getMasters();

        assertThat(result).extracting(CategoryRes::code).containsExactly("an_uong", "luong");
    }

    @Test
    @DisplayName("Lấy con của danh mục trả đúng các con trực tiếp")
    void getOfParent_returnsMappedChildren() {
        Category parent = category("an_uong", null);
        Category coffee = category("ca_phe", parent);
        when(categoryRepository.findByCodeAndDeletedFalse("an_uong")).thenReturn(Optional.of(parent));
        when(categoryRepository.findByParent_IdAndDeletedFalseOrderByNameAsc(parent.getId())).thenReturn(List.of(coffee));

        List<CategoryRes> result = cateImpl.getOfParent("an_uong");

        assertThat(result).extracting(CategoryRes::code).containsExactly("ca_phe");
    }

    @Test
    @DisplayName("Lấy con của danh mục không tồn tại thì báo CATEGORY_NOT_FOUND")
    void getOfParent_unknownParent_throws() {
        assertThatThrownBy(() -> cateImpl.getOfParent("khong_co"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CATEGORY_NOT_FOUND.getCode());
    }

    // ---------------------------------------------------------------- update

    @Test
    @DisplayName("Cập nhật chỉ đổi trường được gửi lên, trường null giữ nguyên")
    void update_changesOnlyProvidedFields() {
        Category coffee = category("ca_phe", null);
        IconResponse newIcon = icon("tra_sua");
        when(categoryRepository.findByCodeAndDeletedFalse("ca_phe")).thenReturn(Optional.of(coffee));
        when(iconService.getActive("tra_sua")).thenReturn(newIcon);

        cateImpl.update("ca_phe", new CategoryUpdateReq("Trà sữa", null, "tra_sua", "#112233", null));

        assertThat(coffee.getName()).isEqualTo("Trà sữa");
        assertThat(coffee.getIconId()).isEqualTo(newIcon.id());
        assertThat(coffee.getColor()).isEqualTo("#112233");
        assertThat(coffee.getDescription()).isEqualTo("mô tả cũ");
        assertThat(coffee.getPath()).isEqualTo("ca_phe");
    }

    @Test
    @DisplayName("Cập nhật danh mục không tồn tại thì báo CATEGORY_NOT_FOUND")
    void update_unknownCategory_throws() {
        assertThatThrownBy(() -> cateImpl.update("khong_co", new CategoryUpdateReq("A", null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CATEGORY_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("Đổi tên trùng với danh mục cùng cấp thì báo CATEGORY_NAME_EXISTS")
    void update_duplicateName_throws() {
        Category coffee = category("ca_phe", null);
        when(categoryRepository.findByCodeAndDeletedFalse("ca_phe")).thenReturn(Optional.of(coffee));
        when(categoryRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot("Trà sữa", coffee.getId())).thenReturn(true);

        assertThatThrownBy(() -> cateImpl.update("ca_phe", new CategoryUpdateReq("Trà sữa", null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CATEGORY_NAME_EXISTS.getCode());
        assertThat(coffee.getName()).isEqualTo("Ca ca_phe");
    }

    @Test
    @DisplayName("Đổi cha: tính lại level/path cả cây con, cha cũ hết con thành lá, cha mới thôi là lá")
    void update_changeParent_recomputesSubtreeAndLeafFlags() {
        Category oldParent = category("cu", null);
        oldParent.setIsLeaf(false);
        Category moved = category("x", oldParent);
        moved.setIsLeaf(false);
        Category grandChild = category("y", moved);
        Category newParent = category("moi", null);
        when(categoryRepository.findByCodeAndDeletedFalse("x")).thenReturn(Optional.of(moved));
        when(categoryRepository.findByCodeAndDeletedFalse("moi")).thenReturn(Optional.of(newParent));
        when(categoryRepository.findDescendants("cu/x")).thenReturn(List.of(grandChild));

        cateImpl.update("x", new CategoryUpdateReq(null, null, null, null, "moi"));

        assertThat(moved.getParent()).isSameAs(newParent);
        assertThat(moved.getLevel()).isEqualTo(1);
        assertThat(moved.getPath()).isEqualTo("moi/x");
        assertThat(grandChild.getLevel()).isEqualTo(2);
        assertThat(grandChild.getPath()).isEqualTo("moi/x/y");
        assertThat(oldParent.getIsLeaf()).isTrue();
        assertThat(newParent.getIsLeaf()).isFalse();
    }

    @Test
    @DisplayName("Đổi cha sang chính nó thì báo CATEGORY_PARENT_INVALID")
    void update_parentIsSelf_throws() {
        Category x = category("x", null);
        when(categoryRepository.findByCodeAndDeletedFalse("x")).thenReturn(Optional.of(x));

        assertThatThrownBy(() -> cateImpl.update("x", new CategoryUpdateReq(null, null, null, null, "x")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CATEGORY_PARENT_INVALID.getCode());
    }

    @Test
    @DisplayName("Đổi cha sang con cháu của chính nó thì báo CATEGORY_PARENT_INVALID")
    void update_parentIsDescendant_throws() {
        Category x = category("x", null);
        Category y = category("y", x);
        when(categoryRepository.findByCodeAndDeletedFalse("x")).thenReturn(Optional.of(x));
        when(categoryRepository.findByCodeAndDeletedFalse("y")).thenReturn(Optional.of(y));

        assertThatThrownBy(() -> cateImpl.update("x", new CategoryUpdateReq(null, null, null, null, "y")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CATEGORY_PARENT_INVALID.getCode());
        assertThat(x.getPath()).isEqualTo("x");
    }

    // ---------------------------------------------------------------- delete

    @Test
    @DisplayName("Xóa danh mục không tồn tại thì báo CATEGORY_NOT_FOUND")
    void delete_unknownCategory_throws() {
        assertThatThrownBy(() -> cateImpl.delete("khong_co"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CATEGORY_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("Xóa danh mục cha thì xóa mềm cả con và cháu")
    void delete_withDescendants_softDeletesWholeSubtree() {
        Category food = category("an_uong", null);
        Category coffee = category("ca_phe", food);
        Category latte = category("latte", coffee);
        when(categoryRepository.findByCodeAndDeletedFalse("an_uong")).thenReturn(Optional.of(food));
        when(categoryRepository.findDescendants("an_uong")).thenReturn(List.of(coffee, latte));

        cateImpl.delete("an_uong");

        assertThat(food.getDeleted()).isTrue();
        assertThat(coffee.getDeleted()).isTrue();
        assertThat(latte.getDeleted()).isTrue();
    }

    @Test
    @DisplayName("Danh mục thay thế nằm trong cây bị xóa thì báo VALIDATION_ERROR và không xóa gì")
    void delete_replacementInsideDeletedSubtree_throws() {
        Category food = category("an_uong", null);
        food.setIsLeaf(false);
        Category coffee = category("ca_phe", food);
        when(categoryRepository.findByCodeAndDeletedFalse("an_uong")).thenReturn(Optional.of(food));
        when(categoryRepository.findByCodeAndDeletedFalse("ca_phe")).thenReturn(Optional.of(coffee));

        assertThatThrownBy(() -> cateImpl.delete("an_uong"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_ERROR.getCode());
        assertThat(food.getDeleted()).isFalse();
        assertThat(coffee.getDeleted()).isFalse();
    }

    @Test
    @DisplayName("Xóa con cuối cùng: đánh dấu xóa mềm và cha trở lại là lá")
    void delete_lastChild_softDeletesAndParentBecomesLeaf() {
        Category parent = category("an_uong", null);
        parent.setIsLeaf(false);
        Category coffee = category("ca_phe", parent);
        when(categoryRepository.findByCodeAndDeletedFalse("ca_phe")).thenReturn(Optional.of(coffee));

        cateImpl.delete("ca_phe");

        assertThat(coffee.getDeleted()).isTrue();
        assertThat(parent.getIsLeaf()).isTrue();
    }

    @Test
    @DisplayName("Xóa một con khi cha còn con khác thì cha vẫn không phải lá")
    void delete_parentStillHasOtherChildren_parentStaysNonLeaf() {
        Category parent = category("an_uong", null);
        parent.setIsLeaf(false);
        Category coffee = category("ca_phe", parent);
        when(categoryRepository.findByCodeAndDeletedFalse("ca_phe")).thenReturn(Optional.of(coffee));
        when(categoryRepository.existsByParent_IdAndDeletedFalse(parent.getId())).thenReturn(true);

        cateImpl.delete("ca_phe");

        assertThat(coffee.getDeleted()).isTrue();
        assertThat(parent.getIsLeaf()).isFalse();
    }

    @Test
    @DisplayName("Danh mục thay thế không tồn tại thì báo CATEGORY_NOT_FOUND và không xóa")
    void delete_replacementNotFound_throws() {
        Category coffee = category("ca_phe", null);
        when(categoryRepository.findByCodeAndDeletedFalse("ca_phe")).thenReturn(Optional.of(coffee));

        assertThatThrownBy(() -> cateImpl.delete("ca_phe"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CATEGORY_NOT_FOUND.getCode());
        assertThat(coffee.getDeleted()).isFalse();
    }

    @Test
    @DisplayName("Danh mục thay thế không phải lá thì báo VALIDATION_ERROR và không xóa")
    void delete_replacementNotLeaf_throws() {
        Category coffee = category("ca_phe", null);
        Category food = category("an_uong", null);
        food.setIsLeaf(false);
        when(categoryRepository.findByCodeAndDeletedFalse("ca_phe")).thenReturn(Optional.of(coffee));
        when(categoryRepository.findByCodeAndDeletedFalse("an_uong")).thenReturn(Optional.of(food));

        assertThatThrownBy(() -> cateImpl.delete("ca_phe"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_ERROR.getCode());
        assertThat(coffee.getDeleted()).isFalse();
    }

    @Test
    @DisplayName("Danh mục thay thế là chính nó thì báo VALIDATION_ERROR và không xóa")
    void delete_replacementIsSelf_throws() {
        Category coffee = category("ca_phe", null);
        when(categoryRepository.findByCodeAndDeletedFalse("ca_phe")).thenReturn(Optional.of(coffee));

        assertThatThrownBy(() -> cateImpl.delete("ca_phe"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_ERROR.getCode());
        assertThat(coffee.getDeleted()).isFalse();
    }

    // --------------------------------------------------------------- helpers

    private Category savedCategory() {
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        return captor.getValue();
    }

    private static CategoryCreateReq createReq(String code, String name, String parentCode) {
        return new CategoryCreateReq(name, code, "mô tả", "ca_phe", "#3d6b7d", parentCode);
    }

    private static IconResponse icon(String code) {
        return new IconResponse(UUID.randomUUID(), code, code, "M0 0");
    }

    private static Category category(String code, Category parent) {
        return Category.builder()
                .id(UUID.randomUUID())
                .code(code)
                .name("Ca " + code)
                .description("mô tả cũ")
                .parent(parent)
                .iconId(UUID.randomUUID())
                .color("#3d6b7d")
                .level(parent == null ? 0 : parent.getLevel() + 1)
                .path(parent == null ? code : parent.getPath() + "/" + code)
                .createdAt(Instant.now())
                .build();
    }

    private static CategoryRes toResponse(Category category) {
        return new CategoryRes(
                category.getId(),
                category.getName(),
                category.getCode(),
                category.getDescription(),
                category.getParent() == null ? null : category.getParent().getCode(),
                null,
                category.getColor(),
                category.getLevel(),
                category.getIsLeaf(),
                category.getCreatedAt(),
                category.getDeleted());
    }
}
