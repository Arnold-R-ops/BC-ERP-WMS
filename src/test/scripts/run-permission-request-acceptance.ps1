param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$AdminUsername = "admin",
    [string]$AdminPassword = $env:ADMIN_ACCEPTANCE_PASSWORD
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($AdminPassword)) {
    # Local development fallback documented in docs/frontend/03_LOCAL_DEV.md.
    $AdminPassword = "password123"
}

$script:checks = 0
$script:targetUserId = $null
$script:reviewerUserId = $null
$script:adminToken = $null

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw "ASSERTION_FAILED: $Message"
    }
    $script:checks++
    Write-Output "PASS $Message"
}

function Invoke-Api {
    param(
        [ValidateSet("GET", "POST", "PUT", "DELETE")]
        [string]$Method,
        [string]$Path,
        [object]$Body,
        [string]$Token,
        [int[]]$ExpectedStatus = @(200)
    )

    $parameters = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        ContentType = "application/json"
        UseBasicParsing = $true
    }
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $parameters.Headers = @{ Authorization = "Bearer $Token" }
    }
    if ($null -ne $Body) {
        $parameters.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }

    # PowerShell 7 returns HttpResponseMessage and can keep non-2xx bodies
    # available with -SkipHttpErrorCheck. Windows PowerShell 5 uses the
    # WebException response stream instead, so retain that fallback.
    $supportsSkipHttpErrorCheck = (Get-Command Invoke-WebRequest).Parameters.ContainsKey("SkipHttpErrorCheck")
    if ($supportsSkipHttpErrorCheck) {
        $parameters.SkipHttpErrorCheck = $true
    }

    try {
        $response = Invoke-WebRequest @parameters
        $status = [int]$response.StatusCode
        $content = if ([string]::IsNullOrWhiteSpace($response.Content)) {
            $null
        } else {
            $response.Content | ConvertFrom-Json
        }
    } catch {
        if ($supportsSkipHttpErrorCheck -or $null -eq $_.Exception.Response) {
            throw
        }
        $response = $_.Exception.Response
        $status = [int]$response.StatusCode
        $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
        try {
            $raw = $reader.ReadToEnd()
        } finally {
            $reader.Dispose()
        }
        $content = if ([string]::IsNullOrWhiteSpace($raw)) { $null } else { $raw | ConvertFrom-Json }
    }

    if ($ExpectedStatus -notcontains $status) {
        $errorKey = if ($null -ne $content -and $null -ne $content.errorKey) { $content.errorKey } else { "none" }
        throw "Unexpected HTTP status for $Method $Path`: got=$status expected=$($ExpectedStatus -join ',') errorKey=$errorKey"
    }

    return [pscustomobject]@{
        Status = $status
        Body = $content
    }
}

function Login {
    param([string]$Username, [string]$Password)
    return (Invoke-Api -Method POST -Path "/api/auth/login" -Body @{
        username = $Username
        password = $Password
    }).Body
}

$suffix = Get-Date -Format "yyyyMMddHHmmss"
$targetUsername = "uat_perm_$suffix"
$targetPassword = "Uat-Permission-$suffix!"
$reviewerUsername = "uat_perm_reviewer_$suffix"
$reviewerPassword = "Uat-Reviewer-$suffix!"

try {
    $adminLogin = Login -Username $AdminUsername -Password $AdminPassword
    Assert-True ($adminLogin.currentRole -eq "TENANT_ADMIN") "administrator logs in as TENANT_ADMIN"
    $script:adminToken = $adminLogin.token

    $roles = (Invoke-Api -Method GET -Path "/api/roles" -Token $script:adminToken).Body
    $generalRole = $roles | Where-Object { $_.roleCode -eq "GENERAL_MANAGER" } | Select-Object -First 1
    $salesRole = $roles | Where-Object { $_.roleCode -eq "SALESPERSON" } | Select-Object -First 1
    $warehouseAdminRole = $roles | Where-Object { $_.roleCode -eq "WAREHOUSE_ADMIN" } | Select-Object -First 1
    $superAdminRole = $roles | Where-Object { $_.roleCode -eq "TENANT_ADMIN" } | Select-Object -First 1
    Assert-True ($null -ne $generalRole -and $null -ne $salesRole -and $null -ne $warehouseAdminRole -and $null -ne $superAdminRole) "required role fixtures are available"

    $users = (Invoke-Api -Method GET -Path "/api/users" -Token $script:adminToken).Body
    $adminUser = $users | Where-Object { $_.username -eq $AdminUsername } | Select-Object -First 1
    Assert-True ($null -ne $adminUser) "administrator identity is resolvable"

    $reviewerUser = (Invoke-Api -Method POST -Path "/api/users" -Token $script:adminToken -ExpectedStatus @(201) -Body @{
        username = $reviewerUsername
        password = $reviewerPassword
        displayName = "Permission Request High-Risk Reviewer"
        roleIds = @([long]$superAdminRole.id)
        enabled = $true
        remark = "Temporary second-reviewer real-API acceptance user"
    }).Body
    $script:reviewerUserId = [long]$reviewerUser.id
    $reviewerLogin = Login -Username $reviewerUsername -Password $reviewerPassword
    $reviewerToken = $reviewerLogin.token
    Assert-True ($reviewerLogin.currentRole -eq "TENANT_ADMIN") "independent high-risk reviewer logs in as TENANT_ADMIN"

    $createdUser = (Invoke-Api -Method POST -Path "/api/users" -Token $script:adminToken -ExpectedStatus @(201) -Body @{
        username = $targetUsername
        password = $targetPassword
        displayName = "Permission Request API Acceptance"
        roleIds = @([long]$generalRole.id)
        enabled = $true
        remark = "Temporary real-API permission request acceptance user"
    }).Body
    $script:targetUserId = [long]$createdUser.id
    Assert-True ($createdUser.defaultRoleCode -eq "GENERAL_MANAGER" -and $createdUser.roleCodes.Count -eq 1) "target starts with one baseline role"

    $targetBeforeApproval = Login -Username $targetUsername -Password $targetPassword
    $targetTokenBeforeApproval = $targetBeforeApproval.token
    $overreach = Invoke-Api -Method GET -Path "/api/permission-requests?page=0&size=20" -Token $targetTokenBeforeApproval -ExpectedStatus @(403)
    Assert-True ($overreach.Status -eq 403) "ordinary user cannot access IAM permission requests"

    $selfGrant = Invoke-Api -Method POST -Path "/api/permission-requests" -Token $script:adminToken -ExpectedStatus @(403) -Body @{
        targetUserId = [long]$adminUser.id
        requestedRoleId = [long]$salesRole.id
        requestReason = "negative self-grant acceptance"
    }
    Assert-True ($selfGrant.Status -eq 403) "request submitter cannot grant a role to self"

    $protectedRole = Invoke-Api -Method POST -Path "/api/permission-requests" -Token $script:adminToken -ExpectedStatus @(400) -Body @{
        targetUserId = $script:targetUserId
        requestedRoleId = [long]$superAdminRole.id
        requestReason = "negative protected-role acceptance"
    }
    Assert-True ($protectedRole.Status -eq 400) "protected privileged role cannot be requested"

    $normalRequest = (Invoke-Api -Method POST -Path "/api/permission-requests" -Token $script:adminToken -ExpectedStatus @(201) -Body @{
        targetUserId = $script:targetUserId
        requestedRoleId = [long]$salesRole.id
        requestReason = "normal-risk real API acceptance"
    }).Body
    Assert-True ($normalRequest.status -eq "PENDING_REVIEW" -and [int]$normalRequest.highRiskPermissionCount -eq 0) "normal-risk request is submitted without granting the role"

    $stillBaseline = (Invoke-Api -Method GET -Path "/api/users" -Token $script:adminToken).Body |
        Where-Object { [long]$_.id -eq $script:targetUserId } | Select-Object -First 1
    Assert-True ($stillBaseline.roleCodes -notcontains "SALESPERSON") "submission alone does not grant permission"

    $duplicate = Invoke-Api -Method POST -Path "/api/permission-requests" -Token $script:adminToken -ExpectedStatus @(409) -Body @{
        targetUserId = $script:targetUserId
        requestedRoleId = [long]$salesRole.id
        requestReason = "duplicate negative acceptance"
    }
    Assert-True ($duplicate.Status -eq 409) "duplicate pending target-role request is rejected"

    $approved = (Invoke-Api -Method POST -Path "/api/permission-requests/$($normalRequest.id)/review" -Token $script:adminToken -Body @{
        approved = $true
        comment = "normal-risk acceptance approved"
    }).Body
    Assert-True ($approved.status -eq "APPROVED") "normal-risk request can be approved by the authorized submitter"

    $oldTokenAfterGrant = Invoke-Api -Method POST -Path "/api/auth/switch-role" -Token $targetTokenBeforeApproval -ExpectedStatus @(401, 403) -Body @{
        targetRoleCode = "GENERAL_MANAGER"
    }
    Assert-True ($oldTokenAfterGrant.Status -in @(401, 403)) "role grant invalidates the target's pre-grant JWT"

    $targetAfterApproval = Login -Username $targetUsername -Password $targetPassword
    Assert-True ($targetAfterApproval.currentRole -eq "GENERAL_MANAGER") "approval preserves the existing default role"
    Assert-True (($targetAfterApproval.availableRoles -contains "GENERAL_MANAGER") -and ($targetAfterApproval.availableRoles -contains "SALESPERSON") -and $targetAfterApproval.availableRoles.Count -eq 2) "approval adds only the requested role"
    $targetTokenAfterApproval = $targetAfterApproval.token

    $secondReview = Invoke-Api -Method POST -Path "/api/permission-requests/$($normalRequest.id)/review" -Token $script:adminToken -ExpectedStatus @(409) -Body @{
        approved = $true
        comment = "duplicate approval negative acceptance"
    }
    Assert-True ($secondReview.Status -eq 409) "terminal request cannot be approved twice"

    $revoked = (Invoke-Api -Method POST -Path "/api/permission-requests/$($normalRequest.id)/revoke" -Token $script:adminToken -Body @{
        comment = "real API direct revocation acceptance"
    }).Body
    Assert-True ($revoked.status -eq "REVOKED") "approved grant can be revoked immediately with a reason"

    $oldTokenAfterRevoke = Invoke-Api -Method POST -Path "/api/auth/switch-role" -Token $targetTokenAfterApproval -ExpectedStatus @(401, 403) -Body @{
        targetRoleCode = "SALESPERSON"
    }
    Assert-True ($oldTokenAfterRevoke.Status -in @(401, 403)) "role revocation invalidates the target's pre-revocation JWT"

    $targetAfterRevoke = Login -Username $targetUsername -Password $targetPassword
    Assert-True ($targetAfterRevoke.currentRole -eq "GENERAL_MANAGER" -and $targetAfterRevoke.availableRoles.Count -eq 1 -and $targetAfterRevoke.availableRoles -contains "GENERAL_MANAGER") "revocation removes only the requested role"

    $normalAudit = (Invoke-Api -Method GET -Path "/api/permission-requests/$($normalRequest.id)/audit" -Token $script:adminToken).Body
    $normalActions = @($normalAudit | ForEach-Object { $_.action })
    Assert-True ($normalActions.Count -eq 3 -and $normalActions -contains "CREATE" -and $normalActions -contains "APPROVE" -and $normalActions -contains "REVOKE") "append-only audit API exposes the complete normal lifecycle"

    $highRiskRequest = (Invoke-Api -Method POST -Path "/api/permission-requests" -Token $script:adminToken -ExpectedStatus @(201) -Body @{
        targetUserId = $script:targetUserId
        requestedRoleId = [long]$warehouseAdminRole.id
        requestReason = "high-risk reviewer isolation acceptance"
    }).Body
    Assert-True ($highRiskRequest.status -eq "PENDING_REVIEW" -and [int]$highRiskRequest.highRiskPermissionCount -gt 0) "high-risk request is classified from the effective permission snapshot"

    $sameReviewer = Invoke-Api -Method POST -Path "/api/permission-requests/$($highRiskRequest.id)/review" -Token $script:adminToken -ExpectedStatus @(403) -Body @{
        approved = $true
        comment = "negative same-reviewer acceptance"
    }
    Assert-True ($sameReviewer.Status -eq 403) "high-risk request requires a different TENANT_ADMIN approver"

    $approvedHighRisk = (Invoke-Api -Method POST -Path "/api/permission-requests/$($highRiskRequest.id)/review" -Token $reviewerToken -Body @{
        approved = $true
        comment = "approved by independent high-risk reviewer"
    }).Body
    Assert-True ($approvedHighRisk.status -eq "APPROVED" -and $approvedHighRisk.reviewedByUsername -eq $reviewerUsername) "different TENANT_ADMIN can approve the high-risk request"

    $revokedHighRisk = (Invoke-Api -Method POST -Path "/api/permission-requests/$($highRiskRequest.id)/revoke" -Token $script:adminToken -Body @{
        comment = "high-risk acceptance grant cleanup"
    }).Body
    Assert-True ($revokedHighRisk.status -eq "REVOKED") "high-risk grant is removed through the audited direct-revocation path"

    $highRiskAudit = (Invoke-Api -Method GET -Path "/api/permission-requests/$($highRiskRequest.id)/audit" -Token $script:adminToken).Body
    $highRiskActions = @($highRiskAudit | ForEach-Object { $_.action })
    Assert-True ($highRiskActions.Count -eq 3 -and $highRiskActions -contains "CREATE" -and $highRiskActions -contains "APPROVE" -and $highRiskActions -contains "REVOKE") "independently approved high-risk lifecycle is fully auditable"

    $rejectionRequest = (Invoke-Api -Method POST -Path "/api/permission-requests" -Token $script:adminToken -ExpectedStatus @(201) -Body @{
        targetUserId = $script:targetUserId
        requestedRoleId = [long]$salesRole.id
        requestReason = "rejection lifecycle acceptance"
    }).Body
    $rejected = (Invoke-Api -Method POST -Path "/api/permission-requests/$($rejectionRequest.id)/review" -Token $script:adminToken -Body @{
        approved = $false
        comment = "rejection comment is required and audited"
    }).Body
    Assert-True ($rejected.status -eq "REJECTED") "request can be rejected without changing target roles"
    $rejectionAudit = (Invoke-Api -Method GET -Path "/api/permission-requests/$($rejectionRequest.id)/audit" -Token $script:adminToken).Body
    $rejectionActions = @($rejectionAudit | ForEach-Object { $_.action })
    Assert-True ($rejectionActions.Count -eq 2 -and $rejectionActions -contains "CREATE" -and $rejectionActions -contains "REJECT") "rejected lifecycle is auditable without a grant event"

    $page = (Invoke-Api -Method GET -Path "/api/permission-requests?status=REVOKED&targetUserId=$($script:targetUserId)&requestedRoleId=$($salesRole.id)&page=0&size=20" -Token $script:adminToken).Body
    Assert-True ($page.total -eq 1 -and $page.items[0].targetUsername -eq $targetUsername) "server-side status, target and role filters return the accepted request"

    $deletedTargetUserId = $script:targetUserId
    $cleanupTarget = Invoke-Api -Method DELETE -Path "/api/users/$deletedTargetUserId" -Token $script:adminToken -ExpectedStatus @(200, 204)
    $script:targetUserId = $null
    Assert-True ($cleanupTarget.Status -in @(200, 204)) "temporary target account is logically deleted"
    $auditAfterTargetDeletion = (Invoke-Api -Method GET -Path "/api/permission-requests/$($normalRequest.id)/audit" -Token $script:adminToken).Body
    Assert-True ($auditAfterTargetDeletion.Count -eq 3) "audit history remains readable after target account deletion"

    Write-Output "ACCEPTANCE_OK checks=$script:checks normalRequestId=$($normalRequest.id) highRiskRequestId=$($highRiskRequest.id) rejectionRequestId=$($rejectionRequest.id)"
} finally {
    if ($null -ne $script:targetUserId -and -not [string]::IsNullOrWhiteSpace($script:adminToken)) {
        try {
            # If the run stops after a grant assertion, revoke every still-approved
            # acceptance grant before deleting the account so the request state and
            # audit evidence remain truthful.
            $approvedPage = (Invoke-Api -Method GET -Path "/api/permission-requests?status=APPROVED&targetUserId=$($script:targetUserId)&page=0&size=100" -Token $script:adminToken).Body
            foreach ($approvedRequest in @($approvedPage.items)) {
                $null = Invoke-Api -Method POST -Path "/api/permission-requests/$($approvedRequest.id)/revoke" -Token $script:adminToken -Body @{
                    comment = "automatic cleanup after interrupted permission request acceptance"
                }
                Write-Output "CLEANUP_REVOKED requestId=$($approvedRequest.id)"
            }
            $cleanup = Invoke-Api -Method DELETE -Path "/api/users/$($script:targetUserId)" -Token $script:adminToken -ExpectedStatus @(200, 204)
            Write-Output "CLEANUP_OK targetUserId=$($script:targetUserId)"
        } catch {
            Write-Warning "Acceptance user cleanup failed for targetUserId=$($script:targetUserId): $($_.Exception.Message)"
        }
    }
    if ($null -ne $script:reviewerUserId -and -not [string]::IsNullOrWhiteSpace($script:adminToken)) {
        try {
            $cleanupReviewer = Invoke-Api -Method DELETE -Path "/api/users/$($script:reviewerUserId)" -Token $script:adminToken -ExpectedStatus @(200, 204)
            Write-Output "CLEANUP_OK reviewerUserId=$($script:reviewerUserId)"
        } catch {
            Write-Warning "Acceptance reviewer cleanup failed for reviewerUserId=$($script:reviewerUserId): $($_.Exception.Message)"
        }
    }
}
