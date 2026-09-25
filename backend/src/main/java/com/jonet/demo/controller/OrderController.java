package com.jonet.demo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jonet.demo.models.OrderRequest;
import com.jonet.demo.models.Product;
import com.jonet.demo.service.OrderService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping ("/api/orders")
@RequiredArgsConstructor 
public class OrderController {
    private final OrderService orderService;

    @PostMapping 
    public ResponseEntity<?> createOrder(@RequestBody OrderRequest request) {
        try {
            Product updated = orderService.placeOrder(request);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
