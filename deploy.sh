#!/bin/bash

# 阿里云服务器部署脚本
# 使用方法: ./deploy.sh [环境] [服务器IP]

set -e

# 默认配置
ENVIRONMENT=${1:-production}
SERVER_IP=${2:-"你的阿里云服务器IP"}
SERVER_USER="root"
DEPLOY_PATH="/var/www/html"
BACKUP_PATH="/var/www/backup"

echo "=== 开始部署到 $ENVIRONMENT 环境 ==="
echo "服务器: $SERVER_IP"
echo "部署路径: $DEPLOY_PATH"

# 1. 本地构建
echo "步骤1: 本地构建项目..."
pnpm install
pnpm build

# 2. 打包构建产物
echo "步骤2: 打包构建产物..."
tar -czf dist-$(date +%Y%m%d-%H%M%S).tar.gz dist/

# 3. 上传到服务器
echo "步骤3: 上传到服务器..."
scp dist-*.tar.gz $SERVER_USER@$SERVER_IP:/tmp/

# 4. 服务器端部署
echo "步骤4: 服务器端部署..."
ssh $SERVER_USER@$SERVER_IP << 'EOF'
    # 创建备份目录
    mkdir -p /var/www/backup
    
    # 备份当前版本
    if [ -d /var/www/html ]; then
        BACKUP_NAME="backup-$(date +%Y%m%d-%H%M%S)"
        mv /var/www/html /var/www/backup/$BACKUP_NAME
        echo "已备份当前版本到: $BACKUP_NAME"
    fi
    
    # 创建新的部署目录
    mkdir -p /var/www/html
    
    # 解压新版本
    cd /var/www/html
    tar -xzf /tmp/dist-*.tar.gz --strip-components=1
    
    # 设置权限
    chown -R www-data:www-data /var/www/html
    chmod -R 755 /var/www/html
    
    # 重启nginx
    systemctl reload nginx
    
    # 清理临时文件
    rm -f /tmp/dist-*.tar.gz
    
    echo "部署完成！"
EOF

# 5. 健康检查
echo "步骤5: 健康检查..."
sleep 5
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://$SERVER_IP)

if [ "$HTTP_CODE" = "200" ]; then
    echo "✅ 部署成功！网站正常访问"
    # 清理本地临时文件
    rm -f dist-*.tar.gz
else
    echo "❌ 部署可能有问题，HTTP状态码: $HTTP_CODE"
    exit 1
fi

echo "=== 部署完成 ==="