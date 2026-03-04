package com.wms.system.scheduler;

import com.wms.system.dto.stocktake.CreateStocktakeTaskRequest;
import com.wms.system.entity.enums.StocktakeCycleType;
import com.wms.system.service.StocktakeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 盘点任务定时调度器
 *
 * V3.8 架构：智能盘点系统
 *
 * 功能：
 * - 自动创建月度盘点任务
 * - 自动创建季度盘点任务
 *
 * 调度规则：
 * - 月度盘点：每月 28 日凌晨 2 点自动生成
 * - 季度盘点：每季度最后一天凌晨 2 点自动生成
 *
 * 使用说明：
 * 1. 在 application.yml 中启用调度：
 *    spring:
 *      task:
 *        scheduling:
 *          enabled: true
 *
 * 2. 配置系统用户 ID（用于创建任务）：
 *    stocktake:
 *      scheduler:
 *        enabled: true
 *        system-user-id: 1
 *        system-user-name: "系统管理员"
 *        default-warehouse-id: 1
 *
 * 3. 如果不需要自动调度，可以在配置中禁用：
 *    stocktake:
 *      scheduler:
 *        enabled: false
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.8 (Smart Stocktake System)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StocktakeScheduler {

    private final StocktakeService stocktakeService;

    // 配置项（可以从 application.yml 读取）
    // @Value("${stocktake.scheduler.enabled:false}")
    // private Boolean schedulerEnabled;
    //
    // @Value("${stocktake.scheduler.system-user-id:1}")
    // private Long systemUserId;
    //
    // @Value("${stocktake.scheduler.system-user-name:系统管理员}")
    // private String systemUserName;
    //
    // @Value("${stocktake.scheduler.default-warehouse-id:1}")
    // private Long defaultWarehouseId;

    /**
     * 月度盘点任务调度
     *
     * Cron 表达式：0 0 2 28 * ?
     * 说明：每月 28 日凌晨 2 点执行
     *
     * 为什么选择 28 日：
     * - 所有月份都有 28 日（避免 2 月问题）
     * - 给月底结账留出缓冲时间
     * - 可以在月底前完成盘点和平账
     *
     * 注意：
     * - 如果不需要自动调度，请注释掉 @Scheduled 注解
     * - 或在配置文件中设置 stocktake.scheduler.enabled=false
     */
    // @Scheduled(cron = "0 0 2 28 * ?")  // 每月 28 日凌晨 2 点
    public void scheduleMonthlyStocktake() {
        log.info("🗓️ 开始执行月度盘点任务调度...");

        try {
            // 检查是否启用调度
            // if (!schedulerEnabled) {
            //     log.info("月度盘点调度已禁用，跳过执行");
            //     return;
            // }

            // 创建月度盘点任务
            CreateStocktakeTaskRequest request = CreateStocktakeTaskRequest.builder()
                    .warehouseId(1L)  // 默认仓库 ID（可配置）
                    .cycleType(StocktakeCycleType.MONTHLY.name())
                    .build();

            // 使用系统用户创建任务
            Long systemUserId = 1L;  // 系统用户 ID（可配置）
            String systemUserName = "System Scheduler";  // 系统用户名（可配置）

            var taskResponse = stocktakeService.createCycleTask(request, systemUserId, systemUserName);

            log.info("✅ 月度盘点任务创建成功: taskNo={}, totalItems={}",
                    taskResponse.getTaskNo(), taskResponse.getTotalItems());

        } catch (Exception e) {
            log.error("❌ 月度盘点任务调度失败", e);
            // 可以发送告警通知
        }
    }

    /**
     * 季度盘点任务调度
     *
     * Cron 表达式：0 0 2 L 3,6,9,12 ?
     * 说明：每季度最后一天凌晨 2 点执行
     *
     * 执行时间：
     * - 3 月最后一天（第一季度）
     * - 6 月最后一天（第二季度）
     * - 9 月最后一天（第三季度）
     * - 12 月最后一天（第四季度）
     *
     * 注意：
     * - L 表示月份的最后一天
     * - 季度盘点是全量盘点，数据量较大
     * - 建议在业务低峰期执行
     */
    // @Scheduled(cron = "0 0 2 L 3,6,9,12 ?")  // 每季度最后一天凌晨 2 点
    public void scheduleQuarterlyStocktake() {
        log.info("🗓️ 开始执行季度盘点任务调度...");

        try {
            // 检查是否启用调度
            // if (!schedulerEnabled) {
            //     log.info("季度盘点调度已禁用，跳过执行");
            //     return;
            // }

            // 创建季度盘点任务
            CreateStocktakeTaskRequest request = CreateStocktakeTaskRequest.builder()
                    .warehouseId(1L)  // 默认仓库 ID（可配置）
                    .cycleType(StocktakeCycleType.QUARTERLY.name())
                    .build();

            // 使用系统用户创建任务
            Long systemUserId = 1L;  // 系统用户 ID（可配置）
            String systemUserName = "System Scheduler";  // 系统用户名（可配置）

            var taskResponse = stocktakeService.createCycleTask(request, systemUserId, systemUserName);

            log.info("✅ 季度盘点任务创建成功: taskNo={}, totalItems={}",
                    taskResponse.getTaskNo(), taskResponse.getTotalItems());

        } catch (Exception e) {
            log.error("❌ 季度盘点任务调度失败", e);
            // 可以发送告警通知
        }
    }

    /**
     * 测试调度（仅用于开发测试）
     *
     * Cron 表达式：0 0/5 * * * ?
     * 说明：每 5 分钟执行一次
     *
     * 注意：
     * - 仅用于开发测试
     * - 生产环境请删除或注释此方法
     */
    // @Scheduled(cron = "0 0/5 * * * ?")  // 每 5 分钟执行一次（测试用）
    public void testSchedule() {
        log.debug("🧪 测试调度执行 - 当前时间: {}", java.time.LocalDateTime.now());
    }
}
