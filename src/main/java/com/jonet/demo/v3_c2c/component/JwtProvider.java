package com.jonet.demo.v3_c2c.component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.jonet.demo.v3_c2c.entity.RoleEntity;
import com.jonet.demo.v3_c2c.entity.UserEntity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class JwtProvider {
	private String secretKey = "2bsH5LSit3KZYoi7vdXCtsEHQPWoZOr4USwx6k/1xS8=";
	private Long expiration = 3000L;
	
	private Key getSingingKeys() {
		byte[] bytes = Decoders.BASE64.decode(secretKey);
		return Keys.hmacShaKeyFor(bytes);
	}
	
	public String generateToken(String username, Map<String, Object> extraClaims) {
		Date now = new Date();
		Date expiryDate = new Date(now.getTime() + expiration * 1000);
		return Jwts.builder()
				.setClaims(extraClaims)
				.setSubject(username)
				.signWith(getSingingKeys(), SignatureAlgorithm.HS256)
				.setIssuedAt(now)
				.setExpiration(expiryDate)
				.compact();
	}
	
	public Map<String, Object> extraClaims(UserEntity user) {
		Map<String, Object> extraClaims = new HashMap<>();
		extraClaims.put("userId", user.getId());
		extraClaims.put("roles", user.getRoles()
		        .stream()
		        .map(RoleEntity::getCode)
		        .collect(Collectors.toList()));
		extraClaims.put("tokenType", "ACCESS"); 
		return extraClaims;
	}
	
	public boolean validateToken(String token) {
		try {
			Jwts.parserBuilder().setSigningKey(getSingingKeys()).build().parse(token);
			return true;
		} catch (MalformedJwtException e) { 
			log.error("Invalid JWT token: {}", e.getMessage());
		} catch (ExpiredJwtException e) { // khi token hết hạn
			log.error("JWT token is expired: {}", e.getMessage());
		} catch (UnsupportedJwtException e) {// xảy ra khi token đúng format nhưng không dùng được: parse sai cách -
												// thuật toán k hỗ trợ
												// Không phải chuẩn Base64 URL
												// Token từ hệ thống khác (Google, Auth0, Firebase…) nhưng parse như
												// token nội bộ
												// header k hợp lệ
			log.error("JWT token is unsupported: {}", e.getMessage());
		} catch (IllegalArgumentException e) {
			log.error("JWT claims string is empty: {}", e.getMessage());
		}
		return false;
    }

    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }

    private Claims getClaims(String token) {
    	 return Jwts.parserBuilder()
    	            .setSigningKey(getSingingKeys())
    	            .build()
    	            .parseClaimsJws(token)
    	            .getBody();
    }

}
