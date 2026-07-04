$ErrorActionPreference = "Stop"

$HostUrl = "http://127.0.0.1:8080"
$RunId = [DateTimeOffset]::Now.ToUnixTimeSeconds()
$ResultPath = Join-Path $PSScriptRoot "v45-hardening-result.json"

$checks = New-Object System.Collections.Generic.List[object]
$artifacts = [ordered]@{}

function Add-Check {
    param(
        [string]$Scenario,
        [string]$Name,
        [bool]$Passed,
        [object]$Details = $null
    )
    $checks.Add([ordered]@{
        scenario = $Scenario
        name = $Name
        passed = $Passed
        details = $Details
    })
    $mark = if ($Passed) { "PASS" } else { "FAIL" }
    Write-Host "[$mark][$Scenario] $Name"
    if (-not $Passed -and $null -ne $Details) {
        Write-Host "  $($Details | ConvertTo-Json -Depth 8 -Compress)"
    }
}

function Assert-Check {
    param(
        [string]$Scenario,
        [string]$Name,
        [bool]$Condition,
        [object]$Details = $null
    )
    Add-Check -Scenario $Scenario -Name $Name -Passed $Condition -Details $Details
    if (-not $Condition) {
        throw "Assertion failed: [$Scenario] $Name"
    }
}

function Convert-JsonBody {
    param([object]$Content)
    if ([string]::IsNullOrWhiteSpace($Content)) { return $null }
    try { return $Content | ConvertFrom-Json } catch { return $Content }
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )
    $uri = "$HostUrl$Path"
    $params = @{
        Method = $Method
        Uri = $uri
        Headers = $Headers
        UseBasicParsing = $true
        TimeoutSec = 30
    }
    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 30)
    }
    try {
        $response = Invoke-WebRequest @params
        return [ordered]@{
            status = [int]$response.StatusCode
            body = Convert-JsonBody $response.Content
            raw = $response.Content
        }
    } catch {
        $status = 0
        $content = $null
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $content = $reader.ReadToEnd()
            } catch {
                $content = $_.Exception.Message
            }
        } else {
            $content = $_.Exception.Message
        }
        return [ordered]@{
            status = $status
            body = Convert-JsonBody $content
            raw = $content
        }
    }
}

function New-Warehouse {
    param([string]$Prefix)
    $code = ($Prefix + $RunId).ToUpper()
    if ($code.Length -gt 20) { $code = $code.Substring(0, 20) }
    $r = Invoke-Api POST "/api/warehouses" @{
        code = $code
        name = "$Prefix Hardening Warehouse"
        address = "hardening"
        contact = "Codex"
        phone = "13800138045"
    } $Auth
    Assert-Check "setup" "create warehouse $Prefix" ($r.status -eq 201) @{ status = $r.status; body = $r.body }
    return $r.body
}

function New-Location {
    param([long]$WarehouseId, [string]$Zone, [string]$Shelf, [string]$Position)
    $r = Invoke-Api POST "/api/locations" @{
        warehouseId = $WarehouseId
        zone = $Zone
        shelfNumber = $Shelf
        positionNumber = $Position
        remark = "hardening"
    } $Auth
    Assert-Check "setup" "create location $Zone-$Shelf-$Position" ($r.status -eq 201) @{ status = $r.status; body = $r.body }
    return $r.body
}

function New-Product {
    param(
        [string]$Prefix,
        [decimal]$MinSalesPrice = 1.00,
        [string]$BatchTrackingMode = "PRINTED_LABEL",
        [string]$BarcodeOverride = $null
    )
    $barcode = if ($BarcodeOverride) { $BarcodeOverride } else { "$Prefix-$RunId" }
    $r = Invoke-Api POST "/api/products" @{
        barcode = $barcode
        name = "$Prefix Hardening Product"
        skuName = "$Prefix-SKU"
        spuId = 1
        specs = "hardening"
        unitPrice = 10.00
        minSalesPrice = $MinSalesPrice
        perPackQty = 1
        conversionRate = 1
        packUnit = "box"
        nearExpiryDays = 30
        category = "hardening"
        batchTrackingMode = $BatchTrackingMode
        enabled = $true
    } $Auth
    return $r
}

function New-Customer {
    param([string]$Prefix)
    $r = Invoke-Api POST "/api/customers" @{
        code = "CUST-$Prefix-$RunId"
        name = "$Prefix Hardening Customer"
        contact = "Codex"
        phone = "13800138045"
        email = "$Prefix$RunId@example.com"
        address = "hardening"
        creditLimit = 100000.00
        isActive = $true
    } $Auth
    Assert-Check "setup" "create customer $Prefix" ($r.status -eq 201) @{ status = $r.status; body = $r.body }
    return $r.body
}

function Add-Stock {
    param(
        [long]$ProductId,
        [long]$WarehouseId,
        [long]$LocationId,
        [int]$Quantity,
        [string]$Scenario
    )
    $inbound = Invoke-Api POST "/api/inbound-orders" @{
        supplierId = 1
        expectedDate = "2026-07-05"
        remark = "$Scenario stock setup"
        items = @(@{
            productId = $ProductId
            planQty = $Quantity
            unitCost = 5.00
            targetWarehouseId = $WarehouseId
            targetLocationId = $LocationId
            remark = "$Scenario stock"
        })
    } $Auth
    Assert-Check $Scenario "create inbound stock order" ($inbound.status -eq 201) @{ status = $inbound.status; body = $inbound.body }
    $orderId = [long]$inbound.body.id
    $itemId = [long]$inbound.body.items[0].id

    $approve = Invoke-Api POST "/api/inbound-orders/$orderId/approve-plan" @{ comment = "$Scenario approve" } $Auth
    Assert-Check $Scenario "approve inbound stock order" ($approve.status -eq 200) @{ status = $approve.status; body = $approve.body }

    $confirm = Invoke-Api POST "/api/inbound-orders/$orderId/confirm-order" @{
        comment = "$Scenario confirm"
        confirmations = @(@{
            itemId = $itemId
            confirmedQty = $Quantity
            expiryDate = "2027-12-31"
            productionDate = "2026-06-01"
            targetWarehouseId = $WarehouseId
            targetLocationId = $LocationId
        })
    } $Auth
    Assert-Check $Scenario "confirm inbound stock order" ($confirm.status -eq 200) @{ status = $confirm.status; body = $confirm.body }

    $receive = Invoke-Api POST "/api/inbound-orders/$orderId/receive-goods" @{
        receipts = @(@{
            itemId = $itemId
            actualQty = $Quantity
            locationId = $LocationId
        })
    } $Auth
    Assert-Check $Scenario "receive inbound stock order" ($receive.status -eq 200) @{ status = $receive.status; body = $receive.body }
}

function New-SalesOrder {
    param(
        [long]$CustomerId,
        [long]$ProductId,
        [int]$Quantity,
        [decimal]$UnitPrice,
        [string]$Scenario
    )
    $r = Invoke-Api POST "/api/sales-orders" @{
        customerId = $CustomerId
        items = @(@{
            productId = $ProductId
            quantity = $Quantity
            unitPrice = $UnitPrice
            rejectNearExpiry = $false
            remark = "$Scenario sales order"
        })
    } $Auth
    Assert-Check $Scenario "create sales order qty=$Quantity" ($r.status -eq 201) @{ status = $r.status; body = $r.body }
    return $r.body
}

function Sum-OpenReservations {
    param([long[]]$OrderIds)
    $sum = 0
    foreach ($orderId in $OrderIds) {
        $r = Invoke-Api GET "/api/inventory/reservations?salesOrderId=$orderId" $null $Auth
        if ($r.status -eq 200 -and $null -ne $r.body) {
            foreach ($item in @($r.body)) {
                if ($item.status -notin @("RELEASED", "EXPIRED")) {
                    $sum += [int]$item.openQty
                }
            }
        }
    }
    return $sum
}

function Sum-OutboundPlan {
    param([long[]]$OrderIds)
    $sum = 0
    foreach ($orderId in $OrderIds) {
        $r = Invoke-Api GET "/api/outbound-tasks?salesOrderId=$orderId" $null $Auth
        if ($r.status -eq 200 -and $null -ne $r.body) {
            foreach ($task in @($r.body)) {
                if ($task.status -ne "CANCELLED") {
                    $sum += [int]$task.planQty
                }
            }
        }
    }
    return $sum
}

function Invoke-ApproveJob {
    param([long]$OrderId)
    Start-Job -ScriptBlock {
        param($HostUrl, $Token, $OrderId)
        $headers = @{ Authorization = "Bearer $Token" }
        $body = @{ comment = "concurrency approve"; allocationPolicy = "FULL_ONLY" } | ConvertTo-Json
        try {
            $r = Invoke-WebRequest -Method POST -Uri "$HostUrl/api/sales-orders/$OrderId/approve" -Headers $headers -ContentType "application/json" -Body $body -UseBasicParsing -TimeoutSec 30
            [ordered]@{ orderId = $OrderId; status = [int]$r.StatusCode; raw = $r.Content }
        } catch {
            $status = 0
            $content = $_.Exception.Message
            if ($_.Exception.Response) {
                $status = [int]$_.Exception.Response.StatusCode
                try {
                    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                    $content = $reader.ReadToEnd()
                } catch {}
            }
            [ordered]@{ orderId = $OrderId; status = $status; raw = $content }
        }
    } -ArgumentList $HostUrl, $Token, $OrderId
}

Write-Host "WMS V4.5 hardening run: $RunId"

$health = Invoke-Api GET "/health/check"
Assert-Check "preflight" "backend health is UP" ($health.status -eq 200) @{ status = $health.status; body = $health.body }

$login = Invoke-Api POST "/api/auth/login" @{ username = "admin"; password = "password123" }
Assert-Check "preflight" "admin login succeeds" ($login.status -eq 200 -and $login.body.token) @{ status = $login.status; body = $login.body }
$Token = $login.body.token
$Auth = @{ Authorization = "Bearer $Token" }

# 1. Concurrency oversell hardening
$scenario = "1-concurrency"
$warehouse = New-Warehouse "HC"
$location = New-Location $warehouse.id "ZONE_A" "A" "01"
$productResp = New-Product "HC-PROD" 10.00
Assert-Check $scenario "create concurrency product" ($productResp.status -eq 201) @{ status = $productResp.status; body = $productResp.body }
$product = $productResp.body
$customer = New-Customer "HC"
Add-Stock $product.id $warehouse.id $location.id 100 $scenario
$orderA = New-SalesOrder $customer.id $product.id 100 1.00 $scenario
$orderB = New-SalesOrder $customer.id $product.id 100 1.00 $scenario
Assert-Check $scenario "orders wait for approval before allocation" ($orderA.status -eq "PENDING_APPROVAL" -and $orderB.status -eq "PENDING_APPROVAL") @{ orderA = $orderA.status; orderB = $orderB.status }

$jobA = Invoke-ApproveJob $orderA.id
$jobB = Invoke-ApproveJob $orderB.id
$jobs = @($jobA, $jobB)
Wait-Job -Job $jobs | Out-Null
$approveResults = @($jobs | Receive-Job)
Remove-Job -Job $jobs
$artifacts.concurrencyApproveResults = $approveResults

$approvedCount = @($approveResults | Where-Object { $_.status -eq 200 }).Count
Assert-Check $scenario "at least one concurrent approval completes or is safely handled" ($approvedCount -ge 1) $approveResults
$unsafeConcurrencyResponses = @($approveResults | Where-Object { $_.status -notin @(200,400,409) })
Assert-Check $scenario "concurrent approval never leaks internal 500" ($unsafeConcurrencyResponses.Count -eq 0) $approveResults
$openReserved = Sum-OpenReservations @([long]$orderA.id, [long]$orderB.id)
$outboundPlan = Sum-OutboundPlan @([long]$orderA.id, [long]$orderB.id)
$stock = Invoke-Api GET "/api/inventory/total-stock/$($product.id)" $null $Auth
Assert-Check $scenario "open reservation never exceeds on-hand stock" ($openReserved -le 100) @{ openReserved = $openReserved }
Assert-Check $scenario "outbound plan never exceeds on-hand stock" ($outboundPlan -le 100) @{ outboundPlan = $outboundPlan }
Assert-Check $scenario "available stock is not negative" ($stock.status -eq 200 -and [int]$stock.body.availableStock -ge 0) @{ status = $stock.status; body = $stock.body }

# 2. Idempotency and replay hardening
$scenario = "2-idempotency"
$warehouse2 = New-Warehouse "HI"
$location2 = New-Location $warehouse2.id "ZONE_A" "B" "01"
$product2Resp = New-Product "HI-PROD" 10.00
Assert-Check $scenario "create idempotency product" ($product2Resp.status -eq 201) @{ status = $product2Resp.status; body = $product2Resp.body }
$product2 = $product2Resp.body
$customer2 = New-Customer "HI"
Add-Stock $product2.id $warehouse2.id $location2.id 5 $scenario
$idemOrder = New-SalesOrder $customer2.id $product2.id 2 1.00 $scenario
$approve1 = Invoke-Api POST "/api/sales-orders/$($idemOrder.id)/approve" @{ comment = "first approve"; allocationPolicy = "FULL_ONLY" } $Auth
Assert-Check $scenario "first approval succeeds" ($approve1.status -eq 200) @{ status = $approve1.status; body = $approve1.body }
$approve2 = Invoke-Api POST "/api/sales-orders/$($idemOrder.id)/approve" @{ comment = "replay approve"; allocationPolicy = "FULL_ONLY" } $Auth
Assert-Check $scenario "duplicate approval is rejected" ($approve2.status -in @(400,409)) @{ status = $approve2.status; body = $approve2.body }
$taskList = Invoke-Api GET "/api/outbound-tasks?salesOrderId=$($idemOrder.id)" $null $Auth
Assert-Check $scenario "approval generated outbound task" ($taskList.status -eq 200 -and @($taskList.body).Count -ge 1) @{ status = $taskList.status; body = $taskList.body }
$task = @($taskList.body)[0]
$confirm1 = Invoke-Api POST "/api/outbound-tasks/$($task.id)/confirm" @{ actualQty = $task.planQty } $Auth
Assert-Check $scenario "first outbound confirm succeeds" ($confirm1.status -eq 200 -and $confirm1.body.status -eq "COMPLETED") @{ status = $confirm1.status; body = $confirm1.body }
$stockAfterFirst = Invoke-Api GET "/api/inventory/total-stock/$($product2.id)" $null $Auth
$confirm2 = Invoke-Api POST "/api/outbound-tasks/$($task.id)/confirm" @{ actualQty = $task.planQty } $Auth
$stockAfterReplay = Invoke-Api GET "/api/inventory/total-stock/$($product2.id)" $null $Auth
Assert-Check $scenario "duplicate outbound confirm is rejected" ($confirm2.status -in @(400,409)) @{ status = $confirm2.status; body = $confirm2.body }
Assert-Check $scenario "duplicate outbound confirm does not deduct stock twice" ([int]$stockAfterReplay.body.totalStock -eq [int]$stockAfterFirst.body.totalStock) @{ afterFirst = $stockAfterFirst.body; afterReplay = $stockAfterReplay.body }

# 3. Security hardening
$scenario = "3-security"
$noToken = Invoke-Api POST "/api/warehouses" @{
    code = "NOAUTH$RunId"
    name = "No Auth"
    address = "hardening"
    contact = "Codex"
    phone = "13800138045"
}
Assert-Check $scenario "missing token is rejected" ($noToken.status -in @(401,403)) @{ status = $noToken.status; body = $noToken.body }
$fakeToken = Invoke-Api POST "/api/warehouses" @{
    code = "FAKE$RunId"
    name = "Fake Token"
    address = "hardening"
    contact = "Codex"
    phone = "13800138045"
} @{ Authorization = "Bearer fake.invalid.token" }
Assert-Check $scenario "fake token is rejected" ($fakeToken.status -in @(401,403)) @{ status = $fakeToken.status; body = $fakeToken.body }

# 4. SaaS tenant/data isolation placeholder hardening
$scenario = "4-data-isolation"
$dupBarcode = "HD-DUP-$RunId"
$dup1 = New-Product "HD-DUP" 1.00 "PRINTED_LABEL" $dupBarcode
Assert-Check $scenario "first product with unique barcode succeeds" ($dup1.status -eq 201) @{ status = $dup1.status; body = $dup1.body }
$dup2 = New-Product "HD-DUP" 1.00 "PRINTED_LABEL" $dupBarcode
Assert-Check $scenario "same-company duplicate barcode is rejected" ($dup2.status -in @(400,409)) @{ status = $dup2.status; body = $dup2.body }
$dupWhCode = "HDW$RunId"
if ($dupWhCode.Length -gt 20) { $dupWhCode = $dupWhCode.Substring(0, 20) }
$dupWh1 = Invoke-Api POST "/api/warehouses" @{
    code = $dupWhCode
    name = "Duplicate Warehouse A"
    address = "hardening"
    contact = "Codex"
    phone = "13800138045"
} $Auth
Assert-Check $scenario "first warehouse with unique code succeeds" ($dupWh1.status -eq 201) @{ status = $dupWh1.status; body = $dupWh1.body }
$dupWh2 = Invoke-Api POST "/api/warehouses" @{
    code = $dupWhCode
    name = "Duplicate Warehouse B"
    address = "hardening"
    contact = "Codex"
    phone = "13800138045"
} $Auth
Assert-Check $scenario "same-company duplicate warehouse code is rejected" ($dupWh2.status -in @(400,409)) @{ status = $dupWh2.status; body = $dupWh2.body }

# 5. Boundary and dirty input hardening
$scenario = "5-boundary"
$badQty = Invoke-Api POST "/api/sales-orders" @{
    customerId = $customer.id
    items = @(@{
        productId = $product.id
        quantity = 0
        unitPrice = 1.00
        rejectNearExpiry = $false
    })
} $Auth
Assert-Check $scenario "zero sales quantity is rejected" ($badQty.status -eq 400) @{ status = $badQty.status; body = $badQty.body }
$missingCustomer = Invoke-Api POST "/api/sales-orders" @{
    items = @(@{
        productId = $product.id
        quantity = 1
        unitPrice = 1.00
        rejectNearExpiry = $false
    })
} $Auth
Assert-Check $scenario "missing customer is rejected" ($missingCustomer.status -eq 400) @{ status = $missingCustomer.status; body = $missingCustomer.body }
$missingProduct = Invoke-Api POST "/api/sales-orders" @{
    customerId = $customer.id
    items = @(@{
        productId = 999999999
        quantity = 1
        unitPrice = 1.00
        rejectNearExpiry = $false
    })
} $Auth
Assert-Check $scenario "non-existent product is rejected" ($missingProduct.status -in @(400,404)) @{ status = $missingProduct.status; body = $missingProduct.body }
$badAdjust = Invoke-Api POST "/api/inventory/adjust" @{
    productId = $product.id
    locationId = $location.id
    transactionType = "ADJUST"
    sourceType = "MANUAL_ADJUST"
    quantity = 0
    sourceOrderId = "HARDEN-BAD-ADJ-$RunId"
    operatorId = 2
    operatorName = "admin"
    remark = "bad boundary"
} $Auth
Assert-Check $scenario "zero stock adjustment quantity is rejected" ($badAdjust.status -eq 400) @{ status = $badAdjust.status; body = $badAdjust.body }

$passed = -not ($checks | Where-Object { -not $_.passed })
$summary = [ordered]@{
    passed = $passed
    generatedAt = [DateTimeOffset]::Now.ToString("o")
    runId = $RunId
    artifacts = $artifacts
    checks = $checks
}
$summary | ConvertTo-Json -Depth 30 | Set-Content -Path $ResultPath -Encoding UTF8
Write-Host "Result written to $ResultPath"
if (-not $passed) {
    exit 1
}
