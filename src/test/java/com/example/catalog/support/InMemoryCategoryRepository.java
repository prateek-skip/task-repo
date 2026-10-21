package com.example.catalog.support;

import com.example.catalog.domain.model.Category;
import com.example.catalog.domain.port.CategoryRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class InMemoryCategoryRepository implements CategoryRepository {

    private final List<Category> categories;
    private int loads;

    public InMemoryCategoryRepository(Category... categories) {
        this.categories = List.of(categories);
    }

    @Override
    public Optional<Category> findBySlug(String slug) {
        loads++;
        return categories.stream().filter(category -> category.slug().equals(slug)).findFirst();
    }

    @Override
    public List<Category> findAll() {
        loads++;
        return categories.stream().sorted(Comparator.comparing(Category::name)).toList();
    }

    public int loads() {
        return loads;
    }
}
