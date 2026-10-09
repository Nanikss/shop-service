package dev.kumarnenavath.shop.service;

import dev.kumarnenavath.shop.domain.Category;
import dev.kumarnenavath.shop.domain.Product;
import dev.kumarnenavath.shop.error.ConflictException;
import dev.kumarnenavath.shop.error.NotFoundException;
import dev.kumarnenavath.shop.repository.CategoryRepository;
import dev.kumarnenavath.shop.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CatalogService {

    static final int MAX_PAGE_SIZE = 100;

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CatalogService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @Transactional
    public Category createCategory(String name) {
        String trimmed = name.trim();
        if (categories.existsByNameIgnoreCase(trimmed)) throw new ConflictException("Category '" + trimmed + "' already exists");
        return categories.save(new Category(trimmed));
    }

    @Transactional(readOnly = true)
    public List<Category> listCategories() {
        return categories.findAll(Sort.by("name"));
    }

    @Transactional
    public Product createProduct(String name, long categoryId, long priceCents, int stock) {
        Category category = categories.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category " + categoryId + " not found"));
        return products.save(new Product(name.trim(), category, priceCents, stock));
    }

    /** Stable order (by id) so pages don't shuffle between requests; page size capped at 100. */
    @Transactional(readOnly = true)
    public Page<Product> listProducts(Long categoryId, int page, int size) {
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by("id"));
        return categoryId == null ? products.findAll(request) : products.findByCategoryId(categoryId, request);
    }

    @Transactional(readOnly = true)
    public Product getProduct(long productId) {
        return products.findWithCategoryById(productId)
                .orElseThrow(() -> new NotFoundException("Product " + productId + " not found"));
    }
}
