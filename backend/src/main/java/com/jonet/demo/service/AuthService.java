package com.jonet.demo.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.jonet.demo.auth.AuthRequest;
import com.jonet.demo.auth.AuthResponse;
import com.jonet.demo.enums.RoleEnum;
import com.jonet.demo.models.User;
import com.jonet.demo.repository.UserReopository;
import com.jonet.demo.security.JwtService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserReopository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(AuthRequest request) {
        validateRequest(request);
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username is already in use");
        }

        User user = userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode(request.password()))
                .role(RoleEnum.USER)
                .build());
        return createResponse(user);
    }

    public AuthResponse login(AuthRequest request) {
        if (request == null || !StringUtils.hasText(request.username()) || !StringUtils.hasText(request.password())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        User user = userRepository.findByUsername(request.username().trim());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        return createResponse(user);
    }

    private void validateRequest(AuthRequest request) {
        if (request == null || !StringUtils.hasText(request.username()) || !StringUtils.hasText(request.password())) {
            throw new IllegalArgumentException("Username and password are required");
        }
    }

    private AuthResponse createResponse(User user) {
        RoleEnum role = user.getRole() == null ? RoleEnum.USER : user.getRole();
        String token = jwtService.generateToken(user.getUsername(), role);
        return new AuthResponse(token, "Bearer", jwtService.getExpirationMs() / 1000);
    }
}