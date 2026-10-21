package com.example.catalog.domain.port;

import com.example.catalog.domain.model.Category;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository {

    Optional<Category> findBySlug(String slug);

    /** All categories, ordered by name. */
    List<Category> findAll();
}
