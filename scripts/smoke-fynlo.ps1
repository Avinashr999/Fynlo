param(
    [string]$Package = "app.fynlo.dev",
    [string]$OutDir = (Join-Path $env:TEMP "fynlo-smoke-output")
)

$ErrorActionPreference = "Stop"

function Invoke-Adb {
    param([string[]]$AdbArgs)
    & adb @AdbArgs
    if ($LASTEXITCODE -ne 0) {
        throw "adb $($AdbArgs -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Dump-Screen {
    param(
        [string]$Name,
        [string[]]$MustContain,
        [string[]]$AnyContain = @()
    )

    $remote = "/sdcard/fynlo_$Name.xml"
    $local = Join-Path $OutDir "$Name.xml"
    Invoke-Adb @("shell", "uiautomator", "dump", $remote) | Out-Null
    Invoke-Adb @("pull", $remote, $local) | Out-Null
    $text = Get-Content $local -Raw

    foreach ($label in $MustContain) {
        if ($text -notmatch [regex]::Escape($label)) {
            throw "Smoke check '$Name' did not contain '$label'"
        }
    }
    if ($AnyContain.Count -gt 0) {
        $matched = $false
        foreach ($label in $AnyContain) {
            if ($text -match [regex]::Escape($label)) {
                $matched = $true
                break
            }
        }
        if (-not $matched) {
            throw "Smoke check '$Name' did not contain any expected state label: $($AnyContain -join ', ')"
        }
    }
    Write-Host "OK $Name"
}

function Wait-Screen {
    param(
        [string]$Name,
        [string[]]$MustContain,
        [string[]]$AnyContain = @(),
        [int]$TimeoutSeconds = 45
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastError = $null
    do {
        try {
            Dump-Screen $Name $MustContain $AnyContain
            return
        } catch {
            $lastError = $_
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline)

    throw $lastError
}

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb is not available on PATH"
}

New-Item -ItemType Directory -Force $OutDir | Out-Null

Invoke-Adb @("shell", "monkey", "-p", $Package, "1") | Out-Null
Start-Sleep -Seconds 2
Dump-Screen "login" @("Fynlo")

$loginDump = Get-Content (Join-Path $OutDir "login.xml") -Raw
if ($loginDump -match "Continue without signing in") {
    Invoke-Adb @("shell", "input", "tap", "636", "1852") | Out-Null
    Start-Sleep -Seconds 3
}
Invoke-Adb @("shell", "input", "tap", "170", "2625") | Out-Null
Start-Sleep -Seconds 1
# Dashboard restores its last scroll offset. Normalize to the top before
# asserting hero labels so a prior manual bottom-section check does not fail
# the smoke run.
Invoke-Adb @("shell", "input", "swipe", "650", "720", "650", "2300", "600") | Out-Null
Start-Sleep -Milliseconds 400
Invoke-Adb @("shell", "input", "swipe", "650", "720", "650", "2300", "600") | Out-Null
Start-Sleep -Milliseconds 400
Wait-Screen "dashboard" @("Total net worth", "Dashboard", "Loans", "Invest", "Reports", "Expenses")

Invoke-Adb @("shell", "input", "tap", "98", "227") | Out-Null
Start-Sleep -Seconds 1
Wait-Screen "drawer" @("Settings", "Profile &amp; Security", "Budgeting", "Savings Goals", "Contact Book", "EMI Calculator")
Invoke-Adb @("shell", "input", "tap", "1030", "220") | Out-Null
Start-Sleep -Milliseconds 600

Invoke-Adb @("shell", "input", "tap", "402", "2625") | Out-Null
Start-Sleep -Seconds 1
Wait-Screen "loans" @("Loans", "Add Loan", "Borrowers")

Invoke-Adb @("shell", "input", "tap", "636", "2625") | Out-Null
Start-Sleep -Seconds 1
Wait-Screen "invest" @("Invest", "Portfolio value", "Add Investment", "Holdings")

Invoke-Adb @("shell", "input", "tap", "868", "2625") | Out-Null
Start-Sleep -Seconds 1
Wait-Screen "reports" @("Reports", "Report PDF", "P&amp;L Statement", "Net Worth")

Invoke-Adb @("shell", "input", "tap", "1102", "2625") | Out-Null
Start-Sleep -Seconds 1
Wait-Screen "expenses" @("Expenses") @("Spent in", "No expenses", "Recent", "Category Breakdown")

Write-Host "Smoke passed. Dumps saved to $OutDir"
