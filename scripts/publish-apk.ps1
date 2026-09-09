<#
.SYNOPSIS
    Script tự động đóng gói APK Release trên máy local và xuất bản lên GitHub Releases cho Tester.

.DESCRIPTION
    1. Biên dịch file APK Release bằng Gradle (sử dụng cache máy local cực nhanh).
    2. Đổi tên APK theo phiên bản chuẩn (ví dụ: EpubPro-v1.0.0-test.apk).
    3. Tự động đẩy lên GitHub Releases kèm theo Mã QR Code quét tải app trực tiếp trên điện thoại.
    4. Hỗ trợ lưu GitHub Token vào file bí mật .github_token (đã được .gitignore bảo vệ).

.PARAMETER Tag
    Tên phiên bản / Git Tag (ví dụ: v1.0.0-test, v1.0.1-beta).

.PARAMETER Title
    Tiêu đề bản phát hành trên GitHub.

.PARAMETER Notes
    Ghi chú cập nhật tính năng mới cho tester.

.PARAMETER Token
    GitHub Personal Access Token (tùy chọn). Nếu không truyền, script sẽ đọc từ file .github_token hoặc biến môi trường.

.PARAMETER SkipBuild
    Nếu bật cờ này, script sẽ bỏ qua bước build Gradle và dùng file APK đã build sẵn.

.EXAMPLE
    .\scripts\publish-apk.ps1 -Tag "v1.0.0-test" -Notes "Sửa lỗi font chữ và cập nhật giao diện"
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string]$Tag,

    [Parameter(Mandatory = $false)]
    [string]$Title,

    [Parameter(Mandatory = $false)]
    [string]$Notes = "Bản build thử nghiệm tính năng mới được đóng gói từ máy phát triển.",

    [Parameter(Mandatory = $false)]
    [string]$Token,

    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

# Chuyển về thư mục gốc của project
$RootDir = Split-Path -Parent $PSScriptRoot
Set-Location $RootDir

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " 🚀 EPUBPRO - LOCAL BUILD & GITHUB RELEASE PUBLISHER" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Xác định Git Remote và thông tin Repo
$RemoteUrl = git remote get-url origin 2>$null
if (-not $RemoteUrl) {
    Write-Error "Không tìm thấy Git remote 'origin'. Vui lòng kiểm tra lại repository."
}

# Tách owner và repo từ URL (hỗ trợ cả HTTPS và SSH)
if ($RemoteUrl -match "github\.com[:/](?<owner>[^/]+)/(?<repo>[^\.]+)") {
    $RepoOwner = $Matches['owner']
    $RepoName = $Matches['repo']
} else {
    Write-Error "Không thể trích xuất thông tin GitHub repository từ remote URL: $RemoteUrl"
}

Write-Host "📦 Repository: $RepoOwner/$RepoName" -ForegroundColor Green

# 2. Xác định Tag phiên bản
if (-not $Tag) {
    $DefaultTag = "v1.0.0-test-" + (Get-Date -Format "yyyyMMdd-HHmm")
    $TagInput = Read-Host "Nhập Tag phiên bản (Mặc định: $DefaultTag)"
    if ([string]::IsNullOrWhiteSpace($TagInput)) {
        $Tag = $DefaultTag
    } else {
        $Tag = $TagInput.Trim()
    }
}

if (-not $Title) {
    $Title = "EpubPro Test Build ($Tag)"
}

Write-Host "🏷️  Phiên bản: $Tag" -ForegroundColor Yellow
Write-Host "📝 Tiêu đề:   $Title" -ForegroundColor Yellow

# 3. Biên dịch Release APK
$ApkSource = "$RootDir\app\build\outputs\apk\release\app-release.apk"
$ApkTargetName = "EpubPro-$Tag.apk"
$ApkTarget = "$RootDir\$ApkTargetName"

if (-not $SkipBuild) {
    Write-Host "`n⏳ Đang tiến hành biên dịch Release APK..." -ForegroundColor Cyan
    $BuildStartTime = Get-Date

    # Chạy lệnh gradle wrapper trên Windows
    $GradleCommand = ".\gradlew.bat"
    $GradleArgs = @("assembleRelease", "-x", "lintVitalRelease")

    & $GradleCommand $GradleArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Quá trình biên dịch APK thất bại. Vui lòng kiểm tra lại log ở trên."
    }

    $BuildDuration = [math]::Round(((Get-Date) - $BuildStartTime).TotalSeconds, 1)
    Write-Host "✅ Biên dịch thành công trong $BuildDuration giây!" -ForegroundColor Green
} else {
    Write-Host "`n⏩ Bỏ qua bước biên dịch APK (SkipBuild)." -ForegroundColor Gray
}

if (-not (Test-Path $ApkSource)) {
    Write-Error "Không tìm thấy file APK đầu ra tại: $ApkSource"
}

# Sao chép và đổi tên file APK
Copy-Item -Path $ApkSource -Destination $ApkTarget -Force
$ApkSizeMb = [math]::Round((Get-Item $ApkTarget).Length / 1MB, 2)
Write-Host "📁 File APK: $ApkTargetName ($ApkSizeMb MB)" -ForegroundColor Green

# 4. Kiểm tra GitHub Token để upload tự động
$TokenFile = "$RootDir\.github_token"
if (-not $Token) {
    if ($env:GITHUB_TOKEN) {
        $Token = $env:GITHUB_TOKEN
    } elseif (Test-Path $TokenFile) {
        $Token = (Get-Content $TokenFile -Raw).Trim()
    }
}

if (-not $Token) {
    Write-Host "`n🔑 Chưa tìm thấy GitHub Token để tự động tải lên." -ForegroundColor Yellow
    Write-Host "   (Bạn có thể tạo Token tại: https://github.com/settings/tokens - chỉ cần tick quyền 'repo')" -ForegroundColor Gray
    $InputToken = Read-Host "Dán GitHub Token của bạn vào đây (hoặc bấm ENTER để mở trình duyệt kéo thả thủ công)"
    if (-not [string]::IsNullOrWhiteSpace($InputToken)) {
        $Token = $InputToken.Trim()
        Set-Content -Path $TokenFile -Value $Token
        Write-Host "🔒 Đã lưu Token vào .github_token (đã được .gitignore bảo vệ an toàn)." -ForegroundColor Green
    }
}

# 5. Đẩy Git Tag lên GitHub
Write-Host "`n📌 Đang tạo và đẩy Git Tag '$Tag' lên GitHub..." -ForegroundColor Cyan
git tag -d $Tag 2>$null | Out-Null
git push origin --delete $Tag 2>$null | Out-Null
git tag -a $Tag -m "$Title"
git push origin $Tag
if ($LASTEXITCODE -ne 0) {
    Write-Warning "Không thể push tag lên Git. Vẫn tiếp tục tạo release..."
}

# 6. Tải lên GitHub Release
if ($Token) {
    Write-Host "`n☁️  Đang tạo GitHub Release qua API..." -ForegroundColor Cyan

    $DownloadUrl = "https://github.com/$RepoOwner/$RepoName/releases/download/$Tag/$ApkTargetName"
    $QrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=$DownloadUrl"

    $ReleaseBody = @"
## 📱 Thông tin bản thử nghiệm ($Tag)

$Notes

---

### ⬇️ Hướng dẫn cài đặt cho Tester:
1. **Tải trực tiếp bằng điện thoại:**
   - [👉 Bấm vào đây để tải $ApkTargetName]($DownloadUrl)
2. **Hoặc quét mã QR dưới đây bằng điện thoại Android để tải ngay:**

![Quét mã QR để cài đặt]($QrUrl)

---
*Bản dựng được đóng gói trực tiếp từ máy phát triển.*
"@

    $Headers = @{
        "Authorization" = "Bearer $Token"
        "Accept"        = "application/vnd.github+json"
        "User-Agent"    = "EpubPro-Release-Script"
    }

    # Tạo Release
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

        Write-Host "✅ Đã tạo Release ID: $ReleaseId" -ForegroundColor Green
        Write-Host "📤 Đang tải file APK ($ApkSizeMb MB) lên Release Assets..." -ForegroundColor Cyan

        # Upload APK binary
        $UploadAssetUrl = "https://uploads.github.com/repos/$RepoOwner/$RepoName/releases/$ReleaseId/assets?name=$ApkTargetName"
        $UploadHeaders = @{
            "Authorization" = "Bearer $Token"
            "Accept"        = "application/vnd.github+json"
            "Content-Type"  = "application/vnd.android.package-archive"
            "User-Agent"    = "EpubPro-Release-Script"
        }

        $UploadResponse = Invoke-RestMethod -Uri $UploadAssetUrl -Method Post -Headers $UploadHeaders -InFile $ApkTarget

        Write-Host "`n🎉 XUẤT BẢN THÀNH CÔNG RỰC RỠ!" -ForegroundColor Green
        Write-Host "🔗 Link Release: $ReleaseHtmlUrl" -ForegroundColor Cyan
        Write-Host "📲 Tester có thể quét mã QR hoặc bấm link trên để cài đặt ngay lập tức!" -ForegroundColor Yellow

        # Mở trang release trên trình duyệt
        Start-Process $ReleaseHtmlUrl
    } catch {
        Write-Warning "Lỗi khi gọi GitHub API: $_"
        Write-Host "Chuyển sang chế độ mở web kéo thả thủ công..." -ForegroundColor Yellow
        $FallbackToWeb = $true
    }
} else {
    $FallbackToWeb = $true
}

if ($FallbackToWeb) {
    Write-Host "`n🌐 Đang mở trang tạo Release trên trình duyệt..." -ForegroundColor Cyan
    $ManualReleaseUrl = "https://github.com/$RepoOwner/$RepoName/releases/new?tag=$Tag&title=" + [System.Uri]::EscapeDataString($Title)
    Start-Process $ManualReleaseUrl

    # Mở thư mục chứa file APK để kéo thả
    Write-Host "📂 Đang mở thư mục chứa file APK: $ApkTarget" -ForegroundColor Green
    explorer.exe /select,"$ApkTarget"

    Write-Host "`n👉 Bạn chỉ cần kéo thả file '$ApkTargetName' vào trang web vừa mở và bấm 'Publish release' là xong!" -ForegroundColor Yellow
}
