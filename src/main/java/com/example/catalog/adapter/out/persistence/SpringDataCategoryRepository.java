package com.example.catalog.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataCategoryRepository extends JpaRepository<CategoryEntity, UUID> {

    Optional<CategoryEntity> findBySlug(String slug);

    List<CategoryEntity> findAllByOrderByNameAsc();
}
