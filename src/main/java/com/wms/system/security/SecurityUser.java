package com.wms.system.security;

import com.wms.system.entity.User;
import com.wms.system.entity.enums.Role;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
        // 将 Role 枚举转换为 Spring Security 的 Authority
        // 假设 User 中有 getRole() 方法，且 Role 是枚举
        if (user.getRole() != null) {
            return Collections.singletonList(
                    new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
            );
        }
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
        // 如果你的 User 表有 enabled 字段，可以改为: return user.isEnabled();
        return true;
    }

    // ========== 便捷方法 (快捷代理内部 User 对象) ==========

    /**
     * 获取用户角色
     * 代理调用内部 user.getRole()
     *
     * @return 用户角色枚举
     */
    public Role getRole() {
        return user.getRole();
    }

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