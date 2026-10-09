package dev.kumarnenavath.shop.repository;

import dev.kumarnenavath.shop.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = "category")
    Optional<Product> findWithCategoryById(long id);

    @EntityGraph(attributePaths = "category")
    Page<Product> findByCategoryId(long categoryId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Pageable pageable);
}
