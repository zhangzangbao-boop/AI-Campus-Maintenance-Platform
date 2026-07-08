package com.ligong.reportingcenter.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "repair_category") // 修改为与数据库一致
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id") // 修改为主键列名与数据库一致
    private Long categoryId;

    @Column(name = "category_key", nullable = false, unique = true, length = 50) // 修改列名与长度
    private String categoryName;
}
