package com.wms.system.repository;

import com.wms.system.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 客户数据访问接口
 *
 * V3.7 架构：客户管理
 *
 * 核心功能：
 * 1. 基础 CRUD 操作（继承自 JpaRepository）
 * 2. 根据客户编码查询客户
 * 3. 查询激活的客户列表
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * 根据客户编码查询客户
     *
     * 使用场景：
     * 1. 创建销售订单时选择客户
     * 2. 客户信息查询
     *
     * @param code 客户编码
     * @return Optional<Customer> 客户对象（可能为空）
     */
    Optional<Customer> findByCode(String code);

    /**
     * 检查客户编码是否已存在
     *
     * @param code 客户编码
     * @return true 表示客户编码已存在
     */
    boolean existsByCode(String code);

    /**
     * 查询所有激活的客户
     *
     * @return 所有激活状态的客户
     */
    List<Customer> findByIsActiveTrue();

    /**
     * 根据客户名称模糊查询
     *
     * @param keyword 搜索关键词
     * @return 客户名称包含关键词的所有客户
     */
    List<Customer> findByNameContaining(String keyword);

    /**
     * 根据邮箱查询客户
     *
     * 使用场景：
     * 1. Shopify 订单同步时匹配客户
     * 2. 客户去重检查
     *
     * @param email 客户邮箱
     * @return Optional<Customer> 客户对象（可能为空）
     */
    Optional<Customer> findByEmail(String email);

    /**
     * 根据归属人ID查询客户列表（V4.1 行级隔离）
     *
     * 使用场景：
     * - 销售员查看自己的客户列表
     * - 行级隔离（Row-Level Security）
     *
     * @param ownerId 归属人ID
     * @return 该归属人的所有客户
     * @since V4.1 (Customer Data Security)
     */
    List<Customer> findByOwnerId(Long ownerId);

    /**
     * 根据归属人ID查询激活的客户列表（V4.1 行级隔离）
     *
     * 使用场景：
     * - 销售员查看自己的激活客户列表
     * - 行级隔离（Row-Level Security）
     *
     * @param ownerId 归属人ID
     * @return 该归属人的所有激活客户
     * @since V4.1 (Customer Data Security)
     */
    List<Customer> findByOwnerIdAndIsActiveTrue(Long ownerId);
}
