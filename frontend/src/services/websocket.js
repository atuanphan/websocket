// websocket.js
export function connectWebSocket(onMessage) {
  const socket = new WebSocket("ws://localhost:8080/ws");

  socket.onopen = () => console.log("connected");
  socket.onmessage = (e) => onMessage(JSON.parse(e.data)); // server gửi JSON
  socket.onclose = () => console.log("closed");
  socket.onerror = (err) => console.error(err);

  // trả về hàm cleanup + socket để component còn gửi tin được
  return {
    socket,
    disconnect: () => socket.close(),
  };
}