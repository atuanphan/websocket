package com.jonet.demo.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jonet.demo.models.Order;

public interface OrderRepository extends JpaRepository<Order, UUID> {
}