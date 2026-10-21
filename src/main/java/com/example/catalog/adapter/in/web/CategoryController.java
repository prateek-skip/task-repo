package com.example.catalog.adapter.in.web;

import com.example.catalog.adapter.in.web.dto.CategoryResponse;
import com.example.catalog.application.port.in.ListCategoriesUseCase;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/categories", produces = MediaType.APPLICATION_JSON_VALUE)
public class CategoryController {

    // Reference data that changes only with a release, so clients can keep it for an hour.
    private static final CacheControl REFERENCE_DATA = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

    private final ListCategoriesUseCase listCategories;
    private final ProductWebMapper mapper;

    public CategoryController(ListCategoriesUseCase listCategories, ProductWebMapper mapper) {
        this.listCategories = listCategories;
        this.mapper = mapper;
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> list() {
        List<CategoryResponse> categories = listCategories.listCategories().stream().map(mapper::toResponse).toList();
        return ResponseEntity.ok().cacheControl(REFERENCE_DATA).body(categories);
    }
}
