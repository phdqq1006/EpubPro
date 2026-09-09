# ==========================================================
# EpubPro - Local Build and Publish to GitHub Releases
# ==========================================================

[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string]$Tag,

    [Parameter(Mandatory = $false)]
    [string]$Title,

    [Parameter(Mandatory = $false)]
    [string]$Notes = "Ban build thu nghiem moi dong goi tu may dev.",

    [Parameter(Mandatory = $false)]
    [string]$Token,

    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

# Chuyen ve thu muc goc cua project
$RootDir = Split-Path -Parent $PSScriptRoot
Set-Location $RootDir

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " EPUBPRO - LOCAL BUILD & GITHUB RELEASE PUBLISHER" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Xac dinh Git Remote va Repo
$RemoteUrl = git remote get-url origin 2>$null
if (-not $RemoteUrl) {
    Write-Error "Khong tim thay Git remote 'origin'. Vui long kiem tra lai repository."
}

if ($RemoteUrl -match "github\.com[:/](?<owner>[^/]+)/(?<repo>[^\.]+)") {
    $RepoOwner = $Matches['owner']
    $RepoName = $Matches['repo']
} else {
    Write-Error "Khong the trich xuat thong tin GitHub tu URL: $RemoteUrl"
}

Write-Host "Repository: $RepoOwner/$RepoName" -ForegroundColor Green

# 2. Xac dinh Tag phien ban
if (-not $Tag) {
    $DefaultTag = "v1.0.0-test-" + (Get-Date -Format "yyyyMMdd-HHmm")
    $TagInput = Read-Host "Nhap Tag phien ban (Mac dinh: $DefaultTag)"
    if ([string]::IsNullOrWhiteSpace($TagInput)) {
        $Tag = $DefaultTag
    } else {
        $Tag = $TagInput.Trim()
    }
}

if (-not $Title) {
    $Title = "EpubPro Test Build ($Tag)"
}

Write-Host "Tag:      $Tag" -ForegroundColor Yellow
Write-Host "Title:    $Title" -ForegroundColor Yellow

# 3. Bien dich Release APK
$ApkSource = Join-Path $RootDir "app\build\outputs\apk\release\app-release.apk"
$ApkTargetName = "EpubPro-$Tag.apk"
$ApkTarget = Join-Path $RootDir $ApkTargetName

if (-not $SkipBuild) {
    Write-Host "`nDang tien hanh bien dich Release APK..." -ForegroundColor Cyan
    $BuildStartTime = Get-Date

    $GradleCommand = ".\gradlew.bat"
    $GradleArgs = @("assembleRelease", "-x", "lintVitalRelease")

    & $GradleCommand $GradleArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Bien dich APK that bai. Vui long kiem tra log o tren."
    }

    $BuildDuration = [math]::Round(((Get-Date) - $BuildStartTime).TotalSeconds, 1)
    Write-Host "Bien dich thanh cong trong $BuildDuration giay!" -ForegroundColor Green
} else {
    Write-Host "`nBo qua buoc bien dich APK (SkipBuild)." -ForegroundColor Gray
}

if (-not (Test-Path $ApkSource)) {
    Write-Error "Khong tim thay file APK dau ra tai: $ApkSource"
}

Copy-Item -Path $ApkSource -Destination $ApkTarget -Force
$ApkItem = Get-Item $ApkTarget
$ApkSizeMb = [math]::Round($ApkItem.Length / 1MB, 2)
Write-Host "File APK: $ApkTargetName ($ApkSizeMb MB)" -ForegroundColor Green

# 4. Kiem tra GitHub Token
$TokenFile = Join-Path $RootDir ".github_token"
if (-not $Token) {
    if ($env:GITHUB_TOKEN) {
        $Token = $env:GITHUB_TOKEN
    } elseif (Test-Path $TokenFile) {
        $Token = (Get-Content $TokenFile -Raw).Trim()
    }
}

if (-not $Token) {
    Write-Host "`nChua co GitHub Token de tu dong upload len Release." -ForegroundColor Yellow
    Write-Host "(Tao token tai: https://github.com/settings/tokens voi quyen 'repo')" -ForegroundColor Gray
    $InputToken = Read-Host "Dan GitHub Token vao day (hoac bam ENTER de keo tha thu cong)"
    if (-not [string]::IsNullOrWhiteSpace($InputToken)) {
        $Token = $InputToken.Trim()
        Set-Content -Path $TokenFile -Value $Token
        Write-Host "Da luu Token vao .github_token an toan." -ForegroundColor Green
    }
}

# 5. Day Git Tag len GitHub
Write-Host "`nDang tao va day Git Tag '$Tag' len GitHub..." -ForegroundColor Cyan
git tag -d $Tag 2>$null | Out-Null
git push origin --delete $Tag 2>$null | Out-Null
git tag -a $Tag -m "$Title"
git push origin $Tag
if ($LASTEXITCODE -ne 0) {
    Write-Warning "Khong the push tag. Van tiep tuc tao release..."
}

# 6. Upload len GitHub Release
$FallbackToWeb = $false

if ($Token) {
    Write-Host "`nDang tao GitHub Release qua API..." -ForegroundColor Cyan

    $DownloadUrl = "https://github.com/$RepoOwner/$RepoName/releases/download/$Tag/$ApkTargetName"
    $QrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=220x220`&data=" + [System.Uri]::EscapeDataString($DownloadUrl)

    $BodyLines = @(
        "## Thong tin ban thu nghiem ($Tag)",
        "",
        "$Notes",
        "",
        "---",
        "",
        "### Huong dan cai dat cho Tester:",
        "1. **Tai truc tiep bang dien thoai:**",
        "   - [Bam vao day de tai $ApkTargetName]($DownloadUrl)",
        "2. **Hoac quet ma QR duoi day bang dien thoai Android de tai ngay:**",
        "",
        "![$ApkTargetName]($QrUrl)",
        "",
        "---",
        "*Ban dung duoc dong goi truc tiep tu may phat trien.*"
    )
    $ReleaseBody = $BodyLines -join "`n"

    $Headers = @{
        "Authorization" = "Bearer $Token"
        "Accept"        = "application/vnd.github+json"
        "User-Agent"    = "EpubPro-Release-Script"
    }

    $ReleasePayload = @{
        tag_name   = $Tag
        name       = $Title
        body       = $ReleaseBody
        prerelease = $true
    } | ConvertTo-Json

    try {
        $CreateReleaseUrl = "https://api.github.com/repos/$RepoOwner/$RepoName/releases"
        $ReleaseResponse = Invoke-RestMethod -Uri $CreateReleaseUrl -Method Post -Headers $Headers -Body $ReleasePayload -ContentType "application/json; charset=utf-8"
        $ReleaseId = $ReleaseResponse.id
        $ReleaseHtmlUrl = $ReleaseResponse.html_url

        Write-Host "Da tao Release ID: $ReleaseId" -ForegroundColor Green
        Write-Host "Dang tai file APK ($ApkSizeMb MB) len Release Assets..." -ForegroundColor Cyan

        $UploadAssetUrl = "https://uploads.github.com/repos/$RepoOwner/$RepoName/releases/$ReleaseId/assets?name=$ApkTargetName"
        $UploadHeaders = @{
            "Authorization" = "Bearer $Token"
            "Accept"        = "application/vnd.github+json"
            "Content-Type"  = "application/vnd.android.package-archive"
            "User-Agent"    = "EpubPro-Release-Script"
        }

        $UploadResponse = Invoke-RestMethod -Uri $UploadAssetUrl -Method Post -Headers $UploadHeaders -InFile $ApkTarget

        Write-Host "`nXUAT BAN THANH CONG!" -ForegroundColor Green
        Write-Host "Link Release: $ReleaseHtmlUrl" -ForegroundColor Cyan
        Write-Host "Tester co the quet ma QR hoac mo link de cai dat ngay!" -ForegroundColor Yellow

        Start-Process $ReleaseHtmlUrl
    } catch {
        Write-Warning "Loi khi goi GitHub API: $_"
        Write-Host "Chuyen sang che do keo tha thu cong..." -ForegroundColor Yellow
        $FallbackToWeb = $true
    }
} else {
    $FallbackToWeb = $true
}

if ($FallbackToWeb) {
    Write-Host "`nDang mo trang tao Release tren trinh duyet..." -ForegroundColor Cyan
    $ManualReleaseUrl = "https://github.com/$RepoOwner/$RepoName/releases/new?tag=" + [System.Uri]::EscapeDataString($Tag) + "&title=" + [System.Uri]::EscapeDataString($Title)
    Start-Process $ManualReleaseUrl

    Write-Host "Dang mo thu muc chua file APK: $ApkTarget" -ForegroundColor Green
    explorer.exe /select,"$ApkTarget"

    Write-Host "`nKeo tha file '$ApkTargetName' vao trang web vua mo va bam 'Publish release' la xong!" -ForegroundColor Yellow
}
