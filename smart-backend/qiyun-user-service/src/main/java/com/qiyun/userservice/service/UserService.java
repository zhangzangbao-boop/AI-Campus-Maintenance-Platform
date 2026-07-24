package com.qiyun.userservice.service;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.userservice.domain.entity.User;
import com.qiyun.userservice.domain.enums.UserRole;
import com.qiyun.userservice.dto.ResetPasswordResult;
import com.qiyun.userservice.dto.UserDto;
import com.qiyun.userservice.dto.request.ChangePasswordRequest;
import com.qiyun.userservice.dto.request.LoginRequest;
import com.qiyun.userservice.dto.request.UserRegisterRequest;
import com.qiyun.userservice.dto.request.UserUpdateRequest;
import com.qiyun.userservice.dto.response.AuthResponse;
import com.qiyun.userservice.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
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

    @Transactional(readOnly = true)
    public Optional<UserDto> findByIdOptional(String userId) {
        return userRepository.findById(userId)
            .map(this::toDto);
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
    public void activate(String userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "用户不存在"));
        user.setIsActive(true);
    }

    @Transactional
    public UserDto updateUser(String userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "用户不存在"));

        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            user.setNickname(request.getNickname());
        }

        if (request.getContactPhone() != null && !request.getContactPhone().isBlank()) {
            user.setContactPhone(request.getContactPhone());
        }

        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        if (user.getRole() == UserRole.STAFF) {
            user.setResponsibleArea(cleanStaffText(request.getResponsibleArea()));
            user.setSpecialties(cleanStaffText(request.getSpecialties()));
        } else {
            user.setResponsibleArea(null);
            user.setSpecialties(null);
        }

        userRepository.save(user);
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

        // avatarUrl允许为空，不再强制必填
        if (request.getAvatarUrl() != null) {
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

    private UserDto toDto(User user) {
        boolean staff = user.getRole() == UserRole.STAFF;
        return new UserDto(
            user.getUserId(),
            user.getNickname(),
            user.getContactPhone(),
            user.getAvatarUrl(),
            user.getRole(),
            Boolean.TRUE.equals(user.getIsActive()),
            staff ? user.getResponsibleArea() : null,
            staff ? user.getSpecialties() : null
        );
    }

    private String cleanStaffText(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.trim();
        return cleaned.isEmpty() ? null : cleaned;
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
