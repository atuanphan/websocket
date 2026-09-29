package com.jonet.demo.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jonet.demo.models.Order;
import com.jonet.demo.models.User;


public interface OrderRepository extends JpaRepository<Order, UUID> {
    long countByUser(User user);
}