package com.jonet.demo.controller;

import java.security.Principal;

import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jonet.demo.models.OrderRequest;
import com.jonet.demo.service.OrderService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping ("/api/orders")
@RequiredArgsConstructor 
public class OrderController {
    private final OrderService orderService;

    @MessageMapping("/orders/count")
    public void getOrderCount(Principal principal) {
        orderService.getQuantityOrder(principal.getName());
    }

    @PostMapping 
    public ResponseEntity<?> createOrder(@RequestBody OrderRequest request, Principal principal) {
        try {
            return ResponseEntity.ok(orderService.placeOrder(request, principal.getName()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
