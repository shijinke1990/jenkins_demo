#!/bin/bash

# Docker镜像源配置脚本
# 设置 docker.1ms.run 为唯一镜像源

set -e

echo "=== Docker镜像源配置脚本 ==="
echo ""

# 检查Docker是否安装
if ! command -v docker &> /dev/null; then
    echo "❌ Docker未安装，请先安装Docker"
    exit 1
fi

echo "✅ Docker已安装: $(docker --version)"

# 创建或备份现有配置
DOCKER_CONFIG_DIR="/etc/docker"
DAEMON_JSON="$DOCKER_CONFIG_DIR/daemon.json"

echo ""
echo "🔧 配置Docker镜像源..."

# 确保配置目录存在
sudo mkdir -p "$DOCKER_CONFIG_DIR"

# 备份现有配置
if [ -f "$DAEMON_JSON" ]; then
    echo "📋 备份现有配置文件..."
    sudo cp "$DAEMON_JSON" "$DAEMON_JSON.backup.$(date +%Y%m%d-%H%M%S)"
    echo "✅ 备份完成: $DAEMON_JSON.backup.$(date +%Y%m%d-%H%M%S)"
fi

# 创建新的daemon.json配置
echo "🛠️  创建新的Docker配置..."
sudo tee "$DAEMON_JSON" > /dev/null << 'EOF'
{
  "registry-mirrors": [
    "https://docker.1ms.run"
  ],
  "insecure-registries": [],
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  },
  "storage-driver": "overlay2",
  "experimental": false,
  "features": {
    "buildkit": true
  }
}
EOF

echo "✅ Docker配置文件已创建"

# 显示配置内容
echo ""
echo "📄 当前Docker配置内容:"
echo "========================"
sudo cat "$DAEMON_JSON"
echo "========================"

# 重启Docker服务
echo ""
echo "🔄 重启Docker服务..."

if systemctl is-active --quiet docker; then
    echo "停止Docker服务..."
    sudo systemctl stop docker
    sleep 2
fi

echo "启动Docker服务..."
sudo systemctl start docker

# 等待Docker启动
echo "等待Docker服务启动..."
sleep 5

# 检查Docker服务状态
if systemctl is-active --quiet docker; then
    echo "✅ Docker服务重启成功"
else
    echo "❌ Docker服务启动失败"
    echo "请检查配置文件语法:"
    sudo docker info
    exit 1
fi

# 验证配置
echo ""
echo "🔍 验证Docker配置..."

# 检查镜像源配置
echo "检查镜像源配置:"
if docker info 2>/dev/null | grep -q "docker.1ms.run"; then
    echo "✅ 镜像源配置成功"
    docker info | grep -A 10 "Registry Mirrors:" || echo "镜像源信息未显示"
else
    echo "⚠️  镜像源配置可能未生效，请检查:"
    docker info | grep -A 5 "Registry" || echo "未找到镜像源信息"
fi

# 测试镜像拉取
echo ""
echo "🧪 测试镜像拉取..."
echo "尝试拉取hello-world镜像..."

if docker pull hello-world:latest; then
    echo "✅ 镜像拉取测试成功"
    echo "🎉 Docker镜像源配置完成！"
else
    echo "❌ 镜像拉取测试失败"
    echo "请检查网络连接和镜像源配置"
fi

echo ""
echo "📋 配置摘要:"
echo "- 镜像源: https://docker.1ms.run"
echo "- 配置文件: $DAEMON_JSON"
echo "- 备份文件: $DAEMON_JSON.backup.*"
echo ""
echo "💡 使用说明:"
echo "- 现在拉取镜像将自动使用docker.1ms.run源"
echo "- 如需恢复原配置，请使用备份文件"
echo "- 配置已持久化，重启系统后仍然生效"
echo ""
echo "🔧 常用命令:"
echo "- 查看配置: docker info"
echo "- 测试拉取: docker pull nginx:latest"
echo "- 查看镜像: docker images"