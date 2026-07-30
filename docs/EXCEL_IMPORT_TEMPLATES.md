# Excel Import Templates

## Scope

The sales and purchase Excel files are batch-import templates. They are not printable
sales-order or purchase-order documents. Formal document printing remains a separate
future capability because it has different layout, approval, numbering and audit rules.

Customers and suppliers are selected once in the WMS form. Excel contains line items only.

## Download endpoints

- Sales: `GET /api/sales-orders/template`
- Purchase: `GET /api/purchase-orders/template`

Both downloads include an `Instructions` sheet and a refreshed `SKU Reference` sheet.
The reference sheet is capped at 5,000 enabled SKUs to keep downloads bounded.

## External templates

Set the optional environment variable:

```text
WMS_EXCEL_TEMPLATE_DIR=D:\wms-config\excel-templates
```

The directory may contain:

- `sales_order_import_template.xlsx`
- `purchase_order_import_template.xlsx`

At download time the server validates the external file. If it is missing, unreadable or
structurally invalid, the server logs a warning and returns the built-in template instead.
The active SKU reference sheet is regenerated for every download.

Operators may change colors, fonts, logos, row heights, column widths and instruction
sheets. Do not rename the data sheet, reorder columns or change machine headers.

## Required structures

Sales data sheet: `Sales Order Lines`

```text
productSkuId | quantity | unitPrice | rejectNearExpiry | specifiedBatchIds | remark
```

Purchase data sheet: `Purchase Order Lines`

```text
productSkuId | orderedQuantity | unitCost | expiryDate | productionDate | externalBatchCode | remark
```

Data starts on row 2. Date values use `yyyy-MM-dd`. Changed or missing headers are rejected
with `INVALID_EXCEL_DATA` before any order is created.

## Deployment check

1. Leave `WMS_EXCEL_TEMPLATE_DIR` unset to verify the built-in fallback.
2. Download both templates and verify the SKU reference is current.
3. Place customized copies in the configured directory and download again.
4. Rename one required header in a test copy and confirm upload is rejected.
5. Restore the valid template before production use.
