package com.ligong.reportingcenter.service;

import com.ligong.reportingcenter.domain.entity.Category;
import com.ligong.reportingcenter.dto.CategoryDto;
import com.ligong.reportingcenter.exception.BusinessException;
import com.ligong.reportingcenter.repository.CategoryRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    public CategoryDto create(String name) {
        if (categoryRepository.existsByCategoryName(name)) {
            throw new BusinessException("分类名称已存在");
        }
        Category category = new Category();
        category.setCategoryName(name);
        categoryRepository.save(category);
        return toDto(category);
    }

    @Transactional
    public CategoryDto update(Long categoryId, String name) {
        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "分类不存在"));
        if (!category.getCategoryName().equals(name) &&
                categoryRepository.existsByCategoryNameAndCategoryIdNot(name, categoryId)) {
            throw new BusinessException("分类名称已存在");
        }

        category.setCategoryName(name);
        return toDto(category);
    }

    @Transactional
    public void delete(Long categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "分类不存在");
        }
        categoryRepository.deleteById(categoryId);
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> listAll() {
        return categoryRepository.findAll()
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Category getById(Long categoryId) {
        return categoryRepository.findById(categoryId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "分类不存在"));
    }

    @Transactional(readOnly = true)
    public Category getByName(String categoryName) {
        return categoryRepository.findByCategoryName(categoryName)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "分类不存在"));
    }
    
    @Transactional(readOnly = true)
    public List<Category> listAllCategories() {
        return categoryRepository.findAll();
    }

    private CategoryDto toDto(Category category) {
        return new CategoryDto(category.getCategoryId(), category.getCategoryName());
    }
}
