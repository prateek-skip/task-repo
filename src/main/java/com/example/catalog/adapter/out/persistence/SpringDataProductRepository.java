package com.example.catalog.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataProductRepository extends JpaRepository<ProductEntity, UUID> {

    // Fetches the category in the same query; mapping to the domain needs its slug and name.
    @EntityGraph(attributePaths = "category")
    Optional<ProductEntity> findWithCategoryById(UUID id);
}
