package com.qiyun.userservice.service;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.userservice.domain.entity.User;
import com.qiyun.userservice.domain.enums.UserRole;
import com.qiyun.userservice.dto.UserDto;
import com.qiyun.userservice.dto.request.LoginRequest;
import com.qiyun.userservice.dto.request.UserRegisterRequest;
import com.qiyun.userservice.dto.response.AuthResponse;
import com.qiyun.userservice.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserDto register(UserRegisterRequest request) {
        if (userRepository.existsByUserId(request.userId())) {
            throw new BusinessException("用户ID已存在");
        }
        User user = new User();
        user.setUserId(request.userId());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setContactPhone(request.contactPhone());
        user.setRole(request.role());
        userRepository.save(user);
        return toDto(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Optional<User> userOpt = userRepository.findActiveByUserId(request.userId());

        if (userOpt.isEmpty()) {
            userOpt = userRepository.findActiveByNickname(request.userId());
        }

        User user = userOpt
            .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "账号不存在或已禁用"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "密码错误");
        }
        return new AuthResponse(toDto(user));
    }

    @Transactional(readOnly = true)
    public UserDto findById(String userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "用户不存在"));
        return toDto(user);
    }

    public User loadActiveUser(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (!user.getIsActive()) {
            throw new BusinessException("用户账户已被禁用");
        }
        return user;
    }

    private UserDto toDto(User user) {
        return new UserDto(
            user.getUserId(),
            user.getNickname(),
            user.getContactPhone(),
            user.getRole(),
            Boolean.TRUE.equals(user.getIsActive())
        );
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUserId(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在：" + username));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUserId())
                .password(user.getPasswordHash())
                .authorities("ROLE_" + user.getRole().name())
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(!user.getIsActive())
                .build();
    }
}