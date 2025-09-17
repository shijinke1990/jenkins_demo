@echo off
REM GitHub SSH 配置脚本 (Windows版本)
REM 使用方法: setup-github-ssh.bat

echo === GitHub SSH 配置脚本 (Windows) ===
echo.

REM 检查SSH目录
if not exist "%USERPROFILE%\.ssh" (
    echo 创建SSH目录...
    mkdir "%USERPROFILE%\.ssh"
)

REM 检查是否已存在GitHub SSH密钥
if exist "%USERPROFILE%\.ssh\github_rsa" (
    echo ⚠️  GitHub SSH密钥已存在: %USERPROFILE%\.ssh\github_rsa
    set /p regenerate="是否要重新生成？(y/N): "
    if /i not "%regenerate%"=="y" (
        echo 使用现有密钥...
        goto show_keys
    ) else (
        echo 删除现有密钥...
        del "%USERPROFILE%\.ssh\github_rsa" 2>nul
        del "%USERPROFILE%\.ssh\github_rsa.pub" 2>nul
    )
)

REM 生成新的SSH密钥对
if not exist "%USERPROFILE%\.ssh\github_rsa" (
    echo.
    set /p email="📋 请输入您的邮箱地址（用于SSH密钥标识）: "
    if "%email%"=="" set email=jenkins@yourcompany.com
    
    echo 🔑 生成GitHub SSH密钥对...
    ssh-keygen -t rsa -b 4096 -C "%email%" -f "%USERPROFILE%\.ssh\github_rsa" -N ""
    
    if %errorlevel% neq 0 (
        echo ❌ SSH密钥生成失败！请确认已安装Git或OpenSSH
        pause
        exit /b 1
    )
    
    echo ✅ SSH密钥对生成成功！
)

:show_keys
REM 显示公钥
echo.
echo 🔑 GitHub SSH 公钥内容：
echo ====================
type "%USERPROFILE%\.ssh\github_rsa.pub"
echo ====================
echo.

REM 显示私钥（用于Jenkins配置）
echo 🔐 Jenkins SSH 私钥内容（用于Jenkins凭据配置）：
echo ====================
type "%USERPROFILE%\.ssh\github_rsa"
echo ====================
echo.

REM 配置SSH config
echo ⚙️  配置SSH config...
if not exist "%USERPROFILE%\.ssh\config" (
    echo. > "%USERPROFILE%\.ssh\config"
)

REM 检查是否已有GitHub配置
findstr /c:"Host github.com" "%USERPROFILE%\.ssh\config" >nul
if %errorlevel% neq 0 (
    echo. >> "%USERPROFILE%\.ssh\config"
    echo # GitHub配置 >> "%USERPROFILE%\.ssh\config"
    echo Host github.com >> "%USERPROFILE%\.ssh\config"
    echo     HostName github.com >> "%USERPROFILE%\.ssh\config"
    echo     User git >> "%USERPROFILE%\.ssh\config"
    echo     IdentityFile ~/.ssh/github_rsa >> "%USERPROFILE%\.ssh\config"
    echo     IdentitiesOnly yes >> "%USERPROFILE%\.ssh\config"
    echo ✅ SSH config 配置完成
) else (
    echo ℹ️  SSH config 中已存在GitHub配置
)

echo.
echo 📋 下一步操作：
echo 1. 复制上面的公钥内容
echo 2. 登录 GitHub → Settings → SSH and GPG keys → New SSH key
echo 3. 粘贴公钥内容并保存
echo 4. 运行测试命令: ssh -T git@github.com
echo 5. 在Jenkins中添加SSH凭据（ID: github-ssh-key），使用上面的私钥内容
echo.

REM 提供测试选项
set /p test_connection="是否立即测试GitHub SSH连接？(y/N): "
if /i "%test_connection%"=="y" (
    echo 🔍 测试GitHub SSH连接...
    ssh -T git@github.com 2>&1 | findstr "successfully authenticated" >nul
    if %errorlevel% equ 0 (
        echo ✅ GitHub SSH连接测试成功！
    ) else (
        echo ❌ GitHub SSH连接测试失败
        echo 请确认：
        echo 1. 公钥已正确添加到GitHub
        echo 2. 网络连接正常
        echo 3. SSH配置正确
        echo 4. 已安装Git或OpenSSH客户端
    )
)

echo.
echo 🎉 GitHub SSH配置完成！
echo.
pause