package dev.kumarnenavath.shop.repository;

import dev.kumarnenavath.shop.domain.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "lines")
    Optional<Order> findWithLinesById(long id);

    @EntityGraph(attributePaths = "lines")
    Optional<Order> findByIdAndUserId(long id, long userId);

    @EntityGraph(attributePaths = "lines")
    List<Order> findByUserIdOrderByIdDesc(long userId);
}
