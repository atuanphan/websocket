import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useEffect, useRef, useState } from 'react';

export function ProductList({ token }) {
    const clientRef = useRef(null);
    const [products, setProducts] = useState([]);
    const [productsError, setProductsError] = useState('');
    const [orders, setOrders] = useState([]);
    const [orderingProductId, setOrderingProductId] = useState(null);
    const [orderNotice, setOrderNotice] = useState(null);

    // Load danh sách sản phẩm ban đầu qua REST API
    useEffect(() => {
        fetch('http://localhost:8080/api/products', {
            headers: { Authorization: `Bearer ${token}` },
        })
            .then((res) => {
                if (!res.ok) throw new Error(`Không thể tải danh sách sản phẩm (HTTP ${res.status}).`);
                return res.json();
            })
            .then((data) => {
                setProducts(data);
                setProductsError('');
            })
            .catch((error) => setProductsError(error.message || 'Không thể kết nối máy chủ.'));
    }, [token]);

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
        setOrderingProductId(productId);
        setOrderNotice(null);
        try {
            const res = await fetch('http://localhost:8080/api/orders', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    Authorization: `Bearer ${token}`,
                },
                body: JSON.stringify({ productId, quantity: 1 }),
            });

            if (!res.ok) {
                throw new Error(await res.text());
            }

            const order = await res.json();
            setProducts((current) => current.map((product) =>
                product.id === order.productId
                    ? { ...product, quantity: order.remainingQuantity }
                    : product
            ));
            setOrders((current) => [
                { ...order, placedAt: new Date().toLocaleString('vi-VN') },
                ...current,
            ]);
            setOrderNotice({
                type: 'success',
                message: `Đặt hàng thành công: ${order.productName} × ${order.quantity}`,
            });
        } catch (error) {
            setOrderNotice({ type: 'error', message: error.message || 'Không thể đặt hàng.' });
        } finally {
            setOrderingProductId(null);
        }
    };

    return (
        <div className="inventory-view">
            <section className="inventory-section" aria-labelledby="products-heading">
                <div className="section-heading">
                    <h2 id="products-heading">Danh sách sản phẩm</h2>
                    <span>{products.length} sản phẩm</span>
                </div>
                {productsError && <p className="order-notice error" role="alert">{productsError}</p>}
                {orderNotice && (
                    <p className={`order-notice ${orderNotice.type}`} role={orderNotice.type === 'error' ? 'alert' : 'status'}>
                        {orderNotice.message}
                    </p>
                )}
                <div className="product-table-wrap">
                    <table className="product-table">
                        <thead>
                            <tr><th>Sản phẩm</th><th>Tồn kho</th><th></th></tr>
                        </thead>
                        <tbody>
                            {products.map((product) => (
                                <tr key={product.id}>
                                    <td className="product-name">{product.name}</td>
                                    <td>{product.quantity}</td>
                                    <td className="order-action">
                                        <button
                                            className="order-button"
                                            onClick={() => handleOrder(product.id)}
                                            disabled={product.quantity <= 0 || orderingProductId !== null}
                                        >
                                            {orderingProductId === product.id ? 'Đang đặt...' : 'Đặt mua'}
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </section>

            <section className="orders-section" aria-labelledby="orders-heading">
                <div className="section-heading">
                    <h2 id="orders-heading">Đơn hàng vừa đặt</h2>
                    <span>{orders.length} đơn</span>
                </div>
                {orders.length === 0 ? (
                    <p className="empty-orders">Đơn hàng mới sẽ hiển thị tại đây.</p>
                ) : (
                    <div className="product-table-wrap">
                        <table className="product-table order-table">
                            <thead>
                                <tr><th>Mã đơn</th><th>Sản phẩm</th><th>SL</th><th>Thời gian</th></tr>
                            </thead>
                            <tbody>
                                {orders.map((order) => (
                                    <tr key={order.orderId}>
                                        <td><code title={order.orderId}>{order.orderId.slice(0, 8)}</code></td>
                                        <td className="product-name">{order.productName}</td>
                                        <td>{order.quantity}</td>
                                        <td className="order-time">{order.placedAt}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </section>
        </div>
    );
}