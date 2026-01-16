# WMS System - RESTful API Documentation

## 📋 Overview

**Version**: 2.0 (Controller Layer + Error Key System)
**Base URL**: `http://localhost:8080/api`
**Last Updated**: 2025-01-11
**Author**: WMS Team

---

## 🎯 API Design Principles

### 1. Error Key System (CLAUDE.md Compliance)

All API errors return **error keys** (NOT hardcoded messages) for frontend i18n translation:

```json
{
  "errorKey": "STOCK_INSUFFICIENT",
  "params": {
    "productId": 123,
    "currentStock": 50,
    "requestedQuantity": 100,
    "shortage": 50
  },
  "timestamp": "2025-01-11T10:30:00+00:00",
  "path": "/api/inventory/adjust",
  "status": 400
}
```

### 2. DTO Pattern (NO Direct Entity Exposure)

- ✅ Request: Use DTO with validation annotations (@Valid, @NotNull, @Min)
- ✅ Response: Convert Entity to DTO before returning
- ❌ NEVER expose Entity classes directly in Controller

### 3. Timezone Handling

- Database: Stores UTC timestamps (OffsetDateTime)
- API Response: Returns ISO 8601 format (e.g., "2025-01-11T10:30:00+00:00")
- Prediction Logic: Converts UTC to Europe/London for date-based calculations

---

## 📡 API Endpoints

### 1. Inventory Management APIs

#### 1.1. Adjust Stock

**Endpoint**: `POST /api/inventory/adjust`

**Description**: Adjust stock (inbound/outbound/adjustment) with automatic transaction recording and audit fields.

**Request Headers**:
```http
Content-Type: application/json
```

**Request Body**:
```json
{
  "productId": 123,
  "locationId": 456,
  "transactionType": "OUT",
  "sourceType": "SALE_OUT",
  "quantity": 50,
  "sourceOrderId": "SO202501110001",
  "operatorId": 1,
  "operatorName": "John Doe",
  "remark": "Sale to customer ABC"
}
```

**Request Body Schema**:

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| `productId` | Long | ✅ Yes | @NotNull | Product ID |
| `locationId` | Long | ✅ Yes | @NotNull | Location ID |
| `transactionType` | String | ✅ Yes | @NotNull, Enum: IN/OUT/ADJUST | Transaction type |
| `sourceType` | String | ✅ Yes | @NotNull, Enum | Source type (PURCHASE_IN, SALE_OUT, etc.) |
| `quantity` | Integer | ✅ Yes | @NotNull, @Min(1) | Adjustment quantity |
| `sourceOrderId` | String | ✅ Yes | @NotBlank | Source order ID |
| `operatorId` | Long | No | - | Operator ID (optional) |
| `operatorName` | String | No | - | Operator name (optional) |
| `remark` | String | No | - | Remark (optional) |

**Success Response (200 OK)**:
```json
{
  "id": 1001,
  "productId": 123,
  "productName": "Coca-Cola 500ml",
  "productBarcode": "4901234567890",
  "locationId": 456,
  "locationCode": "WH01-ZONE_A-A-01-001",
  "transactionType": "OUT",
  "sourceType": "SALE_OUT",
  "quantity": 50,
  "quantityBefore": 200,
  "quantityAfter": 150,
  "sourceOrderId": "SO202501110001",
  "operatorId": 1,
  "operatorName": "John Doe",
  "remark": "Sale to customer ABC",
  "createdAt": "2025-01-11T10:30:00+00:00",
  "updatedAt": "2025-01-11T10:30:00+00:00"
}
```

**Error Response (400 Bad Request - Insufficient Stock)**:
```json
{
  "errorKey": "STOCK_INSUFFICIENT",
  "params": {
    "productId": 123,
    "productName": "Coca-Cola 500ml",
    "locationId": 456,
    "locationCode": "WH01-ZONE_A-A-01-001",
    "currentStock": 50,
    "requestedQuantity": 100,
    "shortage": 50
  },
  "timestamp": "2025-01-11T10:30:00+00:00",
  "path": "/api/inventory/adjust",
  "status": 400
}
```

**Error Response (404 Not Found - Product Not Found)**:
```json
{
  "errorKey": "PRODUCT_NOT_FOUND",
  "params": {
    "productId": 999
  },
  "timestamp": "2025-01-11T10:30:00+00:00",
  "path": "/api/inventory/adjust",
  "status": 404
}
```

**Error Response (409 Conflict - Concurrency Conflict)**:
```json
{
  "errorKey": "STOCK_CONCURRENCY_CONFLICT",
  "params": {
    "productId": 123,
    "locationId": 456,
    "operationType": "OUT",
    "retryAttempts": 3
  },
  "timestamp": "2025-01-11T10:30:00+00:00",
  "path": "/api/inventory/adjust",
  "status": 409
}
```

**cURL Example**:
```bash
curl -X POST http://localhost:8080/api/inventory/adjust \
  -H "Content-Type: application/json" \
  -d '{
    "productId": 123,
    "locationId": 456,
    "transactionType": "OUT",
    "sourceType": "SALE_OUT",
    "quantity": 50,
    "sourceOrderId": "SO202501110001"
  }'
```

---

#### 1.2. Query Total Stock

**Endpoint**: `GET /api/inventory/total-stock/{productId}`

**Description**: Query total stock for a product (sum across all locations).

**Path Parameters**:

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `productId` | Long | ✅ Yes | Product ID |

**Success Response (200 OK)**:
```json
{
  "productId": 123,
  "totalStock": 350
}
```

**cURL Example**:
```bash
curl http://localhost:8080/api/inventory/total-stock/123
```

---

### 2. Stock Prediction APIs

#### 2.1. Get Reorder Suggestion (Single Product)

**Endpoint**: `GET /api/predictions/reorder/{productId}?days=30`

**Description**: Generate reorder suggestion for a single product based on historical outbound data.

**Path Parameters**:

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `productId` | Long | ✅ Yes | Product ID |

**Query Parameters**:

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `days` | Integer | No | 30 | Calculation period in days (7/14/30/60/90) |

**Success Response (200 OK)**:
```json
{
  "productId": 123,
  "barcode": "4901234567890",
  "productName": "Coca-Cola 500ml",
  "specification": "500ml",
  "unitPrice": 3.50,
  "supplier": "Coca-Cola Company",
  "currentStock": 50,
  "minStock": 100,
  "leadTime": 7,
  "dailyAverageOutbound": 30.5,
  "calculationPeriod": 30,
  "suggestedReorderQuantity": 314,
  "estimatedDaysUntilStockout": 1.64,
  "urgencyLevel": "CRITICAL",
  "estimatedCost": 1099.00,
  "needsReorder": true
}
```

**Response Fields**:

| Field | Type | Description |
|-------|------|-------------|
| `productId` | Long | Product ID |
| `barcode` | String | Product barcode |
| `productName` | String | Product name |
| `currentStock` | Integer | Current total stock (all locations) |
| `minStock` | Integer | Safety stock threshold |
| `leadTime` | Integer | Procurement lead time (days) |
| `dailyAverageOutbound` | Double | Daily average outbound (last N days) |
| `suggestedReorderQuantity` | Integer | Suggested reorder quantity |
| `estimatedDaysUntilStockout` | Double | Estimated days until stockout |
| `urgencyLevel` | String | Urgency level (CRITICAL/HIGH/MEDIUM/LOW) |
| `estimatedCost` | BigDecimal | Estimated reorder cost |
| `needsReorder` | Boolean | Whether reorder is needed |

**Urgency Levels**:

| Level | Condition | Priority |
|-------|-----------|----------|
| `CRITICAL` | Current stock < Safety stock | 1 (Highest) |
| `HIGH` | Estimated stockout days < Lead time | 2 |
| `MEDIUM` | Estimated stockout days ≈ Lead time (±3 days) | 3 |
| `LOW` | Stock sufficient | 4 (Lowest) |

**Prediction Formula**:
```
Daily Average Outbound = Total outbound in last N days / N days
Suggested Reorder Quantity = (Daily Average × Lead Time) + Safety Stock
Estimated Days Until Stockout = Current Stock / Daily Average
```

**Timezone Conversion**:
- Database stores UTC timestamps
- Calculations use **Europe/London timezone** for date grouping
- Ensures accurate daily statistics for UK business operations

**cURL Example**:
```bash
curl http://localhost:8080/api/predictions/reorder/123?days=30
```

---

#### 2.2. Get All Reorder Suggestions

**Endpoint**: `GET /api/predictions/reorder?days=30`

**Description**: Generate reorder suggestions for all low stock products (batch processing).

**Query Parameters**:

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `days` | Integer | No | 30 | Calculation period in days |

**Success Response (200 OK)**:
```json
{
  "suggestions": [
    {
      "productId": 123,
      "productName": "Coca-Cola 500ml",
      "urgencyLevel": "CRITICAL",
      "currentStock": 50,
      "suggestedReorderQuantity": 314,
      "estimatedCost": 1099.00,
      ...
    },
    {
      "productId": 456,
      "productName": "Pepsi 500ml",
      "urgencyLevel": "HIGH",
      "currentStock": 80,
      "suggestedReorderQuantity": 250,
      "estimatedCost": 875.00,
      ...
    }
  ],
  "totalCount": 2,
  "totalCost": 1974.00
}
```

**Sorting**:
- Primary: Urgency level (CRITICAL > HIGH > MEDIUM > LOW)
- Secondary: Estimated stockout days (ascending)

**cURL Example**:
```bash
curl http://localhost:8080/api/predictions/reorder?days=30
```

---

#### 2.3. Get Urgent Reorder Suggestions

**Endpoint**: `GET /api/predictions/reorder/urgent?days=30`

**Description**: Get high priority reorder suggestions (CRITICAL and HIGH only).

**Query Parameters**:

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `days` | Integer | No | 30 | Calculation period in days |

**Success Response (200 OK)**:
```json
{
  "suggestions": [
    {
      "productId": 123,
      "urgencyLevel": "CRITICAL",
      ...
    }
  ],
  "totalCount": 1,
  "totalCost": 1099.00
}
```

**cURL Example**:
```bash
curl http://localhost:8080/api/predictions/reorder/urgent?days=30
```

---

## 🔑 Error Keys Reference

| Error Key | HTTP Status | Description | Parameters |
|-----------|-------------|-------------|------------|
| `PRODUCT_NOT_FOUND` | 404 | Product does not exist | `productId` |
| `LOCATION_NOT_FOUND` | 404 | Location does not exist | `locationId` |
| `STOCK_INSUFFICIENT` | 400 | Outbound exceeds current stock | `productId`, `currentStock`, `requestedQuantity`, `shortage` |
| `STOCK_CONCURRENCY_CONFLICT` | 409 | Optimistic lock conflict (after 3 retries) | `productId`, `locationId`, `operationType`, `retryAttempts` |
| `VALIDATION_FAILED` | 400 | Input validation failed | Field errors (e.g., `productId`: "Product ID is required") |
| `INTERNAL_SERVER_ERROR` | 500 | Unexpected server error | `message`, `exceptionType` |

---

## 📊 Usage Examples

### Example 1: Purchase Inbound

```bash
curl -X POST http://localhost:8080/api/inventory/adjust \
  -H "Content-Type: application/json" \
  -d '{
    "productId": 123,
    "locationId": 456,
    "transactionType": "IN",
    "sourceType": "PURCHASE_IN",
    "quantity": 200,
    "sourceOrderId": "PO202501110001",
    "operatorId": 1,
    "operatorName": "John Doe",
    "remark": "Purchase from Supplier ABC"
  }'
```

### Example 2: Sale Outbound

```bash
curl -X POST http://localhost:8080/api/inventory/adjust \
  -H "Content-Type: application/json" \
  -d '{
    "productId": 123,
    "locationId": 456,
    "transactionType": "OUT",
    "sourceType": "SALE_OUT",
    "quantity": 50,
    "sourceOrderId": "SO202501110002",
    "operatorId": 2,
    "operatorName": "Jane Smith"
  }'
```

### Example 3: Query Reorder Suggestions

```bash
# Get all reorder suggestions (30 days)
curl http://localhost:8080/api/predictions/reorder?days=30

# Get urgent reorder suggestions only
curl http://localhost:8080/api/predictions/reorder/urgent?days=30

# Get suggestion for specific product (60 days)
curl http://localhost:8080/api/predictions/reorder/123?days=60
```

---

## 🧪 Frontend Integration Example

### JavaScript (Fetch API)

```javascript
// Adjust stock
async function adjustStock(request) {
  try {
    const response = await fetch('http://localhost:8080/api/inventory/adjust', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(request)
    });

    if (!response.ok) {
      const error = await response.json();
      // Handle error with i18n translation
      const errorMessage = translateErrorKey(error.errorKey, error.params);
      alert(errorMessage);
      return;
    }

    const transaction = await response.json();
    console.log('Stock adjusted successfully:', transaction);
  } catch (error) {
    console.error('Network error:', error);
  }
}

// i18n translation function
function translateErrorKey(errorKey, params) {
  const messages = {
    'STOCK_INSUFFICIENT': {
      en: `Insufficient stock: Current ${params.currentStock}, Requested ${params.requestedQuantity}, Shortage ${params.shortage}`,
      zh: `库存不足：当前 ${params.currentStock}，请求 ${params.requestedQuantity}，缺少 ${params.shortage}`
    },
    'PRODUCT_NOT_FOUND': {
      en: `Product not found: ID ${params.productId}`,
      zh: `商品不存在：ID ${params.productId}`
    }
  };

  const userLocale = navigator.language.startsWith('zh') ? 'zh' : 'en';
  return messages[errorKey]?.[userLocale] || errorKey;
}

// Get reorder suggestions
async function getReorderSuggestions() {
  const response = await fetch('http://localhost:8080/api/predictions/reorder?days=30');
  const data = await response.json();
  console.log(`Found ${data.totalCount} products needing reorder`);
  console.log(`Total cost: ${data.totalCost}`);
  data.suggestions.forEach(s => {
    console.log(`${s.productName}: ${s.urgencyLevel}, Reorder ${s.suggestedReorderQuantity}`);
  });
}
```

---

## 📚 Related Documentation

- `CLAUDE.md` - Project technical requirements
- `SERVICE_LAYER_v2.0.md` - Service layer implementation
- `ARCHITECTURE_UPGRADE_v2.0.md` - International architecture standards
- `ENTITY_MODELING.md` - Database design
- `REPOSITORY_LAYER.md` - Data access layer

---

**API Documentation**: ✅ **COMPLETED**
**Last Updated**: 2025-01-11
**Version**: 2.0 (Error Key System + Timezone Conversion)
