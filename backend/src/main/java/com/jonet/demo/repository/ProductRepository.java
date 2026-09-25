package com.jonet.demo.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jonet.demo.models.Product;

public interface ProductRepository extends JpaRepository<Product, UUID> {
}
