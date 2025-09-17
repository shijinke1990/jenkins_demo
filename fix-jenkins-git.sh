#!/bin/bash

# Jenkins Git 配置快速修复脚本
# 使用方法: ./fix-jenkins-git.sh

set -e

echo "=== Jenkins Git 配置快速修复脚本 ==="
echo ""

# 检查是否在Jenkins环境中
if [ -d "/var/jenkins_home" ]; then
    JENKINS_HOME="/var/jenkins_home"
    JENKINS_USER="jenkins"
    echo "✅ 检测到Jenkins环境: $JENKINS_HOME"
else
    echo "⚠️  未检测到标准Jenkins环境，请手动指定Jenkins主目录"
    read -p "请输入Jenkins主目录路径 (默认: $HOME): " custom_home
    JENKINS_HOME=${custom_home:-$HOME}
    JENKINS_USER=$(whoami)
fi

echo "Jenkins主目录: $JENKINS_HOME"
echo "Jenkins用户: $JENKINS_USER"
echo ""

# 1. 配置Git全局设置
echo "🔧 步骤1: 配置Git全局设置..."
git config --global user.name "Jenkins CI" 2>/dev/null || true
git config --global user.email "jenkins@yourcompany.com" 2>/dev/null || true
git config --global init.defaultBranch main 2>/dev/null || true

# 禁用SSH主机密钥检查（仅用于CI环境）
git config --global core.sshCommand "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no" 2>/dev/null || true

echo "✅ Git全局配置完成"

# 2. 创建SSH目录
echo ""
echo "🔧 步骤2: 创建SSH配置目录..."
SSH_DIR="$JENKINS_HOME/.ssh"
mkdir -p "$SSH_DIR"

# 设置SSH目录权限
if [ "$JENKINS_USER" != "$(whoami)" ] && [ "$(whoami)" = "root" ]; then
    chown -R $JENKINS_USER:$JENKINS_USER "$SSH_DIR"
fi
chmod 700 "$SSH_DIR"

echo "✅ SSH目录创建完成: $SSH_DIR"

# 3. 添加GitHub到known_hosts
echo ""
echo "🔧 步骤3: 添加GitHub到SSH known_hosts..."
KNOWN_HOSTS="$SSH_DIR/known_hosts"

# 检查GitHub是否已在known_hosts中
if ! grep -q "github.com" "$KNOWN_HOSTS" 2>/dev/null; then
    ssh-keyscan -H github.com >> "$KNOWN_HOSTS" 2>/dev/null || {
        echo "⚠️  无法自动获取GitHub SSH密钥，请检查网络连接"
        echo "手动添加命令: ssh-keyscan -H github.com >> $KNOWN_HOSTS"
    }
    echo "✅ GitHub SSH密钥已添加到known_hosts"
else
    echo "ℹ️  GitHub已存在于known_hosts中"
fi

# 设置known_hosts权限
chmod 644 "$KNOWN_HOSTS" 2>/dev/null || true

# 4. 生成SSH密钥（如果不存在）
echo ""
echo "🔧 步骤4: 检查SSH密钥..."
SSH_KEY="$SSH_DIR/github_rsa"

if [ ! -f "$SSH_KEY" ]; then
    echo "🔑 生成新的SSH密钥对..."
    read -p "请输入SSH密钥的邮箱标识 (默认: jenkins@yourcompany.com): " email
    email=${email:-"jenkins@yourcompany.com"}
    
    ssh-keygen -t rsa -b 4096 -C "$email" -f "$SSH_KEY" -N "" || {
        echo "❌ SSH密钥生成失败"
        exit 1
    }
    
    echo "✅ SSH密钥对生成完成"
    
    # 设置密钥权限
    chmod 600 "$SSH_KEY"
    chmod 644 "$SSH_KEY.pub"
    
    echo ""
    echo "📋 请将以下公钥添加到GitHub:"
    echo "==================================="
    cat "$SSH_KEY.pub"
    echo "==================================="
    echo ""
    echo "📋 私钥内容（用于Jenkins凭据配置）:"
    echo "==================================="
    cat "$SSH_KEY"
    echo "==================================="
    
else
    echo "ℹ️  SSH密钥已存在: $SSH_KEY"
fi

# 5. 创建SSH配置文件
echo ""
echo "🔧 步骤5: 创建SSH配置文件..."
SSH_CONFIG="$SSH_DIR/config"

if [ ! -f "$SSH_CONFIG" ] || ! grep -q "github.com" "$SSH_CONFIG"; then
    cat >> "$SSH_CONFIG" << EOF

# GitHub配置
Host github.com
    HostName github.com
    User git
    IdentityFile ~/.ssh/github_rsa
    IdentitiesOnly yes
    StrictHostKeyChecking no
    UserKnownHostsFile /dev/null
EOF
    
    chmod 600 "$SSH_CONFIG"
    echo "✅ SSH配置文件已创建/更新"
else
    echo "ℹ️  GitHub SSH配置已存在"
fi

# 6. 设置文件所有权
if [ "$JENKINS_USER" != "$(whoami)" ] && [ "$(whoami)" = "root" ]; then
    echo ""
    echo "🔧 步骤6: 设置文件所有权..."
    chown -R $JENKINS_USER:$JENKINS_USER "$SSH_DIR"
    echo "✅ 文件所有权设置完成"
fi

# 7. 测试Git访问
echo ""
echo "🔧 步骤7: 测试Git配置..."

echo "测试HTTPS访问..."
if git ls-remote https://github.com/shijinke1990/jenkins_demo.git HEAD >/dev/null 2>&1; then
    echo "✅ HTTPS访问正常"
else
    echo "⚠️  HTTPS访问失败，请检查网络连接"
fi

if [ -f "$SSH_KEY" ]; then
    echo "测试SSH访问..."
    if ssh -T git@github.com -i "$SSH_KEY" -o StrictHostKeyChecking=no 2>&1 | grep -q "successfully authenticated"; then
        echo "✅ SSH访问正常"
    else
        echo "⚠️  SSH访问失败，请确认公钥已添加到GitHub"
    fi
fi

echo ""
echo "🎉 配置完成！"
echo ""
echo "📋 下一步操作:"
echo "1. 如果生成了新的SSH密钥，请将公钥添加到GitHub"
echo "2. 在Jenkins中添加SSH凭据 (ID: github-ssh-key)"
echo "3. 重新运行Pipeline"
echo ""
echo "💡 提示:"
echo "- 当前Jenkinsfile已配置为HTTPS方式，可以直接使用"
echo "- 如需使用SSH方式，请完成SSH密钥配置后更新Jenkinsfile"
echo ""
echo "🔍 故障排除:"
echo "- 如果仍有问题，请查看: ./GITHUB_SSH_SETUP.md"
echo "- Jenkins日志位置: $JENKINS_HOME/logs/"