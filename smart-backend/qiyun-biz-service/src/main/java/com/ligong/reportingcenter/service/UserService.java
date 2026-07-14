package com.ligong.reportingcenter.service;

import com.ligong.reportingcenter.domain.entity.User;
import com.ligong.reportingcenter.domain.enums.UserRole;
import com.ligong.reportingcenter.dto.UserDto;
import com.ligong.reportingcenter.dto.request.LoginRequest;
import com.ligong.reportingcenter.dto.request.UserRegisterRequest;
import com.ligong.reportingcenter.dto.response.AuthResponse;
import com.qiyun.common.exception.BusinessException;
import com.ligong.reportingcenter.repository.UserRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * biz-service 用户服务 - 仅保留只读查询和内部业务依赖方法
 * 用户写操作已迁移到qiyun-user-service
 *
 * 注意：register和login方法仅供内部测试使用，对外用户接口已由user-service承载
 */
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 用户注册 - 仅供内部测试使用
     * 对外注册接口已迁移到qiyun-user-service
     */
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

    /**
     * 用户登录 - 仅供内部测试使用
     * 对外登录接口已迁移到qiyun-user-service
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        var userOpt = userRepository.findActiveByUserId(request.userId());

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

    /**
     * 禁用用户 - 仅供内部测试使用
     */
    @Transactional
    public void deactivate(String userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "用户不存在"));
        user.setIsActive(false);
    }

    /**
     * 启用用户 - 仅供内部测试使用
     */
    @Transactional
    public void activate(String userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "用户不存在"));
        user.setIsActive(true);
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

    /**
     * 加载活跃用户 - 供工单、通知等业务使用
     */
    public User loadActiveUser(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (!user.getIsActive()) {
            throw new BusinessException("用户账户已被禁用");
        }
        return user;
    }

    /**
     * 获取当前用户 - 供内部业务使用
     */
    @Transactional(readOnly = true)
    public UserDto getCurrentUser(String userId) {
        return findById(userId);
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
