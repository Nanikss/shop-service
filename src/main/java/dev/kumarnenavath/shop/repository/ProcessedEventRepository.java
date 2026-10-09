package dev.kumarnenavath.shop.repository;

import dev.kumarnenavath.shop.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
