package com.jonet.demo.v3_c2c.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginRequest {
	 private String username;
	 private String password;
}
