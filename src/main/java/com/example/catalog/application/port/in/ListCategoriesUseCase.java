package com.example.catalog.application.port.in;

import com.example.catalog.domain.model.Category;
import java.util.List;

public interface ListCategoriesUseCase {

    List<Category> listCategories();
}
