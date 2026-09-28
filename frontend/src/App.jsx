import { useEffect, useState } from "react";
import { connectWebSocket } from "./services/websocket";

function App() {
  const [stock, setStock] = useState();

  useEffect(() => {
    const disconnect = connectWebSocket();
    return () => disconnect(); // cleanup khi unmount
  }, []);

  return (
    <div>
      <h1>WebSocket Test</h1>
      <p></p>
    </div>
  );
}

export default App;