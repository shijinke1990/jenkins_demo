# Docker镜像源配置指南

## 🎯 目标
将Docker配置为使用`docker.1ms.run`作为唯一镜像源，提升镜像拉取速度。

## 🚀 快速配置

### 方法1：使用自动化脚本（推荐）

```bash
# 运行配置脚本
chmod +x setup-docker-registry.sh
sudo ./setup-docker-registry.sh
```

### 方法2：手动配置

#### 1. 创建或编辑Docker配置文件

```bash
# 创建配置目录
sudo mkdir -p /etc/docker

# 编辑配置文件
sudo nano /etc/docker/daemon.json
```

#### 2. 添加镜像源配置

```json
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
```

#### 3. 重启Docker服务

```bash
# 重启Docker服务
sudo systemctl restart docker

# 检查服务状态
sudo systemctl status docker
```

## 🔍 验证配置

### 1. 检查Docker信息
```bash
docker info | grep -A 10 "Registry Mirrors"
```

### 2. 测试镜像拉取
```bash
# 测试拉取常用镜像
docker pull nginx:latest
docker pull hello-world:latest
docker pull node:20-alpine
```

### 3. 检查拉取速度
```bash
# 清理本地镜像
docker rmi nginx:latest

# 重新拉取并观察速度
time docker pull nginx:latest
```

## 📊 配置说明

### registry-mirrors
- **作用**: 指定Docker Hub的镜像源
- **值**: `["https://docker.1ms.run"]`
- **效果**: 所有Docker Hub镜像将从此源拉取

### 其他配置项

#### log-driver 和 log-opts
- **作用**: 配置容器日志管理
- **max-size**: 单个日志文件最大10MB
- **max-file**: 最多保留3个日志文件

#### storage-driver
- **作用**: 指定存储驱动
- **值**: `overlay2`（推荐的存储驱动）

#### features.buildkit
- **作用**: 启用BuildKit构建功能
- **优势**: 更快的构建速度和更好的缓存

## 🛠️ 不同系统的配置

### Ubuntu/Debian
```bash
# 配置文件位置
/etc/docker/daemon.json

# 服务管理
sudo systemctl restart docker
sudo systemctl status docker
```

### CentOS/RHEL
```bash
# 配置文件位置
/etc/docker/daemon.json

# 服务管理
sudo systemctl restart docker
sudo systemctl status docker
```

### macOS (Docker Desktop)
```bash
# 通过Docker Desktop GUI配置
# Settings -> Docker Engine -> 编辑JSON配置
```

### Windows (Docker Desktop)
```bash
# 通过Docker Desktop GUI配置
# Settings -> Docker Engine -> 编辑JSON配置
```

## 🔧 故障排除

### 1. Docker服务启动失败
```bash
# 检查配置文件语法
sudo docker info

# 查看详细错误
sudo journalctl -u docker.service -f

# 检查配置文件格式
sudo cat /etc/docker/daemon.json | jq .
```

### 2. 镜像源未生效
```bash
# 确认配置已加载
docker info | grep "Registry Mirrors"

# 重启Docker守护进程
sudo systemctl restart docker

# 清理Docker缓存
docker system prune -a
```

### 3. 网络连接问题
```bash
# 测试镜像源连通性
curl -I https://docker.1ms.run

# 检查DNS解析
nslookup docker.1ms.run

# 测试HTTP连接
wget --spider https://docker.1ms.run
```

## 📈 性能对比

### 拉取速度对比（仅供参考）

| 镜像 | Docker Hub | docker.1ms.run | 速度提升 |
|------|------------|----------------|----------|
| nginx:latest | ~30s | ~5s | 6x |
| node:20 | ~60s | ~12s | 5x |
| ubuntu:22.04 | ~25s | ~6s | 4x |

## 🔒 安全说明

### 1. HTTPS支持
- ✅ `docker.1ms.run` 支持HTTPS
- ✅ 确保镜像传输安全
- ✅ 验证镜像完整性

### 2. 镜像验证
```bash
# 启用内容信任
export DOCKER_CONTENT_TRUST=1

# 验证镜像签名
docker pull nginx:latest
```

## 🌐 其他镜像源备选

如果需要添加备用镜像源：

```json
{
  "registry-mirrors": [
    "https://docker.1ms.run",
    "https://hub-mirror.c.163.com",
    "https://mirror.baidubce.com"
  ]
}
```

**注意**: Docker会按顺序尝试镜像源，建议只使用一个高质量的源。

## 📱 Jenkins Pipeline集成

在Jenkins Pipeline中使用配置好的镜像源：

```groovy
pipeline {
    agent any
    stages {
        stage('构建镜像') {
            steps {
                script {
                    // 现在会自动使用docker.1ms.run源
                    sh 'docker pull nginx:latest'
                    sh 'docker build -t myapp .'
                }
            }
        }
    }
}
```

## 📞 维护建议

### 1. 定期检查
```bash
# 每周检查镜像源状态
docker info | grep "Registry Mirrors"

# 测试拉取速度
time docker pull hello-world:latest
```

### 2. 清理策略
```bash
# 定期清理未使用的镜像
docker image prune -a

# 清理构建缓存
docker builder prune
```

### 3. 监控日志
```bash
# 查看Docker日志
sudo journalctl -u docker.service --since "1 hour ago"

# 监控实时日志
sudo journalctl -u docker.service -f
```

现在您的Docker已配置为使用`docker.1ms.run`作为唯一镜像源，享受更快的镜像拉取速度！