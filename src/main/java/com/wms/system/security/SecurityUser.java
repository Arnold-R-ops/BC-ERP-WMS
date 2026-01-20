package com.wms.system.security;

import com.wms.system.entity.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Spring Security 用户身份包装类
 * 用于将数据库实体 User 转换为 Security 可识别的 UserDetails
 */
@Getter
@RequiredArgsConstructor
public class SecurityUser implements UserDetails {

    // 包装现有的数据库实体
    private final User user;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // v3.3 Multi-Role System:
        // Authorities are now loaded dynamically from JWT token (JwtAuthenticationFilter)
        // This method returns empty list; actual authorities come from token's current_role claim
        return Collections.emptyList();
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    // ========== 账户状态设置 (默认全部通过) ==========

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // 如果你的 User 表有 locked 字段，可以改为: return !user.isLocked();
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        // 返回用户实体的 enabled 字段值
        // 如果 enabled 为 null，默认返回 true（向后兼容）
        return user.getEnabled() != null ? user.getEnabled() : true;
    }

    // ========== 便捷方法 (快捷代理内部 User 对象) ==========

    /**
     * 获取用户 ID
     * 代理调用内部 user.getId()
     *
     * @return 用户 ID
     */
    public Long getId() {
        return user.getId();
    }
}