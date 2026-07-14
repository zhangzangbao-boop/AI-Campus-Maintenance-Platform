package com.qiyun.opsservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 维修类别引用实体
 * 用于知识库关联维修类别
 */
@Getter
@Setter
@Entity
@Table(name = "repair_category")
public class CategoryReference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long categoryId;

    @Column(name = "category_key", nullable = false, unique = true, length = 50)
    private String categoryName;
}