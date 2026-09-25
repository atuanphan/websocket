import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useEffect, useRef, useState } from 'react';

export function ChatComponent() {
    const clientRef = useRef(null);
    const [messages, setMessages] = useState([]);

    useEffect(() => {
        const client = new Client({
            webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
            reconnectDelay: 5000,
            onConnect: () => {
                client.subscribe('/topic/messages', (msg) => {
                    setMessages((prev) => [...prev, JSON.parse(msg.body)]);
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

    const sendMessage = (text) => {
        clientRef.current.publish({
            destination: '/app/chat.send',
            body: JSON.stringify({ content: text }),
        });
    };

    return (
        <div>
            {messages.map((m, i) => <div key={i}>{m.content}</div>)}
            <button onClick={() => sendMessage('Hello')}>Gửi</button>
        </div>
    );
}