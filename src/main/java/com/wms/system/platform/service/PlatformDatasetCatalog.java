package com.wms.system.platform.service;

import java.util.*;

public final class PlatformDatasetCatalog {
    private PlatformDatasetCatalog() {}

    public record Dataset(String code, List<String> headers, String selectJpql, String countJpql) {}

    private static Dataset dataset(String code, String entity, String fields, String... headers) {
        return new Dataset(code, List.of(headers), "select " + fields + " from " + entity + " e order by e.id",
            "select count(e.id) from " + entity + " e");
    }

    private static final Map<String, Dataset> DATASETS;
    static {
        List<Dataset> values = List.of(
            dataset("users", "User", "e.id,e.username,e.displayName,e.enabled,e.createdAt,e.updatedAt",
                "id","username","display_name","enabled","created_at","updated_at"),
            dataset("roles", "SysRole", "e.id,e.roleCode,e.roleName,e.roleType,e.status,e.createdAt,e.updatedAt",
                "id","role_code","role_name","role_type","status","created_at","updated_at"),
            dataset("warehouses", "Warehouse", "e.id,e.code,e.name,e.address,e.contact,e.phone,e.isActive,e.createdAt,e.updatedAt",
                "id","code","name","address","contact","phone","active","created_at","updated_at"),
            dataset("locations", "Location", "e.id,e.warehouse.id,e.locationCode,e.zone,e.shelfNumber,e.positionNumber,e.enabled,e.status,e.createdAt,e.updatedAt",
                "id","warehouse_id","location_code","zone","shelf_number","position_number","enabled","status","created_at","updated_at"),
            dataset("products", "Product", "e.id,e.productCode,e.productName,e.category.id,e.brand,e.enabled,e.createdAt,e.updatedAt",
                "id","product_code","product_name","category_id","brand","enabled","created_at","updated_at"),
            dataset("skus", "ProductSku", "e.id,e.product.id,e.skuCode,e.skuName,e.barcode,e.unitPrice,e.enabled,e.createdAt,e.updatedAt",
                "id","product_id","sku_code","sku_name","barcode","unit_price","enabled","created_at","updated_at"),
            dataset("inventory", "Inventory", "e.id,e.productSku.id,e.location.id,e.quantity,e.version,e.createdAt,e.updatedAt",
                "id","product_sku_id","location_id","quantity","version","created_at","updated_at"),
            dataset("inventory_batches", "InventoryBatch", "e.id,e.batchCode,e.productSku.id,e.location.id,e.quantity,e.reservedQuantity,e.expiryDate,e.active,e.createdAt,e.updatedAt",
                "id","batch_code","product_sku_id","location_id","quantity","reserved_quantity","expiry_date","active","created_at","updated_at"),
            dataset("customers", "Customer", "e.id,e.code,e.name,e.customerType,e.source,e.contact,e.phone,e.email,e.address,e.isActive,e.createdAt,e.updatedAt",
                "id","code","name","customer_type","source","contact","phone","email","address","active","created_at","updated_at"),
            dataset("suppliers", "Supplier", "e.id,e.code,e.name,e.contact,e.phone,e.email,e.address,e.isActive,e.createdAt,e.updatedAt",
                "id","code","name","contact","phone","email","address","active","created_at","updated_at"),
            dataset("purchase_orders", "PurchaseOrder", "e.id,e.poNumber,e.supplier,e.status,e.totalQuantity,e.totalCost,e.expectedDate,e.createdAt,e.updatedAt",
                "id","po_number","supplier","status","total_quantity","total_cost","expected_date","created_at","updated_at"),
            dataset("sales_orders", "SalesOrder", "e.id,e.orderNo,e.customerId,e.totalAmount,e.status,e.channel,e.externalOrderNo,e.createdAt,e.updatedAt",
                "id","order_no","customer_id","total_amount","status","channel","external_order_no","created_at","updated_at"),
            dataset("inbound_orders", "InboundOrder", "e.id,e.orderNo,e.supplier.id,e.status,e.totalPlanQty,e.totalActualQty,e.expectedDate,e.createdAt,e.updatedAt",
                "id","order_no","supplier_id","status","planned_quantity","actual_quantity","expected_date","created_at","updated_at"),
            dataset("outbound_tasks", "OutboundTask", "e.id,e.salesOrderId,e.salesOrderItemId,e.locationId,e.planQty,e.actualQty,e.status,e.createdAt,e.updatedAt",
                "id","sales_order_id","sales_order_item_id","location_id","planned_quantity","actual_quantity","status","created_at","updated_at"),
            dataset("stocktakes", "StocktakeTask", "e.id,e.taskNo,e.warehouseId,e.cycleType,e.status,e.snapshotTime,e.totalItems,e.countedItems,e.differenceItems,e.createdAt,e.updatedAt",
                "id","task_no","warehouse_id","cycle_type","status","snapshot_time","total_items","counted_items","difference_items","created_at","updated_at"),
            dataset("integrations", "IntegrationConfig", "e.id,e.platform,e.storeUrl,e.canonicalStoreIdentifier,e.isActive,e.retailMode,e.lastSyncAt,e.createdAt,e.updatedAt",
                "id","platform","store_url","canonical_store_identifier","active","retail_mode","last_sync_at","created_at","updated_at")
        );
        Map<String, Dataset> map = new LinkedHashMap<>();
        values.forEach(value -> map.put(value.code(), value));
        DATASETS = Collections.unmodifiableMap(map);
    }

    public static Collection<Dataset> all() { return DATASETS.values(); }
    public static Optional<Dataset> find(String code) { return Optional.ofNullable(DATASETS.get(code)); }
}
