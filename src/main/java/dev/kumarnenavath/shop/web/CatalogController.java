package dev.kumarnenavath.shop.web;

import dev.kumarnenavath.shop.domain.Category;
import dev.kumarnenavath.shop.domain.Product;
import dev.kumarnenavath.shop.security.AdminOnly;
import dev.kumarnenavath.shop.security.PublicEndpoint;
import dev.kumarnenavath.shop.service.CatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    public record CategoryRequest(@NotBlank @Size(max = 100) String name) {}
    public record ProductRequest(@NotBlank @Size(max = 200) String name, @NotNull Long categoryId,
                                 @Positive long priceCents, @PositiveOrZero int stock) {}

    public record CategoryView(long id, String name) {
        static CategoryView of(Category c) {
            return new CategoryView(c.getId(), c.getName());
        }
    }

    public record ProductView(long id, String name, long categoryId, String categoryName, long priceCents, int stock) {
        static ProductView of(Product p) {
            return new ProductView(p.getId(), p.getName(), p.getCategory().getId(), p.getCategory().getName(),
                    p.getPriceCents(), p.getStock());
        }
    }

    /** Explicit page shape, rather than serializing Spring's Page (whose JSON isn't a stable contract). */
    public record PageView<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @AdminOnly
    public CategoryView createCategory(@Valid @RequestBody CategoryRequest req) {
        return CategoryView.of(catalog.createCategory(req.name()));
    }

    @GetMapping("/categories")
    @PublicEndpoint
    public List<CategoryView> categories() {
        return catalog.listCategories().stream().map(CategoryView::of).toList();
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @AdminOnly
    public ProductView createProduct(@Valid @RequestBody ProductRequest req) {
        return ProductView.of(catalog.createProduct(req.name(), req.categoryId(), req.priceCents(), req.stock()));
    }

    @GetMapping("/products")
    @PublicEndpoint
    public PageView<ProductView> products(@RequestParam(required = false) Long categoryId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        Page<Product> result = catalog.listProducts(categoryId, page, size);
        return new PageView<>(result.map(ProductView::of).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/products/{id}")
    @PublicEndpoint
    public ProductView product(@PathVariable long id) {
        return ProductView.of(catalog.getProduct(id));
    }
}
