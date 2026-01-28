package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.entity.Warehouse;
import com.wms.system.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WarehouseController 完整集成测试
 *
 * 测试范围：
 * 1. GET /api/warehouses - 获取所有仓库
 * 2. GET /api/warehouses/active - 获取所有激活的仓库
 * 3. GET /api/warehouses/{id} - 根据ID获取仓库
 * 4. GET /api/warehouses/code/{code} - 根据编码获取仓库
 * 5. POST /api/warehouses - 创建仓库
 * 6. PUT /api/warehouses/{id} - 更新仓库
 * 7. PUT /api/warehouses/{id}/activate - 激活仓库
 * 8. PUT /api/warehouses/{id}/deactivate - 停用仓库
 * 9. GET /api/warehouses/{id}/location-count - 获取库位数量
 *
 * @author WMS Team
 * @since 2026-01-28
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestSecurityConfig.class)
@DisplayName("仓库控制器完整集成测试")
class WarehouseControllerCompleteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WarehouseRepository warehouseRepository;

    private Warehouse testWarehouse1;
    private Warehouse testWarehouse2;
    private Warehouse inactiveWarehouse;

    @BeforeEach
    void setUp() {
        // 清理数据
        warehouseRepository.deleteAll();

        // 创建测试数据
        testWarehouse1 = Warehouse.builder()
            .code("WH01")
            .name("主仓库")
            .address("上海市浦东新区张江高科技园区")
            .contact("张三 13800138000")
            .isActive(true)
            .build();
        testWarehouse1 = warehouseRepository.save(testWarehouse1);

        testWarehouse2 = Warehouse.builder()
            .code("WH02")
            .name("分仓库")
            .address("北京市朝阳区")
            .contact("李四 13900139000")
            .isActive(true)
            .build();
        testWarehouse2 = warehouseRepository.save(testWarehouse2);

        inactiveWarehouse = Warehouse.builder()
            .code("WH03")
            .name("已停用仓库")
            .address("深圳市南山区")
            .contact("王五 13700137000")
            .isActive(false)
            .build();
        inactiveWarehouse = warehouseRepository.save(inactiveWarehouse);
    }

    // ========== 查询所有仓库测试 ==========

    @Test
    @DisplayName("GET /api/warehouses - 获取所有仓库")
    void getAllWarehouses_Success() throws Exception {
        mockMvc.perform(get("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].code").value("WH01"))
            .andExpect(jsonPath("$[1].code").value("WH02"))
            .andExpect(jsonPath("$[2].code").value("WH03"));
    }

    @Test
    @DisplayName("GET /api/warehouses/active - 获取所有激活的仓库")
    void getAllActiveWarehouses_Success() throws Exception {
        mockMvc.perform(get("/api/warehouses/active")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].code").value("WH01"))
            .andExpect(jsonPath("$[0].isActive").value(true))
            .andExpect(jsonPath("$[1].code").value("WH02"))
            .andExpect(jsonPath("$[1].isActive").value(true));
    }

    @Test
    @DisplayName("GET /api/warehouses - 空数据库")
    void getAllWarehouses_EmptyDatabase() throws Exception {
        // 清空数据
        warehouseRepository.deleteAll();

        mockMvc.perform(get("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    // ========== 根据ID查询仓库测试 ==========

    @Test
    @DisplayName("GET /api/warehouses/{id} - 根据ID获取仓库")
    void getWarehouseById_Success() throws Exception {
        mockMvc.perform(get("/api/warehouses/{id}", testWarehouse1.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testWarehouse1.getId()))
            .andExpect(jsonPath("$.code").value("WH01"))
            .andExpect(jsonPath("$.name").value("主仓库"))
            .andExpect(jsonPath("$.address").value("上海市浦东新区张江高科技园区"))
            .andExpect(jsonPath("$.contact").value("张三 13800138000"))
            .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("GET /api/warehouses/{id} - 仓库不存在")
    void getWarehouseById_NotFound() throws Exception {
        mockMvc.perform(get("/api/warehouses/{id}", 99999L)
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/warehouses/{id} - 无效的ID格式")
    void getWarehouseById_InvalidId() throws Exception {
        mockMvc.perform(get("/api/warehouses/{id}", "invalid")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    // ========== 根据编码查询仓库测试 ==========

    @Test
    @DisplayName("GET /api/warehouses/code/{code} - 根据编码获取仓库")
    void getWarehouseByCode_Success() throws Exception {
        mockMvc.perform(get("/api/warehouses/code/{code}", "WH01")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("WH01"))
            .andExpect(jsonPath("$.name").value("主仓库"));
    }

    @Test
    @DisplayName("GET /api/warehouses/code/{code} - 编码不存在")
    void getWarehouseByCode_NotFound() throws Exception {
        mockMvc.perform(get("/api/warehouses/code/{code}", "INVALID")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isNotFound());
    }

    // ========== 创建仓库测试 ==========

    @Test
    @DisplayName("POST /api/warehouses - 创建仓库（所有字段）")
    void createWarehouse_AllFields() throws Exception {
        String requestBody = """
            {
                "code": "WH04",
                "name": "新仓库",
                "address": "广州市天河区",
                "contact": "赵六 13600136000"
            }
            """;

        mockMvc.perform(post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("WH04"))
            .andExpect(jsonPath("$.name").value("新仓库"))
            .andExpect(jsonPath("$.address").value("广州市天河区"))
            .andExpect(jsonPath("$.contact").value("赵六 13600136000"))
            .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("POST /api/warehouses - 创建仓库（只有必填字段）")
    void createWarehouse_RequiredFieldsOnly() throws Exception {
        String requestBody = """
            {
                "code": "WH05",
                "name": "简单仓库"
            }
            """;

        mockMvc.perform(post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("WH05"))
            .andExpect(jsonPath("$.name").value("简单仓库"))
            .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("POST /api/warehouses - 编码已存在")
    void createWarehouse_CodeAlreadyExists() throws Exception {
        String requestBody = """
            {
                "code": "WH01",
                "name": "重复仓库"
            }
            """;

        mockMvc.perform(post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/warehouses - 缺少必填字段（编码）")
    void createWarehouse_MissingCode() throws Exception {
        String requestBody = """
            {
                "name": "无编码仓库"
            }
            """;

        mockMvc.perform(post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/warehouses - 缺少必填字段（名称）")
    void createWarehouse_MissingName() throws Exception {
        String requestBody = """
            {
                "code": "WH06"
            }
            """;

        mockMvc.perform(post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/warehouses - 编码格式不正确")
    void createWarehouse_InvalidCodeFormat() throws Exception {
        String requestBody = """
            {
                "code": "wh01",
                "name": "小写编码仓库"
            }
            """;

        mockMvc.perform(post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    // ========== 更新仓库测试 ==========

    @Test
    @DisplayName("PUT /api/warehouses/{id} - 更新仓库（所有字段）")
    void updateWarehouse_AllFields() throws Exception {
        String requestBody = """
            {
                "name": "更新后的名称",
                "address": "更新后的地址",
                "contact": "更新后的联系方式"
            }
            """;

        mockMvc.perform(put("/api/warehouses/{id}", testWarehouse1.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testWarehouse1.getId()))
            .andExpect(jsonPath("$.code").value("WH01"))  // 编码不变
            .andExpect(jsonPath("$.name").value("更新后的名称"))
            .andExpect(jsonPath("$.address").value("更新后的地址"))
            .andExpect(jsonPath("$.contact").value("更新后的联系方式"));
    }

    @Test
    @DisplayName("PUT /api/warehouses/{id} - 只更新名称")
    void updateWarehouse_OnlyName() throws Exception {
        String requestBody = """
            {
                "name": "新名称"
            }
            """;

        mockMvc.perform(put("/api/warehouses/{id}", testWarehouse1.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("新名称"))
            .andExpect(jsonPath("$.address").value("上海市浦东新区张江高科技园区"))  // 未改变
            .andExpect(jsonPath("$.contact").value("张三 13800138000"));  // 未改变
    }

    @Test
    @DisplayName("PUT /api/warehouses/{id} - 仓库不存在")
    void updateWarehouse_NotFound() throws Exception {
        String requestBody = """
            {
                "name": "新名称"
            }
            """;

        mockMvc.perform(put("/api/warehouses/{id}", 99999L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isNotFound());
    }

    // ========== 激活/停用仓库测试 ==========

    @Test
    @DisplayName("PUT /api/warehouses/{id}/activate - 激活仓库")
    void activateWarehouse_Success() throws Exception {
        mockMvc.perform(put("/api/warehouses/{id}/activate", inactiveWarehouse.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(inactiveWarehouse.getId()))
            .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("PUT /api/warehouses/{id}/activate - 仓库不存在")
    void activateWarehouse_NotFound() throws Exception {
        mockMvc.perform(put("/api/warehouses/{id}/activate", 99999L)
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/warehouses/{id}/deactivate - 停用仓库")
    void deactivateWarehouse_Success() throws Exception {
        mockMvc.perform(put("/api/warehouses/{id}/deactivate", testWarehouse1.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testWarehouse1.getId()))
            .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    @DisplayName("PUT /api/warehouses/{id}/deactivate - 仓库不存在")
    void deactivateWarehouse_NotFound() throws Exception {
        mockMvc.perform(put("/api/warehouses/{id}/deactivate", 99999L)
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isNotFound());
    }

    // ========== 获取库位数量测试 ==========

    @Test
    @DisplayName("GET /api/warehouses/{id}/location-count - 获取库位数量")
    void getLocationCount_Success() throws Exception {
        mockMvc.perform(get("/api/warehouses/{id}/location-count", testWarehouse1.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isNumber());
    }

    @Test
    @DisplayName("GET /api/warehouses/{id}/location-count - 仓库不存在")
    void getLocationCount_WarehouseNotFound() throws Exception {
        // 注意：这个接口不会抛出异常，只是返回 0
        mockMvc.perform(get("/api/warehouses/{id}/location-count", 99999L)
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value(0));
    }

    // ========== 业务流程测试 ==========

    @Test
    @DisplayName("完整业务流程 - 创建、查询、更新、停用、激活")
    void completeWorkflow() throws Exception {
        // 1. 创建仓库
        String createRequest = """
            {
                "code": "WH99",
                "name": "测试仓库",
                "address": "测试地址",
                "contact": "测试联系方式"
            }
            """;

        String createResponse = mockMvc.perform(post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long warehouseId = objectMapper.readTree(createResponse).get("id").asLong();

        // 2. 查询仓库
        mockMvc.perform(get("/api/warehouses/{id}", warehouseId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("WH99"));

        // 3. 更新仓库
        String updateRequest = """
            {
                "name": "更新后的测试仓库"
            }
            """;

        mockMvc.perform(put("/api/warehouses/{id}", warehouseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("更新后的测试仓库"));

        // 4. 停用仓库
        mockMvc.perform(put("/api/warehouses/{id}/deactivate", warehouseId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isActive").value(false));

        // 5. 激活仓库
        mockMvc.perform(put("/api/warehouses/{id}/activate", warehouseId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isActive").value(true));
    }
}
