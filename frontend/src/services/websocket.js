export function connectWebSocket() {
  const socket = new WebSocket("ws://localhost:8080/hello");

  socket.onopen = () => {
    console.log("Đã kết nối WebSocket");
  };

  socket.onmessage = (event) => {
    console.log("Nhận được:", event.data);
  };

  socket.onerror = (error) => {
    console.error("WebSocket error:", error);
  };

  socket.onclose = () => {
    console.log("Đã đóng kết nối WebSocket");
  };

  return socket;
}