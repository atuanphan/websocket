import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useEffect, useRef, useState } from 'react';

export function ProductList() {
    const clientRef = useRef(null);
    const [products, setProducts] = useState([]);

    // Load danh sách sản phẩm ban đầu qua REST API
    useEffect(() => {
        fetch('http://localhost:8080/api/products')
            .then((res) => res.json())
            .then((data) => setProducts(data));
    }, []);

    // Kết nối WebSocket để nhận cập nhật số lượng realtime
    useEffect(() => {
        const client = new Client({
            webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
            reconnectDelay: 5000,
            onConnect: () => {
                client.subscribe('/topic/stock', (msg) => {
                    const update = JSON.parse(msg.body);
                    // Cập nhật đúng sản phẩm trong danh sách, không load lại toàn bộ
                    setProducts((prev) =>
                        prev.map((p) =>
                            p.id === update.productId
                                ? { ...p, quantity: update.remainingQuantity }
                                : p
                        )
                    );
                });
            },
            onStompError: (frame) => console.error(frame),
        });

        client.activate();
        clientRef.current = client;

        return () => {
            client.deactivate();
        };
    }, []);

    // Gọi REST API để đặt hàng
    const handleOrder = async (productId) => {
        const res = await fetch('http://localhost:8080/api/orders', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ productId, quantity: 1 }),
        });

        if (!res.ok) {
            const errorText = await res.text();
            alert(errorText);
        }
        // Không cần tự cập nhật state ở đây — WebSocket sẽ tự đẩy số lượng mới xuống
    };

    return (
        <div>
            <h2>Danh sách sản phẩm</h2>
            {products.map((p) => (
                <div key={p.id} style={{ marginBottom: 8 }}>
                    <strong>{p.name}</strong> — Còn lại: {p.quantity}
                    <button
                        onClick={() => handleOrder(p.id)}
                        disabled={p.quantity <= 0}
                        style={{ marginLeft: 8 }}
                    >
                        Đặt mua
                    </button>
                </div>
            ))}
        </div>
    );
}