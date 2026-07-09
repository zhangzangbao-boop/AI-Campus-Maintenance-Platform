package com.ligong.reportingcenter.repository;

import com.ligong.reportingcenter.domain.entity.Category;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByCategoryName(String categoryName);
    boolean existsByCategoryName(String categoryName);
    boolean existsByCategoryNameAndCategoryIdNot(String categoryName, Long categoryId);
}

