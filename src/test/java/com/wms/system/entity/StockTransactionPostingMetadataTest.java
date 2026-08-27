package com.wms.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StockTransaction posting metadata mapping")
class StockTransactionPostingMetadataTest {

    @Test
    @DisplayName("traceability fields map to the migration column contract")
    void traceabilityFieldsUseExpectedColumns() throws Exception {
        assertColumn("warehouseId", "warehouse_id", 255);
        assertColumn("inventoryBatchId", "inventory_batch_id", 255);
        assertColumn("batchCode", "batch_code", 50);
        assertColumn("postingId", "posting_id", 120);
        assertColumn("idempotencyKey", "idempotency_key", 120);
    }

    @Test
    @DisplayName("posting lookup columns have explicit entity indexes")
    void postingColumnsHaveIndexes() {
        Table table = StockTransaction.class.getAnnotation(Table.class);
        Map<String, String> indexes = Arrays.stream(table.indexes())
            .collect(Collectors.toMap(Index::name, Index::columnList));

        assertThat(indexes)
            .containsEntry("idx_stock_tx_warehouse_id", "company_id,warehouse_id")
            .containsEntry("idx_stock_tx_inventory_batch_id", "company_id,inventory_batch_id")
            .containsEntry("idx_stock_tx_posting_id", "company_id,posting_id")
            .containsEntry("idx_stock_tx_idempotency_key", "company_id,idempotency_key");
    }

    private void assertColumn(String fieldName, String columnName, int length) throws Exception {
        Field field = StockTransaction.class.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);

        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.length()).isEqualTo(length);
    }
}
