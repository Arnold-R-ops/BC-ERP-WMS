package com.wms.system.util;

/**
 * 数据脱敏工具类
 *
 * V4.1 架构：销售权限隔离与数据脱敏
 *
 * 核心职责：
 * 1. 姓名脱敏：保留第一个字，其余掩码
 * 2. 手机号脱敏：保留前3后4，中间掩码
 * 3. 邮箱脱敏：保留首字符和@后缀
 * 4. 地址脱敏：隐藏详细街道/门牌信息
 *
 * 业务场景：
 * - 销售员查看客户信息时，敏感字段自动脱敏
 * - 管理员和经理查看原始数据
 * - 保护客户隐私，防止数据泄露
 *
 * 技术特性：
 * - 静态方法，无需实例化
 * - 空值安全，避免 NullPointerException
 * - 支持各种边界情况（空字符串、短字符串等）
 *
 * 使用示例：
 * <pre>
 * String maskedName = MaskingUtils.maskName("张三");        // 返回 "张*"
 * String maskedPhone = MaskingUtils.maskPhone("13912345678"); // 返回 "139****5678"
 * String maskedEmail = MaskingUtils.maskEmail("john@gmail.com"); // 返回 "j***@gmail.com"
 * String maskedAddr = MaskingUtils.maskAddress("北京市朝阳区建国路88号"); // 返回 "北京市朝阳区***"
 * </pre>
 *
 * @author WMS Team
 * @since 2026-02-12
 * @version 4.1 (Customer Data Security)
 */
public class MaskingUtils {

    /**
     * 掩码字符
     */
    private static final String MASK_CHAR = "*";

    /**
     * 私有构造函数，防止实例化
     */
    private MaskingUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 姓名脱敏
     *
     * 规则：
     * - 保留第一个字，其余用 * 替换
     * - 如果姓名为空或只有1个字，返回原值
     *
     * 示例：
     * - "张三" → "张*"
     * - "李四光" → "李**"
     * - "欧阳修" → "欧**"
     * - "张" → "张"
     * - null → null
     * - "" → ""
     *
     * @param name 原始姓名
     * @return 脱敏后的姓名
     */
    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }

        if (name.length() == 1) {
            return name;
        }

        // 保留第一个字，其余用 * 替换
        return name.charAt(0) + MASK_CHAR.repeat(name.length() - 1);
    }

    /**
     * 手机号脱敏
     *
     * 规则：
     * - 保留前3位和后4位，中间用 **** 替换
     * - 如果手机号长度不足7位，返回原值
     * - 如果手机号为空，返回原值
     *
     * 示例：
     * - "13912345678" → "139****5678"
     * - "18888888888" → "188****8888"
     * - "12345" → "12345"（长度不足，不脱敏）
     * - null → null
     * - "" → ""
     *
     * @param phone 原始手机号
     * @return 脱敏后的手机号
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return phone;
        }

        // 如果手机号长度不足7位，不脱敏
        if (phone.length() < 7) {
            return phone;
        }

        // 保留前3位和后4位
        String prefix = phone.substring(0, 3);
        String suffix = phone.substring(phone.length() - 4);

        return prefix + "****" + suffix;
    }

    /**
     * 邮箱脱敏
     *
     * 规则：
     * - 保留首字符和 @ 后的域名
     * - @ 前的其余字符用 *** 替换
     * - 如果邮箱格式不正确（不包含@），返回原值
     * - 如果邮箱为空，返回原值
     *
     * 示例：
     * - "john@gmail.com" → "j***@gmail.com"
     * - "alice.wang@company.com" → "a***@company.com"
     * - "a@b.com" → "a***@b.com"
     * - "invalid-email" → "invalid-email"（格式不正确，不脱敏）
     * - null → null
     * - "" → ""
     *
     * @param email 原始邮箱
     * @return 脱敏后的邮箱
     */
    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }

        // 查找 @ 符号的位置
        int atIndex = email.indexOf('@');

        // 如果不包含 @，返回原值
        if (atIndex <= 0) {
            return email;
        }

        // 保留首字符和 @ 后的域名
        String prefix = email.substring(0, 1);
        String domain = email.substring(atIndex);

        return prefix + "***" + domain;
    }

    /**
     * 地址脱敏
     *
     * 规则：
     * - 保留前面的省市区信息（前9个字符）
     * - 隐藏详细街道/门牌信息
     * - 如果地址长度不足10个字符，返回原值
     * - 如果地址为空，返回原值
     *
     * 示例：
     * - "北京市朝阳区建国路88号" → "北京市朝阳区***"
     * - "上海市浦东新区世纪大道1号" → "上海市浦东新区***"
     * - "广州市" → "广州市"（长度不足，不脱敏）
     * - null → null
     * - "" → ""
     *
     * @param address 原始地址
     * @return 脱敏后的地址
     */
    public static String maskAddress(String address) {
        if (address == null || address.isEmpty()) {
            return address;
        }

        // 如果地址长度不足10个字符，不脱敏
        if (address.length() < 10) {
            return address;
        }

        // 保留前9个字符（通常是省市区信息）
        return address.substring(0, 9) + "***";
    }

    /**
     * 检查字符串是否包含掩码字符
     *
     * 用于判断字段是否已被脱敏，避免更新时覆盖原始数据
     *
     * 示例：
     * - "139****5678" → true
     * - "13912345678" → false
     * - "张*" → true
     * - "张三" → false
     *
     * @param value 待检查的字符串
     * @return 如果包含掩码字符返回 true，否则返回 false
     */
    public static boolean isMasked(String value) {
        return value != null && value.contains(MASK_CHAR);
    }
}
