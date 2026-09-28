package com.jonet.demo.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jonet.demo.models.Product;

import jakarta.persistence.LockModeType;

public interface ProductRepository extends JpaRepository<Product, UUID> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select product from Product product where product.id = :productId")
	java.util.Optional<Product> findByIdForUpdate(@Param("productId") UUID productId);
}
