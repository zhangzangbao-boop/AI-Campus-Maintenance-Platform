package com.qiyun.repairservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.feign.client.AiServiceClient;
import com.qiyun.repairservice.domain.entity.Category;
import com.qiyun.repairservice.dto.CategoryDto;
import com.qiyun.repairservice.repository.CategoryRepository;
import com.qiyun.repairservice.service.CategoryService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CategoryServiceTests {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CategoryRepository categoryRepository;

    @MockBean
    private AiServiceClient aiServiceClient;

    @BeforeEach
    void setUp() {
        // Clean up any existing categories
        categoryRepository.deleteAll();
    }

    @Test
    void testCreateCategory() {
        String categoryName = "网络设备_" + UUID.randomUUID().toString().substring(0, 8);
        CategoryDto categoryDto = categoryService.create(categoryName);

        assertThat(categoryDto).isNotNull();
        assertThat(categoryDto.categoryName()).isEqualTo(categoryName);
        assertThat(categoryDto.categoryId()).isNotNull();
    }

    @Test
    void testCreateDuplicateCategory() {
        String categoryName = "电器故障_" + UUID.randomUUID().toString().substring(0, 8);
        categoryService.create(categoryName);

        // Creating duplicate should throw BusinessException
        assertThrows(BusinessException.class, () -> categoryService.create(categoryName));
    }

    @Test
    void testUpdateCategory() {
        String categoryName = "硬件问题_" + UUID.randomUUID().toString().substring(0, 8);
        CategoryDto categoryDto = categoryService.create(categoryName);
        String newName = "硬件故障_" + UUID.randomUUID().toString().substring(0, 8);

        CategoryDto updatedDto = categoryService.update(categoryDto.categoryId(), newName);

        assertThat(updatedDto.categoryName()).isEqualTo(newName);
    }

    @Test
    void testUpdateCategoryWithDuplicateName() {
        String categoryName1 = "电脑问题_" + UUID.randomUUID().toString().substring(0, 8);
        String categoryName2 = "手机问题_" + UUID.randomUUID().toString().substring(0, 8);

        categoryService.create(categoryName1);
        CategoryDto category2 = categoryService.create(categoryName2);

        assertThrows(BusinessException.class, () ->
            categoryService.update(category2.categoryId(), categoryName1));
    }

    @Test
    void testDeleteCategory() {
        String categoryName = "测试删除分类_" + UUID.randomUUID().toString().substring(0, 8);
        CategoryDto categoryDto = categoryService.create(categoryName);

        categoryService.delete(categoryDto.categoryId());

        assertThrows(BusinessException.class, () ->
            categoryService.getById(categoryDto.categoryId()));
    }

    @Test
    void testDeleteNonExistentCategory() {
        assertThrows(BusinessException.class, () -> categoryService.delete(99999L));
    }

    @Test
    void testListAllCategories() {
        String categoryName1 = "分类1_" + UUID.randomUUID().toString().substring(0, 8);
        String categoryName2 = "分类2_" + UUID.randomUUID().toString().substring(0, 8);
        String categoryName3 = "分类3_" + UUID.randomUUID().toString().substring(0, 8);

        categoryService.create(categoryName1);
        categoryService.create(categoryName2);
        categoryService.create(categoryName3);

        List<CategoryDto> categories = categoryService.listAll();

        assertThat(categories).hasSizeGreaterThanOrEqualTo(3);
        assertThat(categories).extracting(CategoryDto::categoryName)
                .contains(categoryName1, categoryName2, categoryName3);
    }

    @Test
    void testGetById() {
        String categoryName = "查找测试分类_" + UUID.randomUUID().toString().substring(0, 8);
        CategoryDto createdCategory = categoryService.create(categoryName);
        Category foundCategory = categoryService.getById(createdCategory.categoryId());

        assertThat(foundCategory).isNotNull();
        assertThat(foundCategory.getCategoryId()).isEqualTo(createdCategory.categoryId());
        assertThat(foundCategory.getCategoryName()).isEqualTo(createdCategory.categoryName());
    }

    @Test
    void testGetByNonExistentId() {
        assertThrows(BusinessException.class, () -> categoryService.getById(99999L));
    }
}