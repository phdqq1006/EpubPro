@echo off
chcp 65001 > nul
setlocal

title EpubPro - Publish APK to GitHub Release

echo ==========================================================
echo  🚀 EPUBPRO - ĐÓNG GÓI APK VÀ ĐẨY LÊN GITHUB RELEASE
echo ==========================================================
echo.

set SCRIPT_DIR=%~dp0
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%scripts\publish-apk.ps1" %*

echo.
echo ==========================================================
echo Hoàn tất! Bấm phím bất kỳ để đóng cửa sổ này...
pause > nul
