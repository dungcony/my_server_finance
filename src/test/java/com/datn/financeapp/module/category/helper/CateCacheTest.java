package com.datn.financeapp.module.category.helper;

import com.datn.financeapp.module.category.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CateCacheTest {

    private static final UUID FOOD_ID = UUID.randomUUID();
    private static final UUID SALARY_ID = UUID.randomUUID();

    @Mock
    private CategoryRepository categoryRepository;

    private CateCache cateCache;

    @BeforeEach
    void setUp() {
        cateCache = new CateCache(categoryRepository);
        List<CategoryCodeView> views = List.of(view(FOOD_ID, "FOOD"), view(SALARY_ID, "SALARY"));
        when(categoryRepository.findAllCodes()).thenReturn(views);
        cateCache.loadAll();
    }

    @Test
    void loadAll_populatesBothDirections() {
        assertThat(cateCache.findCode(FOOD_ID)).contains("FOOD");
        assertThat(cateCache.findId("SALARY")).contains(SALARY_ID);
        assertThat(cateCache.existsCode("FOOD")).isTrue();
        assertThat(cateCache.existsCode("UNKNOWN")).isFalse();
    }

    @Test
    void loadAll_isRepeatable() {
        cateCache.loadAll();

        assertThat(cateCache.findCode(FOOD_ID)).contains("FOOD");
        assertThat(cateCache.findId("FOOD")).contains(FOOD_ID);
    }

    @Test
    void register_returnsFalse_whenCodeTakenByAnotherId() {
        UUID newId = UUID.randomUUID();

        boolean registered = cateCache.register(newId, "FOOD");

        assertThat(registered).isFalse();
        assertThat(cateCache.findCode(newId)).isEmpty();
        assertThat(cateCache.findId("FOOD")).contains(FOOD_ID);
    }

    @Test
    void register_returnsTrue_whenSameIdSameCode() {
        assertThat(cateCache.register(FOOD_ID, "FOOD")).isTrue();
        assertThat(cateCache.findId("FOOD")).contains(FOOD_ID);
    }

    @Test
    void register_addsNewCode() {
        UUID newId = UUID.randomUUID();

        assertThat(cateCache.register(newId, "TRAVEL")).isTrue();
        assertThat(cateCache.findCode(newId)).contains("TRAVEL");
        assertThat(cateCache.findId("TRAVEL")).contains(newId);
    }

    @Test
    void changeCode_releasesOldCode() {
        boolean changed = cateCache.changeCode(FOOD_ID, "MEAL");

        assertThat(changed).isTrue();
        assertThat(cateCache.existsCode("FOOD")).isFalse();
        assertThat(cateCache.findId("MEAL")).contains(FOOD_ID);
        assertThat(cateCache.findCode(FOOD_ID)).contains("MEAL");
    }

    @Test
    void changeCode_returnsFalse_whenNewCodeTaken() {
        boolean changed = cateCache.changeCode(FOOD_ID, "SALARY");

        assertThat(changed).isFalse();
        assertThat(cateCache.findCode(FOOD_ID)).contains("FOOD");
        assertThat(cateCache.findId("SALARY")).contains(SALARY_ID);
    }

    @Test
    void lookups_returnEmpty_whenArgumentIsNull() {
        assertThat(cateCache.findCode(null)).isEmpty();
        assertThat(cateCache.findId(null)).isEmpty();
        assertThat(cateCache.existsCode(null)).isFalse();
    }

    @Test
    void register_returnsFalse_whenIdOrCodeIsNullOrBlank() {
        assertThat(cateCache.register(null, "TRAVEL")).isFalse();
        assertThat(cateCache.register(UUID.randomUUID(), null)).isFalse();
        assertThat(cateCache.register(UUID.randomUUID(), "  ")).isFalse();
        assertThat(cateCache.changeCode(FOOD_ID, " ")).isFalse();
        assertThat(cateCache.findCode(FOOD_ID)).contains("FOOD");
    }

    @Test
    void loadAll_dropsEntriesNoLongerInDatabase() {
        when(categoryRepository.findAllCodes()).thenReturn(List.of(view(SALARY_ID, "SALARY")));

        cateCache.loadAll();

        assertThat(cateCache.existsCode("FOOD")).isFalse();
        assertThat(cateCache.findCode(FOOD_ID)).isEmpty();
        assertThat(cateCache.findId("SALARY")).contains(SALARY_ID);
    }

    @Test
    void loadAll_marksDeletedRows() {
        UUID removedId = UUID.randomUUID();
        when(categoryRepository.findAllCodes())
                .thenReturn(List.of(view(FOOD_ID, "FOOD"), new CategoryCodeView(removedId, "OLD", true)));

        cateCache.loadAll();

        assertThat(cateCache.isDeleted(removedId)).isTrue();
        assertThat(cateCache.findId("OLD")).contains(removedId);
        assertThat(cateCache.findActiveId("OLD")).isEmpty();
        assertThat(cateCache.findActiveId("FOOD")).contains(FOOD_ID);
    }

    @Test
    void markDeleted_hidesFromFindActiveId_butKeepsCodeReserved() {
        cateCache.markDeleted(List.of(FOOD_ID));

        assertThat(cateCache.isDeleted(FOOD_ID)).isTrue();
        assertThat(cateCache.findActiveId("FOOD")).isEmpty();
        assertThat(cateCache.existsCode("FOOD")).isTrue();
        assertThat(cateCache.findCode(FOOD_ID)).contains("FOOD");
    }

    @Test
    void markDeleted_ignoresNullInput() {
        cateCache.markDeleted(null);

        assertThat(cateCache.isDeleted(FOOD_ID)).isFalse();
        assertThat(cateCache.isDeleted(null)).isFalse();
    }

    private static CategoryCodeView view(UUID id, String code) {
        return new CategoryCodeView(id, code, false);
    }
}
