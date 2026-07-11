package com.ligong.reportingcenter.service;

import com.ligong.reportingcenter.domain.entity.User;
import com.ligong.reportingcenter.domain.enums.UserRole;
import com.ligong.reportingcenter.dto.ResetPasswordResult;
import com.ligong.reportingcenter.dto.UserDto;
import com.ligong.reportingcenter.dto.request.ChangePasswordRequest;
import com.ligong.reportingcenter.dto.request.LoginRequest;
import com.ligong.reportingcenter.dto.request.UserRegisterRequest;
import com.ligong.reportingcenter.dto.request.UserUpdateRequest;
import com.ligong.reportingcenter.dto.response.AuthResponse;
import com.qiyun.common.exception.BusinessException;
import com.ligong.reportingcenter.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
        // 支持使用 userId 或 nickname 登录
        // 先尝试通过 userId 查找
        Optional<User> userOpt = userRepository.findActiveByUserId(request.userId());
        
        // 如果通过 userId 找不到，尝试通过 nickname 查找
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

    @Transactional(readOnly = true)
    public List<UserDto> listByRole(UserRole role) {
        return userRepository.findByRoleAndIsActiveTrue(role)
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserDto> listAll() {
        return userRepository.findAll()
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @Transactional
    public void deactivate(String userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "用户不存在"));
        user.setIsActive(false);
    }

    @Transactional
    public UserDto updateUser(String userId, UserRegisterRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "用户不存在"));
        
        // 更新用户信息
        if (request.nickname() != null && !request.nickname().isBlank()) {
            user.setNickname(request.nickname());
        }
        
        if (request.contactPhone() != null && !request.contactPhone().isBlank()) {
            user.setContactPhone(request.contactPhone());
        }
        
        userRepository.save(user);
        return toDto(user);
    }

    @Transactional
    public void activate(String userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "用户不存在"));
        user.setIsActive(true);
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

    public User loadActiveUser(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (!user.getIsActive()) {
            throw new BusinessException("用户账户已被禁用");
        }
        return user;
    }
    
    // 添加缺失的方法
    @Transactional(readOnly = true)
    public UserDto getCurrentUser(String userId) {
        return findById(userId);
    }
    
    @Transactional
    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "原密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "新密码不能与原密码相同");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional
    public UserDto updateUserInfo(String userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "用户不存在"));
            
        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            user.setNickname(request.getNickname());
        }
        
        if (request.getContactPhone() != null && !request.getContactPhone().isBlank()) {
            user.setContactPhone(request.getContactPhone());
        }
        
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isBlank()) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        
        userRepository.save(user);
        return toDto(user);
    }
    
    @Transactional
    public ResetPasswordResult resetPassword(String userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "用户不存在"));
            
        // 生成随机密码
        String newPassword = RandomStringUtils.randomAlphanumeric(8);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        return new ResetPasswordResult(userId, newPassword);
    }
}
