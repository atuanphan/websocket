package com.jonet.demo.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.jonet.demo.models.OrderRequest;
import com.jonet.demo.models.Product;
import com.jonet.demo.models.StockUpdateMessage;
import com.jonet.demo.repository.ProductRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor 
public class OrderService {
    private final ProductRepository productRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional 
    public Product placeOrder(OrderRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại"));

        if (product.getQuantity() < request.getQuantity()) {
            throw new RuntimeException("Không đủ hàng trong kho");
        }

        product.setQuantity(product.getQuantity() - request.getQuantity());
        productRepository.save(product);

        StockUpdateMessage update = StockUpdateMessage.builder()
                .productId(product.getId())
                .remainingQuantity(product.getQuantity())
                .build();
        messagingTemplate.convertAndSend("/topic/stock", update);

        return product;
    }
}
