package com.jonet.demo.models;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

@Entity 
@Table(name = "products")
@Data 
public class Product {
    @Id 
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private UUID id;

    private String name;
    private Integer quantity;

    @OneToMany(mappedBy = "product")
    private List<OrderItem> orderItems;
}