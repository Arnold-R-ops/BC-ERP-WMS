package com.wms.system.repository;

import com.wms.system.entity.User;
import com.wms.system.entity.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户数据访问接口
 * 继承 JpaRepository，自动获得 CRUD 方法（save, findById, findAll, delete 等）
 *
 * 自动实现的方法（无需手写实现）：
 * - save(User user): 保存或更新用户
 * - findById(Long id): 根据ID查询用户
 * - findAll(): 查询所有用户
 * - deleteById(Long id): 根据ID删除用户
 * - count(): 统计用户总数
 *
 * Spring Data JPA 方法命名规范：
 * - findBy{字段名}: 根据字段查询
 * - existsBy{字段名}: 检查是否存在
 * - countBy{字段名}: 统计数量
 * - deleteBy{字段名}: 根据条件删除
 *
 * @author WMS Team
 * @since 2025-01-09
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 根据用户名查询用户（用于登录验证）
     * 方法命名规范：findBy + 字段名（Username）
     *
     * @param username 用户名
     * @return Optional<User> 用户对象（可能为空）
     */
    Optional<User> findByUsername(String username);

    /**
     * 检查用户名是否存在（用于注册时校验）
     * 方法命名规范：existsBy + 字段名
     *
     * @param username 用户名
     * @return true 表示用户名已存在
     */
    boolean existsByUsername(String username);

    /**
     * 根据角色查询用户列表
     * 方法命名规范：findBy + 字段名（Role）
     *
     * @param role 用户角色（ADMIN / STAFF）
     * @return 该角色的所有用户
     */
    List<User> findByRole(Role role);

    /**
     * 查询所有启用的用户
     * 方法命名规范：findBy + 字段名（Enabled） + 条件（True）
     *
     * @return 所有启用状态的用户
     */
    List<User> findByEnabledTrue();

    /**
     * 根据用户名模糊查询（用于用户搜索功能）
     * 方法命名规范：findBy + 字段名（Username） + Containing
     *
     * @param keyword 搜索关键词
     * @return 用户名包含关键词的所有用户
     */
    List<User> findByUsernameContaining(String keyword);

    /**
     * 根据角色和启用状态查询
     * 方法命名规范：findBy + 字段1（Role） + And + 字段2（Enabled）
     *
     * @param role 用户角色
     * @param enabled 是否启用
     * @return 符合条件的用户列表
     */
    List<User> findByRoleAndEnabled(Role role, Boolean enabled);
}
