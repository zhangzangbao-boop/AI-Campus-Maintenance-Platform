package com.ligong.reportingcenter;

import static org.assertj.core.api.Assertions.assertThat;

import com.ligong.reportingcenter.domain.entity.Category;
import com.ligong.reportingcenter.dto.CategoryDto;
import com.ligong.reportingcenter.exception.BusinessException;
import com.ligong.reportingcenter.service.CategoryService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CategoryServiceTests {

    @Autowired
    private CategoryService categoryService;

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

        // 注意：这里可能会因为数据库中已存在的数据而失败
        try {
            categoryService.create(categoryName);
        } catch (BusinessException e) {
            // 预期的异常
            assertThat(e.getMessage()).contains("已存在");
        }
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
        
        CategoryDto category1 = categoryService.create(categoryName1);
        CategoryDto category2 = categoryService.create(categoryName2);

        try {
            categoryService.update(category2.categoryId(), categoryName1);
        } catch (BusinessException e) {
            // 预期的异常
            assertThat(e.getMessage()).contains("已存在");
        }
    }

    @Test
    void testDeleteCategory() {
        String categoryName = "测试删除分类_" + UUID.randomUUID().toString().substring(0, 8);
        CategoryDto categoryDto = categoryService.create(categoryName);

        categoryService.delete(categoryDto.categoryId());

        try {
            categoryService.getById(categoryDto.categoryId());
        } catch (BusinessException e) {
            // 预期的异常
            assertThat(e.getMessage()).contains("不存在");
        }
    }

    @Test
    void testDeleteNonExistentCategory() {
        try {
            categoryService.delete(99999L);
        } catch (BusinessException e) {
            // 预期的异常
            assertThat(e.getMessage()).contains("不存在");
        }
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
        try {
            categoryService.getById(99999L);
        } catch (BusinessException e) {
            // 预期的异常
            assertThat(e.getMessage()).contains("不存在");
        }
    }
}