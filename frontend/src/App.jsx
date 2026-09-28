import { ProductList } from "./components/orders.jsx";
import { useState } from "react";
import "./App.css";

function App() {
  const [token, setToken] = useState(() => localStorage.getItem("accessToken"));
  const [username, setUsername] = useState(() => localStorage.getItem("username") || "");
  const [isRegistering, setIsRegistering] = useState(false);
  const [formUsername, setFormUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");
    setIsSubmitting(true);

    try {
      const endpoint = isRegistering ? "register" : "login";
      const response = await fetch(`http://localhost:8080/users/${endpoint}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: formUsername, password }),
      });

      if (!response.ok) {
        const message = await response.text();
        throw new Error(message || "Không thể xác thực tài khoản.");
      }

      const result = await response.json();
      localStorage.setItem("accessToken", result.accessToken);
      localStorage.setItem("username", formUsername.trim());
      setToken(result.accessToken);
      setUsername(formUsername.trim());
      setPassword("");
    } catch (requestError) {
      setError(requestError.message || "Không thể kết nối máy chủ.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleLogout = () => {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("username");
    setToken(null);
    setUsername("");
  };

  return (
    <main className="app-shell">
      <header className="topbar">
        <a className="brand" href="/" aria-label="Stockroom home">
          <span className="brand-mark" aria-hidden="true">S</span>
          <span>Stockroom</span>
        </a>
        {token && (
          <div className="account-bar">
            <span className="account-name">{username}</span>
            <button className="text-button" type="button" onClick={handleLogout}>
              Đăng xuất
            </button>
          </div>
        )}
      </header>

      {token ? (
        <section className="workspace">
          <div className="page-heading">
            <p className="eyebrow">KHO HÀNG TRỰC TUYẾN</p>
            <h1>Sản phẩm</h1>
          </div>
          <ProductList token={token} />
        </section>
      ) : (
        <section className="auth-layout">
          <div className="auth-intro">
            <p className="eyebrow">STOCKROOM / TÀI KHOẢN</p>
            <h1>Quản lý hàng hóa, gọn trong một nơi.</h1>
            <p className="intro-copy">Đăng nhập để xem tồn kho và nhận cập nhật số lượng theo thời gian thực.</p>
            <div className="inventory-preview" aria-hidden="true">
              <div className="preview-heading"><span>TÌNH TRẠNG KHO</span><span className="live-dot">LIVE</span></div>
              <div className="preview-row"><span>Thiết bị âm thanh</span><strong>24 <small>sp</small></strong></div>
              <div className="preview-row"><span>Phụ kiện máy tính</span><strong>08 <small>sp</small></strong></div>
              <div className="preview-row"><span>Thiết bị văn phòng</span><strong>16 <small>sp</small></strong></div>
            </div>
          </div>

          <div className="auth-panel">
            <div className="form-heading">
              <p className="eyebrow">CHÀO MỪNG</p>
              <h2>{isRegistering ? "Tạo tài khoản" : "Đăng nhập"}</h2>
              <p>{isRegistering ? "Tạo tài khoản để bắt đầu quản lý kho." : "Nhập thông tin tài khoản của bạn."}</p>
            </div>
            <form onSubmit={handleSubmit}>
              <label htmlFor="username">Username</label>
              <input
                autoComplete="username"
                id="username"
                name="username"
                onChange={(event) => setFormUsername(event.target.value)}
                placeholder="Nhập username"
                required
                value={formUsername}
              />
              <label htmlFor="password">Password</label>
              <input
                autoComplete={isRegistering ? "new-password" : "current-password"}
                id="password"
                name="password"
                onChange={(event) => setPassword(event.target.value)}
                placeholder="Nhập password"
                required
                type="password"
                value={password}
              />
              {error && <p className="form-error" role="alert">{error}</p>}
              <button className="submit-button" disabled={isSubmitting} type="submit">
                {isSubmitting ? "Đang xử lý..." : isRegistering ? "Tạo tài khoản" : "Đăng nhập"}
              </button>
            </form>
            <p className="form-switch">
              {isRegistering ? "Đã có tài khoản?" : "Chưa có tài khoản?"}{" "}
              <button
                className="text-button"
                onClick={() => { setIsRegistering((current) => !current); setError(""); }}
                type="button"
              >
                {isRegistering ? "Đăng nhập" : "Đăng ký"}
              </button>
            </p>
          </div>
        </section>
      )}
    </main>
  );
}

export default App;