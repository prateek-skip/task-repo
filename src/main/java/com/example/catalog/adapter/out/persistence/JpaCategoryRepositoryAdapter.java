package com.example.catalog.adapter.out.persistence;

import com.example.catalog.domain.model.Category;
import com.example.catalog.domain.port.CategoryRepository;
import java.util.List;
import java.util.Optional;

public class JpaCategoryRepositoryAdapter implements CategoryRepository {

    private final SpringDataCategoryRepository rows;

    public JpaCategoryRepositoryAdapter(SpringDataCategoryRepository rows) {
        this.rows = rows;
    }

    @Override
    public Optional<Category> findBySlug(String slug) {
        return rows.findBySlug(slug).map(PersistenceMapper::toDomain);
    }

    @Override
    public List<Category> findAll() {
        return rows.findAllByOrderByNameAsc().stream().map(PersistenceMapper::toDomain).toList();
    }
}
