#!/bin/bash

# GitHub SSH 配置脚本
# 使用方法: ./setup-github-ssh.sh

set -e

echo "=== GitHub SSH 配置脚本 ==="

# 检查是否已存在GitHub SSH密钥
if [ -f ~/.ssh/github_rsa ]; then
    echo "⚠️  GitHub SSH密钥已存在: ~/.ssh/github_rsa"
    read -p "是否要重新生成？(y/N): " regenerate
    if [[ $regenerate != "y" && $regenerate != "Y" ]]; then
        echo "使用现有密钥..."
    else
        echo "删除现有密钥..."
        rm -f ~/.ssh/github_rsa ~/.ssh/github_rsa.pub
    fi
fi

# 生成新的SSH密钥对
if [ ! -f ~/.ssh/github_rsa ]; then
    echo "📋 请输入您的邮箱地址（用于SSH密钥标识）:"
    read -p "邮箱: " email
    
    if [ -z "$email" ]; then
        email="jenkins@yourcompany.com"
        echo "使用默认邮箱: $email"
    fi
    
    echo "🔑 生成GitHub SSH密钥对..."
    ssh-keygen -t rsa -b 4096 -C "$email" -f ~/.ssh/github_rsa -N ""
    
    echo "✅ SSH密钥对生成成功！"
fi

# 显示公钥
echo ""
echo "🔑 GitHub SSH 公钥内容："
echo "===================="
cat ~/.ssh/github_rsa.pub
echo "===================="
echo ""

# 显示私钥（用于Jenkins配置）
echo "🔐 Jenkins SSH 私钥内容（用于Jenkins凭据配置）："
echo "===================="
cat ~/.ssh/github_rsa
echo "===================="
echo ""

# 配置SSH config
echo "⚙️  配置SSH config..."
if [ ! -f ~/.ssh/config ]; then
    touch ~/.ssh/config
fi

# 检查是否已有GitHub配置
if ! grep -q "Host github.com" ~/.ssh/config; then
    echo "" >> ~/.ssh/config
    echo "# GitHub配置" >> ~/.ssh/config
    echo "Host github.com" >> ~/.ssh/config
    echo "    HostName github.com" >> ~/.ssh/config
    echo "    User git" >> ~/.ssh/config
    echo "    IdentityFile ~/.ssh/github_rsa" >> ~/.ssh/config
    echo "    IdentitiesOnly yes" >> ~/.ssh/config
    echo "✅ SSH config 配置完成"
else
    echo "ℹ️  SSH config 中已存在GitHub配置"
fi

# 设置正确的权限
chmod 600 ~/.ssh/github_rsa
chmod 644 ~/.ssh/github_rsa.pub
chmod 600 ~/.ssh/config

echo ""
echo "📋 下一步操作："
echo "1. 复制上面的公钥内容"
echo "2. 登录 GitHub → Settings → SSH and GPG keys → New SSH key"
echo "3. 粘贴公钥内容并保存"
echo "4. 运行测试命令: ssh -T git@github.com"
echo "5. 在Jenkins中添加SSH凭据（ID: github-ssh-key），使用上面的私钥内容"
echo ""

# 提供测试选项
read -p "是否立即测试GitHub SSH连接？(y/N): " test_connection
if [[ $test_connection == "y" || $test_connection == "Y" ]]; then
    echo "🔍 测试GitHub SSH连接..."
    if ssh -T git@github.com 2>&1 | grep -q "successfully authenticated"; then
        echo "✅ GitHub SSH连接测试成功！"
    else
        echo "❌ GitHub SSH连接测试失败"
        echo "请确认："
        echo "1. 公钥已正确添加到GitHub"
        echo "2. 网络连接正常"
        echo "3. SSH配置正确"
    fi
fi

echo ""
echo "🎉 GitHub SSH配置完成！"