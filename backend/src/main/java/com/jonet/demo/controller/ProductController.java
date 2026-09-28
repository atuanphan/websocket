package com.jonet.demo.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jonet.demo.models.ProductResponse;
import com.jonet.demo.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@RestController 
@RequestMapping ("/api/products")
@RequiredArgsConstructor 
public class ProductController {
    private final ProductRepository productRepository;

    @GetMapping 
    public List<ProductResponse> getAll() {
        return productRepository.findAll().stream()
                .map(product -> new ProductResponse(product.getId(), product.getName(), product.getQuantity()))
                .toList();
    }
}
