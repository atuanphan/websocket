package com.jonet.demo.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.jonet.demo.models.Order;
import com.jonet.demo.models.OrderItem;
import com.jonet.demo.models.OrderRequest;
import com.jonet.demo.models.OrderResponse;
import com.jonet.demo.models.Product;
import com.jonet.demo.models.StockUpdateMessage;
import com.jonet.demo.models.User;
import com.jonet.demo.repository.OrderItemRepository;
import com.jonet.demo.repository.OrderRepository;
import com.jonet.demo.repository.ProductRepository;
import com.jonet.demo.repository.UserReopository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor 
public class OrderService {
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserReopository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional 
    public OrderResponse placeOrder(OrderRequest request, String username) {
        if (request == null || request.getProductId() == null
                || request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Số lượng đặt phải lớn hơn 0");
        }

        Product product = productRepository.findByIdForUpdate(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại"));

        if (product.getQuantity() < request.getQuantity()) {
            throw new RuntimeException("Không đủ hàng trong kho");
        }

        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("Không tìm thấy tài khoản đặt hàng");
        }

        product.setQuantity(product.getQuantity() - request.getQuantity());
        productRepository.save(product);

        Order order = orderRepository.save(Order.builder()
                .user(user)
                .quantity(request.getQuantity())
                .build());

        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setProduct(product);
        orderItem.setQuantity(request.getQuantity());
        orderItemRepository.save(orderItem);

        StockUpdateMessage update = StockUpdateMessage.builder()
                .productId(product.getId())
                .remainingQuantity(product.getQuantity())
                .build();
        
        messagingTemplate.convertAndSend("/topic/stock", update);

        getQuantityOrder(username);

        return new OrderResponse(
                order.getId(), user.getUsername(), product.getId(), product.getName(),
                request.getQuantity(), product.getQuantity());
    }

    public void getQuantityOrder(String username) {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("Không tìm thấy tài khoản");
        }
        Long quantity = orderRepository.countByUser(user);
        messagingTemplate.convertAndSendToUser(user.getUsername(), "/topic/notify", quantity);
    }
}
