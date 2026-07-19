param(
    [string]$HostUrl = "http://127.0.0.1:8080"
)

$ErrorActionPreference = "Stop"
$RunId = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
$checks = New-Object System.Collections.Generic.List[object]
$resultPath = Join-Path $PSScriptRoot "v452-chaotic-storage-result.json"

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )

    $params = @{
        Method = $Method
        Uri = "$HostUrl$Path"
        Headers = $Headers
        UseBasicParsing = $true
        TimeoutSec = 30
    }
    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = $Body | ConvertTo-Json -Depth 30
    }

    try {
        $response = Invoke-WebRequest @params
        $parsed = if ([string]::IsNullOrWhiteSpace($response.Content)) { $null } else { $response.Content | ConvertFrom-Json }
        return [ordered]@{ status = [int]$response.StatusCode; body = $parsed }
    } catch {
        $status = 0
        $content = $_.ErrorDetails.Message
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            if ([string]::IsNullOrWhiteSpace($content)) {
                try {
                    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                    $content = $reader.ReadToEnd()
                } catch {
                    $content = $_.Exception.Message
                }
            }
        }
        $parsed = $content
        if (-not [string]::IsNullOrWhiteSpace($content)) {
            try { $parsed = $content | ConvertFrom-Json } catch {}
        }
        return [ordered]@{ status = $status; body = $parsed }
    }
}

function Assert-Check {
    param([string]$Name, [bool]$Condition, [object]$Details = $null)

    $checks.Add([ordered]@{ name = $Name; passed = $Condition; details = $Details })
    $mark = if ($Condition) { "PASS" } else { "FAIL" }
    Write-Host "[$mark] $Name"
    if (-not $Condition) {
        if ($null -ne $Details) { Write-Host ($Details | ConvertTo-Json -Depth 20 -Compress) }
        throw "Assertion failed: $Name"
    }
}

Write-Host "WMS V4.5.2 chaotic storage run: $RunId"

$health = Invoke-Api GET "/health/check"
Assert-Check "backend health is UP" ($health.status -eq 200) $health

$login = Invoke-Api POST "/api/auth/login" @{ username = "admin"; password = "password123" }
Assert-Check "admin login succeeds" ($login.status -eq 200 -and $login.body.token) $login
$auth = @{ Authorization = "Bearer $($login.body.token)" }

$root = Invoke-Api POST "/api/categories" @{
    categoryCode = "V452R$RunId"
    categoryName = "V4.5.2 Root $RunId"
    sortOrder = 400
} $auth
Assert-Check "create root category" ($root.status -eq 201) $root

$leaf = Invoke-Api POST "/api/categories" @{
    categoryCode = "V452L$RunId"
    categoryName = "V4.5.2 Leaf $RunId"
    parentId = $root.body.id
    sortOrder = 400
} $auth
Assert-Check "create leaf category" ($leaf.status -eq 201) $leaf

$warehouse = Invoke-Api POST "/api/warehouses" @{
    code = "C$RunId"
    name = "V4.5.2 Chaotic Warehouse"
    address = "regression"
    contact = "Codex"
    phone = "13800138045"
} $auth
Assert-Check "create warehouse" ($warehouse.status -eq 201) $warehouse

$fragmentLocation = Invoke-Api POST "/api/locations" @{
    warehouseId = $warehouse.body.id
    zone = "ZONE_A"
    shelfNumber = "A"
    positionNumber = "01"
    remark = "fragment-first"
} $auth
Assert-Check "create fragment location" ($fragmentLocation.status -eq 201) $fragmentLocation

$bulkLocation = Invoke-Api POST "/api/locations" @{
    warehouseId = $warehouse.body.id
    zone = "ZONE_B"
    shelfNumber = "B"
    positionNumber = "01"
    remark = "bulk"
} $auth
Assert-Check "create bulk location" ($bulkLocation.status -eq 201) $bulkLocation

function New-ProductSku {
    param([string]$Prefix, [string]$Name)

    $product = Invoke-Api POST "/api/products" @{
        productCode = "$Prefix-P-$RunId"
        productName = $Name
        categoryId = $leaf.body.id
        brand = "Codex"
        enabled = $true
    } $auth
    Assert-Check "create Product $Prefix" ($product.status -eq 201) $product

    $sku = Invoke-Api POST "/api/product-skus" @{
        productId = $product.body.id
        barcode = "$Prefix-B-$RunId"
        name = $Name
        skuName = "$Prefix-SKU"
        specs = "V4.5.2 regression"
        unitPrice = 10.00
        minSalesPrice = 1.00
        perPackQty = 1
        conversionRate = 1
        packUnit = "box"
        nearExpiryDays = 30
        batchTrackingMode = "LOCATION_VISUAL"
        enabled = $true
    } $auth
    Assert-Check "create ProductSku $Prefix" ($sku.status -eq 201 -and $sku.body.batchTrackingMode -eq "LOCATION_VISUAL") $sku
    return $sku.body
}

$visualSku = New-ProductSku "VIS" "V4.5.2 Visual Product"
$mixingSku = New-ProductSku "MIX" "V4.5.2 Mixing Product"

$customer = Invoke-Api POST "/api/customers" @{
    code = "CV$RunId"
    name = "V4.5.2 Customer"
    contact = "Codex"
    phone = "13800138045"
    email = "v452-$RunId@example.com"
    address = "regression"
    creditLimit = 100000.00
    isActive = $true
} $auth
Assert-Check "create customer" ($customer.status -eq 201) $customer

function Complete-Inbound {
    param(
        [long]$ProductSkuId,
        [object[]]$Lines,
        [string]$Name,
        [bool]$ExpectReceiveSuccess = $true
    )

    $items = @()
    foreach ($line in $Lines) {
        $items += @{
            productSkuId = $ProductSkuId
            planQty = $line.quantity
            unitCost = 5.00
            targetWarehouseId = $warehouse.body.id
            targetLocationId = $line.locationId
            remark = $Name
        }
    }
    $order = Invoke-Api POST "/api/inbound-orders" @{
        supplierId = 1
        expectedDate = "2026-12-01"
        remark = $Name
        items = $items
    } $auth
    Assert-Check "$Name draft" ($order.status -eq 201) $order

    $approve = Invoke-Api POST "/api/inbound-orders/$($order.body.id)/approve-plan" @{ comment = "$Name approve" } $auth
    Assert-Check "$Name approve" ($approve.status -eq 200) $approve

    $confirmations = @()
    $receipts = @()
    for ($index = 0; $index -lt $Lines.Count; $index++) {
        $item = $order.body.items[$index]
        $line = $Lines[$index]
        $confirmations += @{
            itemId = $item.id
            confirmedQty = $line.quantity
            expiryDate = "2027-12-31"
            productionDate = "2026-06-01"
            targetWarehouseId = $warehouse.body.id
            targetLocationId = $line.locationId
        }
        $receipts += @{
            itemId = $item.id
            actualQty = $line.quantity
            locationId = $line.locationId
        }
    }
    $confirm = Invoke-Api POST "/api/inbound-orders/$($order.body.id)/confirm-order" @{
        comment = "$Name confirm"
        confirmations = $confirmations
    } $auth
    Assert-Check "$Name confirm" ($confirm.status -eq 200) $confirm

    $receive = Invoke-Api POST "/api/inbound-orders/$($order.body.id)/receive-goods" @{ receipts = $receipts } $auth
    if ($ExpectReceiveSuccess) {
        Assert-Check "$Name receive" ($receive.status -eq 200) $receive
    } else {
        Assert-Check "anti-mixing receive is blocked" ($receive.status -eq 409 -and $receive.body.errorKey -eq "LOCATION_BATCH_MIXING_FORBIDDEN") $receive
    }
}

Complete-Inbound $visualSku.id @(
    @{ quantity = 3; locationId = $fragmentLocation.body.id },
    @{ quantity = 10; locationId = $bulkLocation.body.id }
) "fragment setup"

$fragmentAfterInbound = Invoke-Api GET "/api/locations/$($fragmentLocation.body.id)" $null $auth
Assert-Check "fragment location becomes OCCUPIED" ($fragmentAfterInbound.status -eq 200 -and $fragmentAfterInbound.body.status -eq "OCCUPIED") $fragmentAfterInbound

Complete-Inbound $mixingSku.id @(
    @{ quantity = 1; locationId = $fragmentLocation.body.id }
) "mixing attack" $false

$salesOrder = Invoke-Api POST "/api/sales-orders" @{
    customerId = $customer.body.id
    items = @(@{
        productSkuId = $visualSku.id
        quantity = 3
        unitPrice = 10.00
        rejectNearExpiry = $false
        remark = "fragment-first"
    })
} $auth
Assert-Check "create fragment-first sales order" ($salesOrder.status -eq 201) $salesOrder

$tasks = Invoke-Api GET "/api/outbound-tasks?salesOrderId=$($salesOrder.body.id)" $null $auth
$taskArray = @($tasks.body)
Assert-Check "allocation creates one fragment task" ($tasks.status -eq 200 -and $taskArray.Count -eq 1 -and $taskArray[0].locationId -eq $fragmentLocation.body.id) $tasks

$confirmTask = Invoke-Api POST "/api/outbound-tasks/$($taskArray[0].id)/confirm" @{
    actualQty = 3
    locationId = $fragmentLocation.body.id
    skuCode = $visualSku.skuCode
} $auth
Assert-Check "LOCATION_VISUAL confirm succeeds without batchCode" ($confirmTask.status -eq 200 -and $confirmTask.body.status -eq "COMPLETED") $confirmTask

$fragmentFinal = Invoke-Api GET "/api/locations/$($fragmentLocation.body.id)" $null $auth
Assert-Check "empty fragment location self-heals to EMPTY" ($fragmentFinal.status -eq 200 -and $fragmentFinal.body.status -eq "EMPTY") $fragmentFinal

$bulkFinal = Invoke-Api GET "/api/locations/$($bulkLocation.body.id)" $null $auth
Assert-Check "bulk location remains OCCUPIED" ($bulkFinal.status -eq 200 -and $bulkFinal.body.status -eq "OCCUPIED") $bulkFinal

$stock = Invoke-Api GET "/api/inventory/total-stock/$($visualSku.id)" $null $auth
Assert-Check "fragment-first shipment leaves ten units" ($stock.status -eq 200 -and [int]$stock.body.totalStock -eq 10) $stock

$summary = [ordered]@{
    passed = $true
    runId = $RunId
    generatedAt = [DateTimeOffset]::Now.ToString("o")
    checks = $checks
}
$summary | ConvertTo-Json -Depth 30 | Set-Content -Path $resultPath -Encoding UTF8
Write-Host "Result written to $resultPath"
