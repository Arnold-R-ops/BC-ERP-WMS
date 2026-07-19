package com.wms.system.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Inbound Item DTO (入库单项 DTO)
 *
 * V3.3 Feature: Split Putaway Support (分散上架支持)
 *
 * 业务场景：
 * - 采购订单到货后，仓库管理员进行上架操作
 * - 支持将同一批次的商品分散摆放在不同库位
 * - 例如：100 箱绿茶（Batch B01）→ 60 箱放 A-1-101，40 箱放 A-1-102
 *
 * 使用方式：
 * - 前端或 Excel 解析生成 List<InboundItemDto>
 * - 传递给 InventoryService.processInbound() 方法
 * - 后端根据 (batchCode + locationCode) 判断是累加还是新建记录
 *
 * 数量单位说明：
 * - quantity 字段使用"最小单位"（基础单位）
 * - 例如：SKU 换算率 conversionRate = 12（1箱=12盒）
 *   - 入库 60 箱 = quantity = 60 * 12 = 720 盒
 *   - 前端可调用 ProductSku.formatQuantity(720) 显示为 "60 Box"
 *
 * @author WMS Team
 * @since V3.3
 * @version 3.3 (Split Putaway Support)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundItemDto {

    /**
     * SKU ID（ProductSku 表主键）
     *
     * 用途：
     * - 关联到具体的 SKU 商品
     * - 用于查询商品的换算率、包装单位等信息
     *
     * 必填
     */
    @NotNull(message = "SKU ID 不能为空")
    private Long skuId;

    /**
     * 批次码（Batch Code）
     *
     * 说明：
     * - 系统生成的唯一批次码（如 R7M4K9）
     * - 或由前端/Excel 导入提供
     * - 同一批次可以分散在多个库位
     *
     * 生成方式：
     * - 前端：调用后端 API 生成批次码
     * - Excel 导入：解析 Excel 中的批次码列
     * - 手动录入：仓库管理员手动输入
     *
     * 必填
     */
    @NotBlank(message = "批次码不能为空")
    @Size(max = 20, message = "批次码长度不能超过 20 个字符")
    private String batchCode;

    /**
     * 入库数量（最小单位）
     *
     * 说明：
     * - 使用"基础单位"表示数量（如 "盒"、"瓶"、"个"）
     * - 不是"包装单位"（如 "箱"）
     *
     * 换算示例（conversionRate = 12）：
     * - 前端输入：60 箱
     * - 转换为最小单位：60 * 12 = 720 盒
     * - 传递给后端：quantity = 720
     *
     * 必填，必须大于 0
     */
    @NotNull(message = "入库数量不能为空")
    @Min(value = 1, message = "入库数量必须大于 0")
    private Integer quantity;

    /**
     * 库位编号（Location Code）
     *
     * V3.3 核心字段：支持 Split Putaway
     *
     * 说明：
     * - 字符串格式的库位编号（如 "A-1-101", "B-2-205"）
     * - 同一批次可以分配到不同库位
     * - 与 batchCode 组合，唯一标识一条库存记录
     *
     * 命名规范建议：
     * - 区域-巷道-货架-层-位（如 A-01-05-02-03）
     * - 或简化为 区域-编号（如 A-101）
     *
     * 业务规则：
     * - 必须是系统中已存在的库位编号
     * - 库位不能是"已禁用"状态
     * - 库位容量需要足够（如果有容量限制）
     *
     * 必填
     */
    @NotBlank(message = "库位编号不能为空")
    @Size(max = 50, message = "库位编号长度不能超过 50 个字符")
    private String locationCode;

    /**
     * 保质期（Expiry Date）
     *
     * 说明：
     * - 商品的过期日期（年-月-日）
     * - 用于 FIFO/FEFO 出库策略
     * - 过期商品将被自动拦截出库
     *
     * 业务规则：
     * - 不能早于当前日期（不允许入库已过期商品）
     * - 建议至少晚于当前日期 7 天（避免入库即将过期商品）
     *
     * 必填
     */
    @NotNull(message = "保质期不能为空")
    @Future(message = "保质期必须晚于当前日期")
    private LocalDate expiryDate;

    /**
     * 生产日期（Production Date，可选）
     *
     * 说明：
     * - 商品的生产日期（年-月-日）
     * - 用于追溯和质量管理
     * - 不是必填字段
     */
    private LocalDate productionDate;

    /**
     * 厂家批次码（External Batch Code，可选）
     *
     * 说明：
     * - 供应商/厂家提供的原始批次号
     * - 用于供应商索赔和追溯
     * - 与系统内部批次码 batchCode 区分
     *
     * 示例：
     * - batchCode: R7M4K9（系统内部码）
     * - externalBatchCode: 2025011201（厂家批次号）
     */
    @Size(max = 100, message = "厂家批次码长度不能超过 100 个字符")
    private String externalBatchCode;

    /**
     * 采购单明细 ID（Purchase Order Item ID，可选）
     *
     * 说明：
     * - 关联到采购单明细（PurchaseOrderItem）
     * - 用于回溯批次来源和采购订单
     * - 如果是手动入库（非采购单入库），可以为空
     */
    private Long purchaseOrderItemId;

    /**
     * 备注（Remark，可选）
     *
     * 说明：
     * - 入库时的备注信息
     * - 例如："质量良好"、"包装破损 2 箱"
     */
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    /**
     * 操作员 ID（Operator ID）
     *
     * 说明：
     * - 执行入库操作的用户 ID
     * - 用于审计和追踪
     *
     * 必填
     */
    @NotNull(message = "操作员 ID 不能为空")
    private Long operatorId;

    /**
     * 操作员姓名（Operator Name）
     *
     * 说明：
     * - 执行入库操作的用户姓名
     * - 用于审计和追踪
     *
     * 必填
     */
    @NotBlank(message = "操作员姓名不能为空")
    @Size(max = 50, message = "操作员姓名长度不能超过 50 个字符")
    private String operatorName;
}
