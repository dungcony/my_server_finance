package com.datn.financeapp.category.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.category.entity.Category;
import com.datn.financeapp.category.entity.CategoryGroup;
import com.datn.financeapp.category.entity.Icon;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Transactional
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class CategoryRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1jYXRlZ29yeS1yZXBvLXRlc3QtMzJi");
    }

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryGroupRepository categoryGroupRepository;

    @Autowired
    private IconRepository iconRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private UUID userId;
    private UUID otherUserId;
    private UUID defaultIconId;
    private UUID defaultGroupId;

    @BeforeEach
    void setUp() {
        User user = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("category.user@example.com")
                .password("hash")
                .firstName("Cat")
                .lastName("User")
                .plan(UserPlan.FREE)
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build());
        userId = user.getId();

        User otherUser = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("other.cat.user@example.com")
                .password("hash")
                .firstName("Other")
                .lastName("User")
                .plan(UserPlan.FREE)
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build());
        otherUserId = otherUser.getId();

        Icon icon = iconRepository.findAll().stream().findFirst().orElseThrow();
        defaultIconId = icon.getId();

        CategoryGroup group = categoryGroupRepository.findAll().stream().findFirst().orElseThrow();
        defaultGroupId = group.getId();
    }

    private Category createCategory(UUID ownerId, UUID parentId, String name, String type, int sortOrder, boolean deleted) {
        Category category = Category.builder()
                .id(UUID.randomUUID())
                .userId(ownerId)
                .parentCategoryId(parentId)
                .categoryGroupId(defaultGroupId)
                .iconId(defaultIconId)
                .name(name)
                .type(type)
                .color("#AABBCC")
                .sortOrder(sortOrder)
                .isDeleted(deleted)
                .createdAt(Instant.now())
                .build();
        return categoryRepository.saveAndFlush(category);
    }

    @Test
    @DisplayName("findSystemCategoryByName: Tìm thấy danh mục hệ thống tạo sẵn trong Flyway")
    void findSystemCategoryByName_FindsSeededCategory() {
        Optional<Category> found = categoryRepository.findSystemCategoryByName("Cho vay", "expense");
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isNull();
        assertThat(found.get().getParentCategoryId()).isNull();
    }

    @Test
    @DisplayName("findByIdAndVisibleToUser: Danh mục hệ thống hiển thị cho mọi user, danh mục riêng chỉ hiển thị cho chủ sở hữu")
    void findByIdAndVisibleToUser_SystemAndCustomVisibility() {
        Category systemCat = categoryRepository.findSystemCategoryByName("Cho vay", "expense").orElseThrow();
        // Hệ thống hiển thị cho user
        assertThat(categoryRepository.findByIdAndVisibleToUser(systemCat.getId(), userId)).isPresent();
        // Hệ thống hiển thị cho otherUser
        assertThat(categoryRepository.findByIdAndVisibleToUser(systemCat.getId(), otherUserId)).isPresent();

        // Danh mục riêng của user
        Category customCat = createCategory(userId, null, "Custom Food", "expense", 1, false);
        assertThat(categoryRepository.findByIdAndVisibleToUser(customCat.getId(), userId)).isPresent();
        // Không hiển thị cho otherUser (404 isolation)
        assertThat(categoryRepository.findByIdAndVisibleToUser(customCat.getId(), otherUserId)).isEmpty();

        // Danh mục bị xóa mềm không hiển thị
        Category deletedCat = createCategory(userId, null, "Deleted Food", "expense", 2, true);
        assertThat(categoryRepository.findByIdAndVisibleToUser(deletedCat.getId(), userId)).isEmpty();
    }

    @Test
    @DisplayName("findCategoryTree: Gọi fn_category_tree trả về ID danh mục cha và các con")
    void findCategoryTree_ReturnsParentAndChildren() {
        Category parent = createCategory(userId, null, "Vehicle", "expense", 1, false);
        Category child1 = createCategory(userId, parent.getId(), "Gas", "expense", 1, false);
        Category child2 = createCategory(userId, parent.getId(), "Maintenance", "expense", 2, false);

        List<UUID> tree = categoryRepository.findCategoryTree(parent.getId());
        assertThat(tree).contains(parent.getId(), child1.getId(), child2.getId());
    }

    @Test
    @DisplayName("existsByParentCategoryIdAndIsDeletedFalse: Kiểm tra tồn tại danh mục con chưa xóa")
    void existsByParentCategoryIdAndIsDeletedFalse_ChecksActiveChildren() {
        Category parent = createCategory(userId, null, "Housing", "expense", 1, false);
        assertThat(categoryRepository.existsByParentCategoryIdAndIsDeletedFalse(parent.getId())).isFalse();

        Category child = createCategory(userId, parent.getId(), "Rent", "expense", 1, false);
        assertThat(categoryRepository.existsByParentCategoryIdAndIsDeletedFalse(parent.getId())).isTrue();

        child.setIsDeleted(true);
        categoryRepository.saveAndFlush(child);
        assertThat(categoryRepository.existsByParentCategoryIdAndIsDeletedFalse(parent.getId())).isFalse();
    }

    @Test
    @DisplayName("existsSiblingWithName: Kiểm tra trùng tên với anh/chị/em cùng cấp")
    void existsSiblingWithName_ChecksDuplicateNames() {
        Category parent = createCategory(userId, null, "Tech", "expense", 1, false);
        createCategory(userId, parent.getId(), "Laptop", "expense", 1, false);

        // Trùng tên cùng cấp
        boolean duplicate = categoryRepository.existsSiblingWithName(
                userId, parent.getId(), "expense", "laptop", null);
        assertThat(duplicate).isTrue();

        // Khác tên
        boolean different = categoryRepository.existsSiblingWithName(
                userId, parent.getId(), "expense", "phone", null);
        assertThat(different).isFalse();
    }

    @Test
    @DisplayName("updateSortOrder: Cập nhật thứ tự hiển thị của danh mục")
    void updateSortOrder_UpdatesSuccessfully() {
        Category cat = createCategory(userId, null, "Sortable", "expense", 1, false);

        int updated = categoryRepository.updateSortOrder(cat.getId(), 5, userId, null);
        assertThat(updated).isEqualTo(1);

        entityManager.clear();
        Category reloaded = categoryRepository.findById(cat.getId()).orElseThrow();
        assertThat(reloaded.getSortOrder()).isEqualTo(5);
    }
}
