package dev.kumarnenavath.shop.repository;

import dev.kumarnenavath.shop.domain.CartItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @EntityGraph(attributePaths = "product")
    List<CartItem> findByUserIdOrderByIdAsc(long userId);

    @EntityGraph(attributePaths = "product")
    Optional<CartItem> findByUserIdAndProductId(long userId, long productId);
}
