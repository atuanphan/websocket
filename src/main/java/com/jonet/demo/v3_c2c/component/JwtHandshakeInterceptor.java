package com.jonet.demo.v3_c2c.component;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor{
	private final JwtProvider jwtProvider;

	//hàm này trước khi kết nối đến web socket
	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Map<String, Object> attributes) throws Exception {
		String token = extractToken(request);
		if (token == null || !jwtProvider.validateToken(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        String username = jwtProvider.getUsernameFromToken(token);
        attributes.put("username", username);
        return true;
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			@Nullable Exception exception) {
		// TODO Auto-generated method stub
		
	}

	private String extractToken(ServerHttpRequest request) {
        String query = request.getURI().getQuery(); // ví dụ: token=xxx
        if (query != null && query.contains("token=")) {
            return query.split("token=")[1].split("&")[0];
        }
        return null;
    }
}
