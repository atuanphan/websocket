import { ChatComponent } from "./components/chat.jsx";
import { ProductList } from "./components/orders.jsx";

function App() {
  return (
    <div>
      <h1>WebSocket Test</h1>
      <ChatComponent />
      <h1>Order Test</h1>
      <ProductList />
    </div>
  );
}

export default App;