# WebSocket: Tài liệu tổng hợp đầy đủ

> Tài liệu này đi từ nền tảng giao thức (RFC 6455) đến thực hành với Spring Boot: handshake, frame, vòng đời, STOMP, bảo mật JWT, scale, debug và demo "bấm mua hàng trừ tồn kho realtime".

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [So sánh với các kỹ thuật realtime khác](#2-so-sánh-với-các-kỹ-thuật-realtime-khác)
3. [Handshake chi tiết](#3-handshake-chi-tiết)
4. [Cấu trúc frame](#4-cấu-trúc-frame)
5. [Vòng đời của một kết nối](#5-vòng-đời-của-một-kết-nối)
6. [Mã đóng (close code)](#6-mã-đóng-close-code)
7. [Heartbeat, timeout và tự kết nối lại](#7-heartbeat-timeout-và-tự-kết-nối-lại)
8. [Browser API](#8-browser-api)
9. [Spring Boot: WebSocket thuần](#9-spring-boot-websocket-thuần)
10. [STOMP](#10-stomp)
11. [SockJS](#11-sockjs)
12. [Bảo mật và JWT](#12-bảo-mật-và-jwt)
13. [Scale và triển khai thực tế](#13-scale-và-triển-khai-thực-tế)
14. [Demo: bấm mua hàng, trừ tồn kho realtime](#14-demo-bấm-mua-hàng-trừ-tồn-kho-realtime)
15. [Test và debug](#15-test-và-debug)
16. [Lỗi thường gặp](#16-lỗi-thường-gặp)
17. [Khi nào dùng, khi nào không](#17-khi-nào-dùng-khi-nào-không)
18. [Cheat sheet](#18-cheat-sheet)
19. [Tài liệu tham khảo](#19-tài-liệu-tham-khảo)

---

## 1. Tổng quan

**WebSocket** là giao thức truyền thông hai chiều (full-duplex) chạy trên một kết nối TCP duy nhất, được chuẩn hóa trong **RFC 6455** (2011). Nó bắt đầu bằng một HTTP request rồi "nâng cấp" (upgrade) sang giao thức WebSocket.

| Đặc điểm | WebSocket |
|---|---|
| Mô hình | Hai chiều, cả client và server gửi bất cứ lúc nào |
| Kết nối | Một kết nối TCP sống lâu, không đóng sau mỗi lần trao đổi |
| Server đẩy dữ liệu | Có (push trực tiếp, không cần client hỏi) |
| Overhead mỗi message | Rất nhỏ (header frame 2 đến 14 byte) |
| Định dạng dữ liệu | Text (UTF-8) hoặc binary |
| Địa chỉ | `ws://` (mặc định cổng 80), `wss://` (TLS, mặc định cổng 443) |
| Use case | Chat, notification, game, dashboard realtime, cộng tác trực tuyến, giá cổ phiếu/crypto |

### Vì sao cần WebSocket?

HTTP truyền thống là **request-response**: chỉ client được chủ động hỏi. Muốn có dữ liệu "mới" thì phải:

- **Polling**: cứ vài giây gọi API một lần. Tốn request, trễ, phần lớn request trả về rỗng.
- **Long polling**: request treo đến khi có dữ liệu rồi trả về, client gọi lại ngay. Đỡ tốn hơn nhưng vẫn mỗi lần một request đầy đủ header.

WebSocket giải quyết bằng cách giữ một kênh mở, dữ liệu chảy hai chiều ngay khi có.

### Hai lớp cần phân biệt

```
Tầng ứng dụng   :  STOMP / WAMP / JSON tự định nghĩa   (ý nghĩa của message)
Tầng giao thức  :  WebSocket (frame, handshake, ping/pong, close)
Tầng vận chuyển :  TCP (+ TLS nếu dùng wss)
```

WebSocket chỉ lo việc **vận chuyển frame**. Nó không quy định message nghĩa là gì, gửi cho ai, hay pub/sub như thế nào. Đó là việc của tầng trên (ví dụ STOMP hoặc JSON do bạn tự đặt).

---

## 2. So sánh với các kỹ thuật realtime khác

| Tiêu chí | Polling | Long polling | SSE | WebSocket |
|---|---|---|---|---|
| Hướng dữ liệu | Client hỏi | Client hỏi (treo) | Server → client | Hai chiều |
| Giao thức | HTTP | HTTP | HTTP (`text/event-stream`) | WebSocket (sau upgrade từ HTTP) |
| Độ trễ | Cao | Trung bình | Thấp | Thấp |
| Overhead mỗi message | Cao (header HTTP) | Cao | Thấp | Rất thấp |
| Tự reconnect | Không cần | Tự viết | Có sẵn (`EventSource`) | Tự viết |
| Dữ liệu binary | Có | Có | Không (chỉ text) | Có |
| Đi qua proxy/firewall | Dễ | Dễ | Dễ | Có thể bị chặn nếu proxy cũ |
| Độ phức tạp | Thấp | Trung bình | Thấp | Cao hơn (quản lý kết nối, scale) |

Gợi ý chọn nhanh:

- Chỉ cần server đẩy một chiều (thông báo, tiến độ, feed): **SSE** thường đơn giản hơn.
- Cần hai chiều, độ trễ thấp (chat, game, cộng tác, đặt lệnh giao dịch): **WebSocket**.
- Tần suất cập nhật rất thấp (vài phút một lần): polling là đủ.

---

## 3. Handshake chi tiết

### 3.1. Request của client

Khi frontend gọi:

```js
const ws = new WebSocket("ws://localhost:8080/ws");
```

trình duyệt gửi một HTTP GET đặc biệt:

```http
GET /ws HTTP/1.1
Host: localhost:8080
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
Sec-WebSocket-Version: 13
Origin: http://localhost:3000
```

| Header | Ý nghĩa |
|---|---|
| `Upgrade: websocket` + `Connection: Upgrade` | Yêu cầu đổi giao thức |
| `Sec-WebSocket-Key` | 16 byte ngẫu nhiên mã hóa Base64, dùng để server chứng minh nó hiểu WebSocket |
| `Sec-WebSocket-Version` | Luôn là `13` (phiên bản của RFC 6455) |
| `Origin` | Trang web đang mở kết nối (do trình duyệt tự gắn, JavaScript không sửa được) |
| `Sec-WebSocket-Protocol` (tùy chọn) | Danh sách subprotocol client hỗ trợ, ví dụ `v12.stomp, v11.stomp` |
| `Sec-WebSocket-Extensions` (tùy chọn) | Extension client hỗ trợ, ví dụ `permessage-deflate` (nén message, RFC 7692) |

### 3.2. Response của server

Nếu đồng ý, server trả `101` thay vì `200 OK`:

```http
HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
```

Nếu client gửi `Sec-WebSocket-Protocol`, server chọn **một** giá trị và trả lại trong header cùng tên. Nếu không có giá trị nào hợp, client sẽ đóng kết nối.

### 3.3. Cách tính `Sec-WebSocket-Accept`

```
Accept = Base64( SHA-1( Sec-WebSocket-Key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11" ) )
```

`258EAFA5-E914-47DA-95CA-C5AB0DC85B11` là GUID cố định trong RFC 6455. Cơ chế này chứng minh server thật sự hiểu WebSocket, tránh trường hợp một server HTTP thường (hoặc proxy cache) trả bừa response. Nó **không phải cơ chế bảo mật** chống kẻ tấn công.

### 3.4. Sơ đồ handshake

```
Client                                Server
  |  GET /ws HTTP/1.1                   |
  |  Upgrade: websocket                 |
  |  Connection: Upgrade                |
  |  Sec-WebSocket-Key: xxxxx           |
  |  Sec-WebSocket-Version: 13          |
  |------------------------------------>|
  |                                     |
  |  HTTP/1.1 101 Switching Protocols   |
  |  Upgrade: websocket                 |
  |  Connection: Upgrade                |
  |  Sec-WebSocket-Accept: yyyyy        |
  |<------------------------------------|
  |                                     |
  |  === Kết nối WebSocket đã mở ===    |
```

Sau `101`, cùng kết nối TCP đó không còn nói HTTP nữa (không còn method, status code, header kiểu HTTP) mà chỉ trao đổi **frame**.

### 3.5. Handshake thất bại

- Server trả mã khác `101` (ví dụ `400`, `401`, `403`, `404`) thì kết nối không thành công.
- Server không hỗ trợ version client yêu cầu thì trả lỗi HTTP phù hợp (ví dụ `426 Upgrade Required`) kèm `Sec-WebSocket-Version` liệt kê version hỗ trợ.
- **Trình duyệt không cho JavaScript đọc mã lỗi HTTP** của handshake (vì lý do bảo mật). Bạn chỉ thấy `onerror` rồi `onclose` với code `1006`. Muốn biết nguyên nhân phải xem tab Network hoặc log server.

### 3.6. Origin và CORS

- WebSocket **không** áp dụng CORS của trình duyệt. Trang ở `evil.com` vẫn có thể mở `new WebSocket("wss://your-api.com/ws")`, và trình duyệt sẽ tự gửi kèm cookie của `your-api.com`.
- Vì vậy server **phải tự kiểm tra `Origin`** (chống Cross-Site WebSocket Hijacking, CSWSH).
- Trong Spring: `setAllowedOrigins("https://your-site.com")`. Không nên để `"*"` ở production, đặc biệt khi xác thực bằng cookie.

---

## 4. Cấu trúc frame

Sau handshake, dữ liệu được chia thành các **frame**:

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-------+-+-------------+-------------------------------+
|F|R|R|R| opcode|M| Payload len |    Extended payload length    |
|I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
|N|V|V|V|       |S|             |   (nếu payload len = 126/127) |
| |1|2|3|       |K|             |                               |
+-+-+-+-+-------+-+-------------+-------------------------------+
|     Masking-key (0 hoặc 4 byte, có khi MASK = 1)              |
+---------------------------------------------------------------+
|                          Payload data                         |
+---------------------------------------------------------------+
```

| Trường | Ý nghĩa |
|---|---|
| `FIN` | `1` nếu đây là frame cuối của message |
| `RSV1-3` | Dành cho extension (ví dụ `permessage-deflate` dùng RSV1). Bình thường là 0 |
| `opcode` | Loại frame (bảng dưới) |
| `MASK` | `1` nếu payload bị mask |
| `Payload len` | 0 đến 125: độ dài thật. `126`: độ dài nằm ở 2 byte tiếp theo. `127`: độ dài nằm ở 8 byte tiếp theo |
| `Masking-key` | 4 byte ngẫu nhiên nếu `MASK = 1` |

### 4.1. Opcode

| Opcode | Loại | Ghi chú |
|---|---|---|
| `0x0` | Continuation | Frame tiếp theo của một message bị chia nhỏ |
| `0x1` | Text | Payload là UTF-8 hợp lệ |
| `0x2` | Binary | Dữ liệu nhị phân bất kỳ |
| `0x8` | Close | Bắt đầu/đáp lại quá trình đóng |
| `0x9` | Ping | Kiểm tra kết nối còn sống |
| `0xA` | Pong | Đáp lại ping |

### 4.2. Masking

- Mọi frame **từ client lên server bắt buộc phải mask**; frame từ server xuống client **không** mask.
- Thuật toán: `masked[i] = original[i] XOR key[i mod 4]`.
- Mục đích không phải mã hóa, mà để tránh proxy trung gian bị "đầu độc cache" bởi dữ liệu client kiểm soát được. Server nhận frame client không mask phải đóng kết nối.
- Muốn bảo mật nội dung thì dùng `wss://` (TLS).

### 4.3. Phân mảnh (fragmentation)

Một message lớn có thể chia thành nhiều frame:

```
Frame 1: FIN=0, opcode=0x1 (text)          <- frame đầu
Frame 2: FIN=0, opcode=0x0 (continuation)
Frame 3: FIN=1, opcode=0x0 (continuation)  <- frame cuối
```

- Frame điều khiển (close, ping, pong) **không được phân mảnh** và payload tối đa 125 byte. Chúng có thể xen giữa các fragment của một message.
- Ứng dụng thường không phải lo phần này, thư viện gộp lại thành một message hoàn chỉnh.

---

## 5. Vòng đời của một kết nối

```
Client                                   Server
  |--- HTTP GET + Upgrade: websocket --->|
  |<-- 101 Switching Protocols ----------|   (1. handshake)
  |                                      |   (2. open)
  |<========= text/binary frames =======>|   (3. trao đổi dữ liệu, hai chiều)
  |<--------- ping / pong -------------->|   (4. heartbeat)
  |                                      |
  |--- close frame (1000) -------------->|
  |<-- close frame ----------------------|   (5. close)
  |            TCP closed                |
```

### 5.1. Handshake

Xem [mục 3](#3-handshake-chi-tiết). Trạng thái phía client: `readyState = 0 (CONNECTING)`.

### 5.2. Open

- Sau khi nhận `101`, `ws.readyState` chuyển sang `1 (OPEN)` và sự kiện `onopen` chạy:

```js
ws.onopen = () => {
  console.log("Đã kết nối");
  ws.send(JSON.stringify({ type: "subscribe", topic: "products" }));
};
```

- Phía backend, một **session** được tạo và lưu lại (thường trong `Set`/`Map`) để biết gửi cho ai. Trong Spring, hook tương ứng là `afterConnectionEstablished(session)`.

### 5.3. Trao đổi dữ liệu (full-duplex)

- Cả frontend và backend đều gửi được bất cứ lúc nào, không cần request trước.
- Frontend nhận bằng `onmessage`:

```js
ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  // cập nhật UI, ví dụ số lượng tồn kho mới
};
```

- Backend nhận trong `handleTextMessage(session, message)`, xử lý xong thì gửi lại cho một session (`session.sendMessage(...)`) hoặc broadcast cho tất cả session đang mở.

### 5.4. Heartbeat

Xem [mục 7](#7-heartbeat-timeout-và-tự-kết-nối-lại).

### 5.5. Close

Đóng cũng là một quá trình handshake:

1. Bên A gửi frame close kèm mã và lý do, ví dụ `ws.close(1000, "bye")`. Lúc này `readyState = 2 (CLOSING)`.
2. Bên B nhận được, gửi frame close đáp lại.
3. TCP được đóng. `readyState = 3 (CLOSED)`, sự kiện `onclose` chạy.

```js
ws.onclose = (e) => console.log(e.code, e.reason, e.wasClean);
```

- Payload của close frame gồm 2 byte mã trạng thái và lý do (UTF-8, tối đa 123 byte).
- Backend nhận `afterConnectionClosed(session, status)` và **phải xóa session khỏi danh sách**, nếu không sẽ rò rỉ bộ nhớ và cố gửi vào session đã chết.

### 5.6. Bảng trạng thái `readyState`

| Giá trị | Hằng số | Ý nghĩa |
|---|---|---|
| 0 | `CONNECTING` | Đang handshake |
| 1 | `OPEN` | Sẵn sàng gửi/nhận |
| 2 | `CLOSING` | Đang thực hiện close handshake |
| 3 | `CLOSED` | Đã đóng hoặc không mở được |

Gọi `ws.send()` khi `readyState` khác `OPEN` sẽ lỗi (khi `CONNECTING`) hoặc bị bỏ qua (khi đã đóng). Nên kiểm tra `ws.readyState === WebSocket.OPEN` trước khi gửi.

### 5.7. Sự kiện lỗi

`onerror` chạy khi có lỗi (không có thông tin chi tiết), thường theo sau là `onclose`. Handshake thất bại cũng cho ra `onerror` rồi `onclose` với code `1006`.

---

## 6. Mã đóng (close code)

| Mã | Ý nghĩa | Ghi chú |
|---|---|---|
| `1000` | Đóng bình thường | |
| `1001` | Going away | Đóng tab, server tắt/restart |
| `1002` | Lỗi giao thức | |
| `1003` | Dữ liệu không hỗ trợ | Ví dụ nhận binary khi chỉ chấp nhận text |
| `1005` | Không có mã trạng thái | Chỉ dùng nội bộ, **không** gửi trên đường truyền |
| `1006` | Đóng bất thường | Không có close frame (rớt mạng, server crash, handshake lỗi). Chỉ do trình duyệt báo, **không** gửi trên đường truyền |
| `1007` | Dữ liệu sai định dạng | Ví dụ text không phải UTF-8 hợp lệ |
| `1008` | Vi phạm policy | Thường dùng khi từ chối vì xác thực/phân quyền |
| `1009` | Message quá lớn | |
| `1010` | Thiếu extension | Client mong đợi server hỗ trợ extension |
| `1011` | Lỗi nội bộ server | |
| `1012` | Service restart | |
| `1013` | Try again later | Server quá tải tạm thời |
| `1015` | TLS handshake lỗi | Chỉ dùng nội bộ, không gửi |
| `3000-3999` | Đăng ký với IANA | Dành cho thư viện/framework |
| `4000-4999` | **Tự do sử dụng** | Ứng dụng tự định nghĩa (ví dụ `4001` = token hết hạn) |

Khi gọi `ws.close(code, reason)` từ trình duyệt, `code` chỉ được là `1000` hoặc trong khoảng `3000-4999`, và `reason` tối đa 123 byte UTF-8, nếu không sẽ ném lỗi.

Ứng dụng nên dùng mã `4xxx` riêng để client biết có nên tự kết nối lại hay không. Ví dụ `4401` (token hết hạn) thì làm mới token rồi mới reconnect, còn `1008` thì không reconnect.

---

## 7. Heartbeat, timeout và tự kết nối lại

### 7.1. Vì sao cần heartbeat

Kết nối có thể "chết im lặng" (mất mạng, chuyển Wi-Fi sang 4G, NAT/proxy/load balancer cắt vì idle timeout, thường khoảng 60 giây) mà cả hai bên không biết vì không có close frame nào được gửi.

### 7.2. Ping/pong ở tầng WebSocket

- Một bên gửi frame `ping`, bên kia phải trả `pong`. Trình duyệt **tự trả pong**, bạn không cần code.
- Trình duyệt **không cho JavaScript gửi ping** trực tiếp. Vì vậy frontend thường gửi một message ứng dụng (`{ "type": "ping" }`) theo chu kỳ, server trả `pong` tương ứng.
- Server (Spring) có thể chủ động gửi ping: `session.sendMessage(new PingMessage())`, và nhận pong ở `handlePongMessage`.
- Nếu quá thời gian không nhận được phản hồi thì coi như kết nối đã chết và đóng lại.
- Chu kỳ khuyến nghị: nhỏ hơn idle timeout của proxy/load balancer (ví dụ 20 đến 30 giây nếu timeout là 60 giây).

### 7.3. Heartbeat của STOMP

STOMP có heartbeat riêng ở tầng giao thức, thỏa thuận qua header `heart-beat` khi `CONNECT`. Xem [mục 10.8](#108-heartbeat-của-stomp).

### 7.4. Tự kết nối lại

WebSocket **không tự reconnect**. Bạn phải tự viết, nên dùng **exponential backoff kèm jitter** để khi server sập, hàng nghìn client không đồng loạt dồn vào kết nối lại:

```js
let retry = 0;

function connect() {
  const ws = new WebSocket("ws://localhost:8080/ws");

  ws.onopen = () => {
    retry = 0; // kết nối được thì reset
  };

  ws.onclose = (e) => {
    if (e.code === 1000) return; // đóng chủ động, không reconnect
    const base = Math.min(1000 * 2 ** retry, 30000); // tối đa 30 giây
    const jitter = Math.random() * 1000;
    retry++;
    setTimeout(connect, base + jitter);
  };
}
```

Lưu ý khi reconnect:

- **Đăng ký lại (re-subscribe)** các topic sau khi mở lại, vì server đã mất session cũ.
- **Đồng bộ lại trạng thái**: trong lúc mất kết nối có thể đã lỡ message. Cách thường dùng là gọi một REST API lấy trạng thái mới nhất sau khi kết nối lại (xem [mục 14.5](#145-tránh-mất-cập-nhật-khi-tải-trang)).
- Nếu gửi message quan trọng, cần cơ chế **ack** và **idempotency** (gắn `messageId`) để không xử lý trùng khi gửi lại.

---

## 8. Browser API

```js
const ws = new WebSocket(url, protocols?);
```

| Thành phần | Mô tả |
|---|---|
| `ws.send(data)` | Gửi `string`, `Blob`, `ArrayBuffer` hoặc typed array |
| `ws.close(code?, reason?)` | Bắt đầu close handshake |
| `ws.readyState` | Trạng thái, xem [mục 5.6](#56-bảng-trạng-thái-readystate) |
| `ws.bufferedAmount` | Số byte đã gọi `send()` nhưng chưa đẩy ra mạng |
| `ws.binaryType` | `"blob"` (mặc định) hoặc `"arraybuffer"`, kiểu dữ liệu binary nhận được |
| `ws.protocol` | Subprotocol mà server đã chọn |
| `ws.extensions` | Extension đã thương lượng |
| `ws.url` | URL đã kết nối |
| `onopen` / `onmessage` / `onerror` / `onclose` | Các sự kiện (có thể dùng `addEventListener`) |

### Kiểm soát tốc độ gửi (backpressure)

`send()` không chặn và không báo khi mạng chậm, dữ liệu được xếp hàng trong bộ nhớ. Nếu gửi liên tục dữ liệu lớn, hãy kiểm tra `bufferedAmount`:

```js
function sendWhenReady(ws, data) {
  if (ws.bufferedAmount > 1_000_000) {
    setTimeout(() => sendWhenReady(ws, data), 50);
  } else {
    ws.send(data);
  }
}
```

### Gửi dữ liệu binary

```js
ws.binaryType = "arraybuffer";
ws.send(new Uint8Array([1, 2, 3]));
ws.onmessage = (e) => {
  if (e.data instanceof ArrayBuffer) { /* xử lý binary */ }
  else { /* string */ }
};
```

### Hạn chế của API trình duyệt

- **Không gắn được header tùy ý** (như `Authorization`) vào handshake. Đây là lý do JWT thường đi qua STOMP `CONNECT` hoặc query/subprotocol (xem [mục 12](#12-bảo-mật-và-jwt)).
- Không gửi được ping frame, không đọc được mã lỗi HTTP của handshake.
- Không có backpressure thật sự (chỉ có `bufferedAmount`).

---

## 9. Spring Boot: WebSocket thuần

Có hai cách trong Spring, khác nhau ở mức trừu tượng:

| | WebSocket thuần | STOMP over WebSocket |
|---|---|---|
| Bạn tự làm | Quản lý danh sách session, định dạng message, routing | Ít hơn, Spring lo phần lớn |
| Ưu điểm | Nhẹ, kiểm soát tối đa, dễ hiểu bản chất | Pub/sub, destination, `@MessageMapping`, user-specific message, broker ngoài |
| Phù hợp | Học, giao thức tự định nghĩa, client không phải web | Ứng dụng web thông thường, chat, notification |

### 9.1. Dependency

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

### 9.2. Cấu hình endpoint

```java
@Configuration
@EnableWebSocket
public class RawWebSocketConfig implements WebSocketConfigurer {

    private final RawHandler rawHandler;

    public RawWebSocketConfig(RawHandler rawHandler) {
        this.rawHandler = rawHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(rawHandler, "/ws")
                .setAllowedOrigins("http://localhost:3000");
    }
}
```

### 9.3. Handler: bốn hook ứng với vòng đời

```java
@Component
public class RawHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // bọc để gửi an toàn từ nhiều thread
        sessions.add(new ConcurrentWebSocketSessionDecorator(session, 10_000, 512 * 1024));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        broadcast(message.getPayload());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) throws Exception {
        session.close(CloseStatus.SERVER_ERROR);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.removeIf(s -> s.getId().equals(session.getId()));
    }

    public void broadcast(String payload) {
        for (WebSocketSession s : sessions) {
            try {
                if (s.isOpen()) s.sendMessage(new TextMessage(payload));
            } catch (IOException e) {
                // session lỗi: bỏ qua, afterConnectionClosed sẽ dọn
            }
        }
    }
}
```

Điểm cần nhớ:

- `WebSocketSession.sendMessage` **không thread-safe**. Nếu nhiều thread cùng gửi cho một session sẽ lỗi hoặc rối message. Dùng `ConcurrentWebSocketSessionDecorator(session, sendTimeLimit, bufferSizeLimit)` để tuần tự hóa việc gửi và cắt client quá chậm.
- Danh sách session phải là cấu trúc thread-safe (`ConcurrentHashMap.newKeySet()`).
- Luôn dọn session ở `afterConnectionClosed`.
- Một client chậm có thể làm nghẽn cả vòng broadcast; hãy đặt giới hạn thời gian/bộ đệm như trên.

### 9.4. Xác thực ở handshake: `HandshakeInterceptor`

```java
registry.addHandler(rawHandler, "/ws")
        .addInterceptors(new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                           WebSocketHandler wsHandler, Map<String, Object> attributes) {
                // đọc cookie/query, kiểm tra quyền; trả false để từ chối (handshake fail)
                attributes.put("userId", "u1"); // truyền dữ liệu vào session.getAttributes()
                return true;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Exception ex) { }
        })
        .setAllowedOrigins("http://localhost:3000");
```

### 9.5. Giới hạn kích thước message

```java
@Bean
public ServletServerContainerFactoryBean createWebSocketContainer() {
    ServletServerContainerFactoryBean c = new ServletServerContainerFactoryBean();
    c.setMaxTextMessageBufferSize(64 * 1024);
    c.setMaxBinaryMessageBufferSize(64 * 1024);
    c.setMaxSessionIdleTimeout(60_000L); // ms
    return c;
}
```

---

## 10. STOMP

**STOMP** (Simple Text Oriented Messaging Protocol) là một **subprotocol** chạy trên WebSocket. Nó định nghĩa cách client và server hiểu nhau: lệnh nào, gửi đến đâu, nội dung là gì, giúp có mô hình **pub/sub** mà WebSocket thuần không có.

### 10.1. Cấu trúc frame STOMP

Một frame gồm: dòng lệnh, các header, một dòng trống, body, kết thúc bằng ký tự null (`^@`):

```
SEND
destination:/app/chat-send
content-type:application/json

{"content":"Hello"}^@
```

Server đọc được:

- Đây là lệnh `SEND`
- Gửi tới destination `/app/chat-send`
- Nội dung (body) là `{"content":"Hello"}`

### 10.2. Các lệnh chính

| Hướng | Lệnh | Ý nghĩa |
|---|---|---|
| Client → server | `CONNECT` / `STOMP` | Mở phiên STOMP (kèm header xác thực, `heart-beat`) |
| Client → server | `SEND` | Gửi message đến một destination |
| Client → server | `SUBSCRIBE` | Đăng ký nhận message từ destination |
| Client → server | `UNSUBSCRIBE` | Hủy đăng ký |
| Client → server | `ACK` / `NACK` | Xác nhận đã xử lý message (chế độ ack) |
| Client → server | `DISCONNECT` | Đóng phiên STOMP |
| Server → client | `CONNECTED` | Chấp nhận kết nối |
| Server → client | `MESSAGE` | Đẩy message tới subscriber |
| Server → client | `RECEIPT` | Xác nhận đã nhận frame có header `receipt` |
| Server → client | `ERROR` | Báo lỗi (thường kèm đóng kết nối) |

### 10.3. Kiến trúc trong Spring

```
                       ┌────────────────────────────────────────┐
                       │                Server                  │
Client --SEND /app/x-->│ clientInboundChannel                   │
                       │    ├─ /app/**   → @MessageMapping      │
                       │    │                 │                 │
                       │    │                 ▼                 │
                       │    │           xử lý nghiệp vụ         │
                       │    │                 │ convertAndSend  │
                       │    └─ /topic/** ─────┤                 │
                       │                      ▼                 │
                       │              Message Broker            │
Client <--MESSAGE------│ clientOutboundChannel ◄─(simple/relay) │
                       └────────────────────────────────────────┘
```

- **`/app/**`**: destination dành cho message gửi lên để **ứng dụng xử lý** (khớp `@MessageMapping`).
- **`/topic/**`** (broadcast) và **`/queue/**`** (điểm-điểm, thường dùng cho từng user): destination do **broker** phân phối tới subscriber.
- Đây chỉ là quy ước đặt tên, prefix thật sự do bạn cấu hình.

### 10.4. Cấu hình Spring

```java
@Configuration
@EnableWebSocketMessageBroker
public class StompConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:3000");
        // .withSockJS();  // bật nếu cần fallback, xem mục 11
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app"); // client -> @MessageMapping
        registry.enableSimpleBroker("/topic", "/queue");    // broker -> client
        registry.setUserDestinationPrefix("/user");         // mặc định là /user
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(64 * 1024)
                    .setSendBufferSizeLimit(512 * 1024)
                    .setSendTimeLimit(20_000);
    }
}
```

### 10.5. Publish: client gửi lên server

```js
stompClient.publish({
  destination: "/app/chat-send",
  body: JSON.stringify(message),
});
```

- Đóng gói thành frame `SEND` gửi cho server.
- Spring bỏ prefix `/app` rồi gọi method có `@MessageMapping("/chat-send")`:

```java
@Controller
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat-send")
    public void handle(ChatMessage message, Principal principal) {
        messagingTemplate.convertAndSend("/topic/messages", message);
    }
}
```

Cách viết ngắn hơn bằng `@SendTo`:

```java
@MessageMapping("/chat-send")
@SendTo("/topic/messages")
public ChatMessage handle(ChatMessage message) {
    return message; // giá trị trả về được broadcast tới /topic/messages
}
```

### 10.6. Subscribe: client lắng nghe

```js
const sub = stompClient.subscribe("/topic/messages", (frame) => {
  const message = JSON.parse(frame.body);
});

// hủy khi không cần nữa (ví dụ khi component unmount)
sub.unsubscribe();
```

- Lắng nghe mọi tin nhắn gửi đến `/topic/messages`.
- Khi server gọi `messagingTemplate.convertAndSend("/topic/messages", message)`, **tất cả** client đã subscribe sẽ nhận được.

### 10.7. Gửi cho một user cụ thể

```java
// Server: gửi riêng cho user "alice"
messagingTemplate.convertAndSendToUser("alice", "/queue/notifications", payload);
```

```js
// Client: subscribe với prefix /user
client.subscribe("/user/queue/notifications", (frame) => { /* ... */ });
```

- Spring dịch `/user/queue/notifications` thành destination riêng theo session của user đó.
- Tên user lấy từ `Principal` của session, nên cần xác thực trước (xem [mục 12](#12-bảo-mật-và-jwt)).
- Các annotation liên quan: `@SendToUser`, `@SubscribeMapping` (trả dữ liệu ngay khi client subscribe, không qua broker), `@MessageExceptionHandler`.

### 10.8. Heartbeat của STOMP

```java
@Override
public void configureMessageBroker(MessageBrokerRegistry registry) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("ws-heartbeat-");
    scheduler.initialize();

    registry.enableSimpleBroker("/topic", "/queue")
            .setHeartbeatValue(new long[]{10_000, 10_000}) // {server gửi, server mong nhận} (ms)
            .setTaskScheduler(scheduler);
}
```

```js
const client = new Client({
  brokerURL: "ws://localhost:8080/ws",
  heartbeatIncoming: 10000,
  heartbeatOutgoing: 10000,
  reconnectDelay: 5000, // tự reconnect sau 5 giây khi mất kết nối
});
```

### 10.9. Client hoàn chỉnh với `@stomp/stompjs`

```js
import { Client } from "@stomp/stompjs";

const client = new Client({
  brokerURL: "ws://localhost:8080/ws",
  connectHeaders: { Authorization: "Bearer " + token },
  reconnectDelay: 5000,
  heartbeatIncoming: 10000,
  heartbeatOutgoing: 10000,

  onConnect: () => {
    // subscribe lại mỗi lần (re)connect
    client.subscribe("/topic/messages", (frame) => console.log(JSON.parse(frame.body)));
  },
  onStompError: (frame) => console.error("STOMP error:", frame.headers["message"]),
  onWebSocketClose: (evt) => console.log("WS closed", evt.code),
});

client.activate();      // mở kết nối
// client.deactivate(); // đóng chủ động
```

### 10.10. Broker ngoài (broker relay)

`enableSimpleBroker` là broker **trong bộ nhớ của một instance**, chỉ đủ cho một server. Khi chạy nhiều instance hoặc cần độ bền/hiệu năng cao, chuyển sang broker ngoài hỗ trợ STOMP (RabbitMQ, ActiveMQ):

```java
registry.enableStompBrokerRelay("/topic", "/queue")
        .setRelayHost("localhost")
        .setRelayPort(61613)   // cổng STOMP của RabbitMQ (cần bật plugin rabbitmq_stomp)
        .setClientLogin("guest")
        .setClientPasscode("guest");
```

---

## 11. SockJS

SockJS là thư viện cung cấp **fallback** khi trình duyệt hoặc proxy không hỗ trợ WebSocket (dùng xhr-streaming, xhr-polling...). Với trình duyệt hiện đại, WebSocket gần như luôn dùng được nên SockJS ít cần.

```java
registry.addEndpoint("/ws").setAllowedOrigins("http://localhost:3000").withSockJS();
```

```js
import SockJS from "sockjs-client";

const client = new Client({
  webSocketFactory: () => new SockJS("http://localhost:8080/ws"), // dùng http://, không phải ws://
  // không dùng brokerURL khi dùng SockJS
});
```

- Khi bật SockJS, endpoint có thêm các đường dẫn phụ như `/ws/info`. Nếu có proxy/security phía trước cần cho phép chúng.
- Chỉ bật khi thực sự cần hỗ trợ môi trường cũ hoặc mạng doanh nghiệp chặn WebSocket.

---

## 12. Bảo mật và JWT

### 12.1. Checklist bảo mật

| Vấn đề | Cách xử lý |
|---|---|
| Nghe lén dữ liệu | Dùng **`wss://`** (TLS) ở production, không dùng `ws://` |
| Cross-Site WebSocket Hijacking | Kiểm tra `Origin` (`setAllowedOrigins` với danh sách cụ thể). Cẩn thận hơn nếu xác thực bằng cookie, vì trình duyệt tự gửi cookie |
| Chưa xác thực | Xác thực ở handshake hoặc STOMP `CONNECT` |
| Chưa phân quyền | Kiểm tra quyền ở từng `SUBSCRIBE` và `SEND`, không chỉ ở `CONNECT` |
| Message quá lớn / spam | Giới hạn kích thước message, rate limit theo user/session |
| Dữ liệu đầu vào độc hại | Validate/sanitize payload như mọi API khác, tuyệt đối không tin dữ liệu client |
| Kết nối treo/chậm (DoS) | Idle timeout, giới hạn số kết nối trên mỗi user/IP, giới hạn bộ đệm gửi |
| Lộ token | Không đưa token vào URL (dễ lộ trong log server/proxy/lịch sử trình duyệt) nếu tránh được |

### 12.2. Các cách truyền JWT

| Cách | Ưu | Nhược |
|---|---|---|
| Trong header của STOMP `CONNECT` | An toàn hơn query string, chuẩn cho STOMP | Xác thực sau khi WebSocket đã mở |
| Query string `?token=...` | Từ chối được ngay ở handshake | Token lộ trong log/proxy |
| Cookie (session/JWT httpOnly) | Trình duyệt tự gửi | Phải chống CSWSH/CSRF (kiểm tra Origin) |
| Ticket dùng một lần: gọi REST lấy ticket ngắn hạn, rồi gắn ticket vào URL | Không lộ JWT thật, hết hạn nhanh | Thêm một bước |
| Header `Sec-WebSocket-Protocol` | Là cách "lách" để mang token trong handshake | Hơi hack, cần server trả lại đúng giá trị |

Trình duyệt **không cho gắn header `Authorization`** vào handshake, nên với STOMP cách phổ biến nhất là gửi JWT trong `CONNECT`.

### 12.3. JWT với STOMP

JWT được kiểm tra **một lần** ở frame `CONNECT`, vì STOMP chạy trên một kết nối WebSocket duy nhất, không xác thực theo từng HTTP request như REST.

Client gửi JWT trong header của frame `CONNECT`:

```js
const client = new Client({
  brokerURL: "ws://localhost:8080/ws",
  connectHeaders: {
    Authorization: "Bearer " + token, // gửi kèm khi CONNECT
  },
});
client.activate();
```

Server dùng `ChannelInterceptor` để bắt frame `CONNECT`:

```java
@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99) // chạy trước interceptor của Spring Security
public class StompConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService; // tự viết: parse + validate JWT

    public StompConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String header = accessor.getFirstNativeHeader("Authorization");
                    if (header == null || !header.startsWith("Bearer ")) {
                        throw new MessagingException("Thiếu token");
                    }
                    // validate token; sai/hết hạn thì ném exception để từ chối kết nối
                    Authentication auth = jwtService.authenticate(header.substring(7));
                    accessor.setUser(auth); // Principal cho toàn bộ session
                }
                return message;
            }
        });
    }
}
```

Sau khi `setUser(...)`, `Principal` có sẵn trong `@MessageMapping` và dùng được với `convertAndSendToUser`.

### 12.4. Phân quyền theo destination

Ngoài `CONNECT`, nên chặn cả `SUBSCRIBE` và `SEND` để user không đăng ký vào topic không thuộc quyền:

```java
if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
    String dest = accessor.getDestination();
    Principal user = accessor.getUser();
    if (dest != null && dest.startsWith("/topic/admin") && !isAdmin(user)) {
        throw new MessagingException("Không có quyền");
    }
}
```

Với Spring Security 6.x, có thể khai báo phân quyền message theo destination (`@EnableWebSocketSecurity` cùng `MessageMatcherDelegatingAuthorizationManager`, các matcher như `simpDestMatchers`, `simpSubscribeDestMatchers`). Cách chi tiết phụ thuộc phiên bản, hãy xem tài liệu Spring Security tương ứng. Nếu dùng JWT không lưu session (stateless), thường phải tắt CSRF cho luồng WebSocket này.

### 12.5. Lưu ý về token hết hạn

- Token chỉ được kiểm tra lúc `CONNECT`. Nếu token hết hạn khi kết nối vẫn mở thì kết nối vẫn sống.
- Cách xử lý: đặt thời gian sống tối đa cho kết nối; server đóng bằng mã riêng (ví dụ `4401`) khi token hết hạn để client làm mới token rồi reconnect; hoặc kiểm tra lại token ở mỗi `SEND`/`SUBSCRIBE`.

---

## 13. Scale và triển khai thực tế

### 13.1. Reverse proxy (Nginx)

Proxy phải chuyển tiếp đúng header `Upgrade`/`Connection`, nếu không handshake sẽ thất bại (thường lỗi `400`):

```nginx
location /ws {
    proxy_pass http://backend;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_read_timeout 3600s;   # mặc định 60s sẽ cắt kết nối idle
    proxy_send_timeout 3600s;
}
```

Load balancer (ALB, ELB...) cũng có **idle timeout** (thường 60 giây), cần heartbeat nhỏ hơn giá trị đó hoặc tăng timeout.

### 13.2. Nhiều instance backend

Kết nối WebSocket gắn với **một instance cụ thể** (session nằm trong bộ nhớ instance đó). Hệ quả:

- Client A ở instance 1, client B ở instance 2: broadcast từ instance 1 **không tới** B nếu dùng simple broker trong bộ nhớ.
- Giải pháp: dùng **message broker chung** làm trục phân phối:
  - STOMP: `enableStompBrokerRelay` tới RabbitMQ/ActiveMQ.
  - WebSocket thuần: dùng Redis Pub/Sub (hoặc Kafka) để mỗi instance nhận sự kiện rồi tự đẩy xuống các session của nó.

```
Instance 1 ─┐                   ┌─► Instance 1 ─► các client của nó
Instance 2 ─┼─► Redis/RabbitMQ ─┼─► Instance 2 ─► các client của nó
Instance 3 ─┘                   └─► Instance 3 ─► các client của nó
```

- **Sticky session** cần thiết khi dùng SockJS (nhiều request HTTP thuộc cùng một phiên). Với WebSocket thuần thì một kết nối TCP đã cố định vào một instance, nhưng sticky vẫn hữu ích khi reconnect.
- Trạng thái như "ai đang online" (presence) cần lưu ở nơi dùng chung (Redis) thay vì trong `Map` cục bộ.

### 13.3. Tài nguyên

- Mỗi kết nối chiếm một socket (file descriptor) và bộ nhớ cho buffer/session. Cần nâng `ulimit -n` và giới hạn kết nối phù hợp khi có hàng chục nghìn kết nối.
- Mô hình thread-per-connection tốn kém; server dựa trên NIO (Tomcat NIO, Netty, Undertow) xử lý nhiều kết nối idle tốt hơn.
- Giới hạn số kết nối trên mỗi user/IP để tránh bị lạm dụng.

### 13.4. Triển khai an toàn

- Deploy/restart server sẽ đóng toàn bộ kết nối (`1001`/`1012`): client phải có reconnect với backoff + jitter (xem [mục 7.4](#74-tự-kết-nối-lại)), tránh "thundering herd".
- Ghi log số kết nối hiện tại, số message/giây, độ dài hàng đợi gửi để phát hiện client chậm.

---

## 14. Demo: bấm mua hàng, trừ tồn kho realtime

Mục tiêu: user bấm "Mua" thì số lượng tồn kho giảm và **mọi client đang mở trang** thấy số mới ngay, không cần refresh.

### 14.1. Luồng xử lý

```
Client A bấm Mua
   │  (REST POST /api/products/1/buy   hoặc   WebSocket message)
   ▼
Backend: trừ tồn kho trong DB (nguyên tử, trong transaction)
   │
   ├─ thất bại (hết hàng) → trả lỗi cho riêng client A
   │
   └─ thành công + COMMIT → đẩy {productId, stock} tới tất cả client
                               │
                               ▼
                Mọi client nhận onmessage → cập nhật UI
```

Khuyến nghị: **thao tác ghi (mua hàng) đi qua REST**, còn WebSocket chỉ dùng để **đẩy thông báo cập nhật**. Cách này tận dụng sẵn validation, transaction, xác thực và mã lỗi HTTP của REST.

### 14.2. Trừ tồn kho an toàn

Viết kiểu "đọc số lượng, trừ đi, lưu lại" sẽ bị **race condition** khi hai người mua cùng lúc (bán lố hàng). Hãy trừ nguyên tử ngay trong câu lệnh SQL:

```java
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :qty WHERE p.id = :id AND p.stock >= :qty")
    int decreaseStock(@Param("id") Long id, @Param("qty") int qty); // trả về số dòng đã cập nhật
}
```

```java
@Service
public class OrderService {

    private final ProductRepository repo;
    private final ApplicationEventPublisher events;

    public OrderService(ProductRepository repo, ApplicationEventPublisher events) {
        this.repo = repo;
        this.events = events;
    }

    @Transactional
    public int buy(Long productId, int qty) {
        int updated = repo.decreaseStock(productId, qty);
        if (updated == 0) {
            throw new IllegalStateException("Hết hàng hoặc không đủ số lượng");
        }
        int stock = repo.findById(productId).orElseThrow().getStock();
        events.publishEvent(new StockChangedEvent(productId, stock));
        return stock;
    }
}

public record StockChangedEvent(Long productId, int stock) {}
```

Cách khác: dùng khóa lạc quan (`@Version`) hoặc khóa bi quan (`@Lock(PESSIMISTIC_WRITE)`), nhưng câu `UPDATE ... WHERE stock >= :qty` đơn giản và hiệu quả nhất cho bài toán này.

### 14.3. Chỉ broadcast sau khi COMMIT

Nếu đẩy message trong transaction rồi transaction rollback, client sẽ thấy số liệu sai. Dùng `@TransactionalEventListener` để chỉ đẩy sau khi commit thành công:

**Với STOMP:**

```java
@Component
public class StockBroadcaster {

    private final SimpMessagingTemplate template;

    public StockBroadcaster(SimpMessagingTemplate template) {
        this.template = template;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockChanged(StockChangedEvent e) {
        template.convertAndSend("/topic/stock", e); // {productId, stock}
    }
}
```

**Với WebSocket thuần** (dùng `RawHandler.broadcast` ở [mục 9.3](#93-handler-bốn-hook-ứng-với-vòng-đời)):

```java
@Component
public class RawStockBroadcaster {

    private final RawHandler handler;
    private final ObjectMapper mapper;

    public RawStockBroadcaster(RawHandler handler, ObjectMapper mapper) {
        this.handler = handler;
        this.mapper = mapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockChanged(StockChangedEvent e) throws JsonProcessingException {
        handler.broadcast(mapper.writeValueAsString(
            Map.of("type", "STOCK_UPDATED", "productId", e.productId(), "stock", e.stock())));
    }
}
```

### 14.4. REST controller

```java
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final OrderService orderService;

    public ProductController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/{id}/buy")
    public ResponseEntity<?> buy(@PathVariable Long id, @RequestParam(defaultValue = "1") int qty) {
        try {
            return ResponseEntity.ok(Map.of("stock", orderService.buy(id, qty)));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
        }
    }
}
```

### 14.5. Tránh mất cập nhật khi tải trang

Nếu client tải danh sách sản phẩm bằng REST **rồi mới** mở WebSocket, các thay đổi xảy ra giữa hai bước sẽ bị lỡ. Cách làm đúng:

1. Mở WebSocket và subscribe trước.
2. Gọi REST lấy trạng thái hiện tại.
3. Áp dụng các cập nhật realtime lên trạng thái đó (tốt nhất kèm `version`/`updatedAt` để bỏ qua cập nhật cũ hơn dữ liệu đã có).
4. Sau mỗi lần **reconnect**, lặp lại bước 2 để đồng bộ lại.

### 14.6. Frontend React (WebSocket thuần)

```jsx
import { useEffect, useRef, useState } from "react";

export default function App() {
  const [products, setProducts] = useState([]);
  const wsRef = useRef(null);

  useEffect(() => {
    let retry = 0;
    let closedByUs = false;

    const load = () =>
      fetch("/api/products").then((r) => r.json()).then(setProducts);

    function connect() {
      const ws = new WebSocket("ws://localhost:8080/ws");
      wsRef.current = ws;

      ws.onopen = () => { retry = 0; load(); }; // đồng bộ lại mỗi lần (re)connect

      ws.onmessage = (e) => {
        const msg = JSON.parse(e.data);
        if (msg.type === "STOCK_UPDATED") {
          setProducts((prev) =>
            prev.map((p) => (p.id === msg.productId ? { ...p, stock: msg.stock } : p)));
        }
      };

      ws.onclose = () => {
        if (closedByUs) return;
        setTimeout(connect, Math.min(1000 * 2 ** retry++, 30000) + Math.random() * 1000);
      };
    }

    connect();
    return () => { closedByUs = true; wsRef.current?.close(1000); }; // dọn khi unmount
  }, []);

  const buy = async (id) => {
    const res = await fetch(`/api/products/${id}/buy`, { method: "POST" });
    if (!res.ok) alert((await res.json()).error);
    // không cần setProducts ở đây: số mới sẽ về qua WebSocket
  };

  return (
    <ul>
      {products.map((p) => (
        <li key={p.id}>
          {p.name}: còn {p.stock}
          <button onClick={() => buy(p.id)} disabled={p.stock <= 0}>Mua</button>
        </li>
      ))}
    </ul>
  );
}
```

Chú ý `useEffect` phải **đóng kết nối khi unmount** (`return` cleanup), nếu không mỗi lần render lại (nhất là trong React StrictMode ở môi trường dev) sẽ tạo thêm kết nối rò rỉ.

### 14.7. Những thứ nên thử tiếp để hiểu sâu hơn

- Mở hai tab, bấm mua ở tab này và quan sát tab kia cập nhật.
- Tắt server rồi bật lại, xem client tự reconnect và đồng bộ lại.
- Chạy hai instance sau load balancer, quan sát việc broadcast không tới client ở instance khác, rồi thêm Redis Pub/Sub để khắc phục.
- Thử 100 request mua đồng thời với `stock = 10` để kiểm tra không bán lố.
- Chuyển từ WebSocket thuần sang STOMP (`/topic/stock`) và so sánh lượng code phải tự quản lý.
- Thêm JWT và phân quyền theo topic.

---

## 15. Test và debug

### 15.1. Trên trình duyệt

Chrome DevTools → tab **Network** → lọc **WS** → chọn kết nối:

- **Headers**: xem request/response handshake, mã `101`, header `Sec-WebSocket-*`.
- **Messages**: xem từng frame gửi (mũi tên lên) và nhận (mũi tên xuống), kèm độ dài và thời gian. Frame ping/pong không hiện ở đây.

### 15.2. Công cụ dòng lệnh và GUI

```bash
# wscat (Node.js)
npm install -g wscat
wscat -c ws://localhost:8080/ws
> {"type":"ping"}

# websocat
websocat ws://localhost:8080/ws
```

Postman và Insomnia cũng hỗ trợ kết nối WebSocket. STOMP thì cần client biết nói STOMP (không gõ tay được dễ dàng).

### 15.3. Test tích hợp trong Spring

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketIT {

    @LocalServerPort int port;

    @Test
    void receivesBroadcast() throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());

        StompSession session = client
            .connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {})
            .get(3, TimeUnit.SECONDS);

        BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
        session.subscribe("/topic/stock", new StompFrameHandler() {
            public Type getPayloadType(StompHeaders h) { return Map.class; }
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders h, Object payload) { queue.add((Map<String, Object>) payload); }
        });

        // ... gây ra sự kiện (gọi REST buy) rồi:
        // assertNotNull(queue.poll(3, TimeUnit.SECONDS));
    }
}
```

### 15.4. Bật log

```properties
logging.level.org.springframework.web.socket=DEBUG
logging.level.org.springframework.messaging=DEBUG
```

---

## 16. Lỗi thường gặp

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| Handshake `403` | `Origin` không nằm trong `setAllowedOrigins`, hoặc Spring Security chặn | Thêm đúng origin (kèm cổng); cho phép đường dẫn `/ws` trong cấu hình security |
| Handshake `404` | Sai đường dẫn endpoint, hoặc bật SockJS mà client dùng `ws://` thay vì `http://` | Kiểm tra `addEndpoint`/`addHandler` và URL phía client |
| Handshake `400` khi qua Nginx/proxy | Thiếu `proxy_set_header Upgrade/Connection` và `proxy_http_version 1.1` | Cấu hình như [mục 13.1](#131-reverse-proxy-nginx) |
| Client thấy `1006` liên tục | Handshake lỗi, server crash, proxy cắt idle | Xem tab Network và log server; thêm heartbeat |
| Kết nối tự đứt sau khoảng 60 giây im lặng | Idle timeout của proxy/LB | Heartbeat nhỏ hơn timeout, hoặc tăng timeout |
| STOMP: gửi `SEND` nhưng `@MessageMapping` không chạy | Quên prefix `/app`, hoặc sai `setApplicationDestinationPrefixes` | `destination` phải là `/app/` + đường dẫn trong `@MessageMapping` |
| STOMP: subscribe nhưng không nhận gì | Destination sai/không nằm trong `enableSimpleBroker` prefix; subscribe sau khi server đã gửi | Kiểm tra prefix (`/topic`, `/queue`) và thứ tự subscribe |
| Nhận message bị lặp | Component render lại tạo nhiều kết nối/nhiều subscription mà không dọn | Dọn (`close`/`unsubscribe`) trong cleanup của `useEffect` |
| `IllegalStateException: TEXT_PARTIAL_WRITING` | Nhiều thread cùng `sendMessage` trên một session | Bọc bằng `ConcurrentWebSocketSessionDecorator` |
| Memory tăng dần | Không xóa session ở `afterConnectionClosed`, subscription không hủy | Dọn session/listener khi đóng |
| Message lớn làm đóng kết nối (`1009`) | Vượt giới hạn buffer | Tăng `setMessageSizeLimit`/buffer, hoặc chia nhỏ message |
| Không nhận được `Principal` trong `@MessageMapping` | Chưa `accessor.setUser(...)` ở `CONNECT` | Gắn user trong interceptor như [mục 12.3](#123-jwt-với-stomp) |
| Có nhiều instance nhưng chỉ một số client nhận | Simple broker chỉ nằm trong bộ nhớ một instance | Dùng broker relay/Redis Pub/Sub ([mục 13.2](#132-nhiều-instance-backend)) |

---

## 17. Khi nào dùng, khi nào không

**Nên dùng WebSocket khi:**

- Cần dữ liệu hai chiều, độ trễ thấp và tần suất cao: chat, game, cộng tác chỉnh sửa, giao dịch, điều khiển thiết bị.
- Server cần đẩy dữ liệu bất kỳ lúc nào, và client cũng gửi thường xuyên.

**Cân nhắc phương án khác khi:**

| Nhu cầu | Phương án phù hợp hơn |
|---|---|
| Chỉ server → client (thông báo, tiến độ, feed) | **SSE** (đơn giản, tự reconnect, đi qua HTTP thường) |
| Cập nhật thưa thớt | Polling / long polling |
| Request-response thông thường | REST |
| Streaming giữa các service backend | gRPC streaming |
| Cần độ trễ cực thấp, nhiều luồng độc lập, UDP-like | WebTransport (mới hơn, chưa phổ biến bằng) |
| Push khi ứng dụng/tab đã đóng | Web Push / FCM (WebSocket chỉ sống khi trang còn mở) |

Ghi chú: WebSocket qua HTTP/2 (RFC 8441) và HTTP/3 (RFC 9220) tồn tại nhưng hỗ trợ phụ thuộc server/proxy; phần lớn triển khai vẫn dùng WebSocket trên HTTP/1.1.

---

## 18. Cheat sheet

**Handshake**: `GET` + `Upgrade: websocket` + `Sec-WebSocket-Key` → `101` + `Sec-WebSocket-Accept = Base64(SHA1(Key + GUID))`.

**Frame**: `FIN`, `opcode` (`1` text, `2` binary, `8` close, `9` ping, `A` pong), client→server luôn mask.

**readyState**: `0` CONNECTING, `1` OPEN, `2` CLOSING, `3` CLOSED.

**Close code cần nhớ**: `1000` bình thường, `1001` rời đi, `1006` bất thường (chỉ phía trình duyệt), `1008` policy, `1009` quá lớn, `1011` lỗi server, `4000-4999` tự định nghĩa.

**Spring thuần (bốn hook)**: `afterConnectionEstablished` → `handleTextMessage` → `handleTransportError` → `afterConnectionClosed`.

**STOMP**:

| Việc | Client | Server |
|---|---|---|
| Gửi lên | `publish({destination:"/app/x"})` | `@MessageMapping("/x")` |
| Broadcast | `subscribe("/topic/y")` | `convertAndSend("/topic/y", data)` |
| Gửi riêng một user | `subscribe("/user/queue/z")` | `convertAndSendToUser(user, "/queue/z", data)` |
| Cấu hình | | `setApplicationDestinationPrefixes("/app")`, `enableSimpleBroker("/topic","/queue")` |

**Bảo mật**: `wss://`, kiểm tra `Origin`, JWT ở STOMP `CONNECT`, phân quyền ở `SUBSCRIBE`/`SEND`, giới hạn kích thước message, rate limit.

**Vận hành**: heartbeat < idle timeout, reconnect có backoff + jitter, dọn session, `ConcurrentWebSocketSessionDecorator`, broker chung khi nhiều instance, Nginx cần `Upgrade`/`Connection` và timeout dài.

---

## 19. Tài liệu tham khảo

- RFC 6455, The WebSocket Protocol: https://datatracker.ietf.org/doc/html/rfc6455
- RFC 7692, Compression Extensions for WebSocket (`permessage-deflate`): https://datatracker.ietf.org/doc/html/rfc7692
- MDN, WebSockets API: https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API
- STOMP 1.2 specification: https://stomp.github.io/stomp-specification-1.2.html
- Spring Framework, WebSocket (bao gồm STOMP): https://docs.spring.io/spring-framework/reference/web/websocket.html
- `@stomp/stompjs`: https://stomp-js.github.io/
