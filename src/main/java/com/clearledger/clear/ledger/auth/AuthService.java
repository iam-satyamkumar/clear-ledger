package com.clearledger.clear.ledger.auth;

import com.clearledger.clear.ledger.user.User;
import com.clearledger.clear.ledger.user.UserRepository;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.clearledger.clear.ledger.security.JwtService;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    
public AuthService(UserRepository userRepository,
                   PasswordEncoder passwordEncoder,
                   JwtService jwtService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
}

    @Transactional
    public User register(String email, String password) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("email already registered");
        }

        String passwordHash = passwordEncoder.encode(password);
        User user = new User(email, passwordHash, Instant.now());
        return userRepository.save(user);
    }

@Transactional(readOnly = true)
public AuthResponse login(String email, String password) {
    User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("invalid credentials"));

    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
        throw new IllegalArgumentException("invalid credentials");
    }

    String token = jwtService.generateToken(user.getEmail());
    return new AuthResponse(user.getId(), user.getEmail(), token);
}
}