# Jenkins Pipeline 部署指南

## 前置准备

### 1. Jenkins环境准备

#### 必要插件安装
- Pipeline Plugin
- SSH Agent Plugin
- NodeJS Plugin
- Email Extension Plugin
- Blue Ocean (可选，提供更好的UI)

#### 全局工具配置
1. **Manage Jenkins** → **Global Tool Configuration**
2. **NodeJS** 配置：
   - 名称: Node-20
   - 版本: NodeJS 20.x
   - 自动安装: 勾选

### 2. 阿里云服务器准备

#### 服务器环境
```bash
# 更新系统
sudo apt update && sudo apt upgrade -y

# 安装Nginx
sudo apt install nginx -y

# 启动并设置开机自启
sudo systemctl start nginx
sudo systemctl enable nginx

# 创建部署目录
sudo mkdir -p /var/www/html
sudo chown -R www-data:www-data /var/www/html
```

#### SSH密钥配置

**为GitHub生成SSH密钥对**
```bash
# 在Jenkins服务器生成GitHub SSH密钥对
ssh-keygen -t rsa -b 4096 -C "jenkins@yourcompany.com" -f ~/.ssh/github_rsa

# 将公钥添加到GitHub账户
cat ~/.ssh/github_rsa.pub
# 复制输出内容，在GitHub Settings > SSH and GPG keys 中添加
```

**为阿里云服务器生成SSH密钥对**
```bash
# 在Jenkins服务器生成阿里云SSH密钥对
ssh-keygen -t rsa -b 4096 -C "jenkins-deploy@yourcompany.com" -f ~/.ssh/aliyun_rsa

# 将公钥添加到阿里云服务器
ssh-copy-id -i ~/.ssh/aliyun_rsa.pub root@your-aliyun-server-ip
```

### 3. Jenkins凭据配置

#### 添加GitHub SSH凭据
1. **Manage Jenkins** → **Manage Credentials**
2. **System** → **Global credentials** → **Add Credentials**
3. 选择类型: **SSH Username with private key**
4. 配置信息:
   - ID: `github-ssh-key`
   - Username: `git`
   - Private Key: 粘贴GitHub SSH私钥内容

#### 添加阿里云SSH凭据
1. **Manage Jenkins** → **Manage Credentials**
2. **System** → **Global credentials** → **Add Credentials**
3. 选择类型: **SSH Username with private key**
4. 配置信息:
   - ID: `aliyun-ssh-key`
   - Username: `root` (或你的用户名)
   - Private Key: 粘贴阿里云服务器SSH私钥内容

#### 添加邮件配置（可选）
1. **Manage Jenkins** → **Configure System**
2. **Extended E-mail Notification** 配置:
   - SMTP server: smtp.example.com
   - 端口: 587
   - 用户名/密码

## Jenkins Pipeline项目创建步骤

### 0. GitHub仓库配置

**项目仓库**: `git@github.com:shijinke1990/jenkins_demo.git`

#### GitHub SSH密钥配置步骤：
1. **生成SSH密钥对**（在Jenkins服务器上执行）：
   ```bash
   ssh-keygen -t rsa -b 4096 -C "jenkins@yourcompany.com" -f ~/.ssh/github_rsa
   ```

2. **添加公钥到GitHub**：
   - 复制公钥内容：`cat ~/.ssh/github_rsa.pub`
   - 登录GitHub → Settings → SSH and GPG keys → New SSH key
   - 粘贴公钥内容并保存

3. **测试SSH连接**：
   ```bash
   ssh -T git@github.com -i ~/.ssh/github_rsa
   ```

4. **在Jenkins中配置SSH凭据**：
   - 复制私钥内容：`cat ~/.ssh/github_rsa`
   - 在Jenkins中添加凭据（ID: `github-ssh-key`）

### 1. 创建新任务
1. 登录Jenkins
2. 点击 **新建任务**
3. 输入任务名称: `react-app-deploy`
4. 选择 **Pipeline**
5. 点击 **确定**

### 2. 配置Pipeline

#### General配置
- ✅ **GitHub project**: 填入项目URL
- ✅ **丢弃旧的构建**: 保留最近10次构建

#### Build Triggers（构建触发器）
选择以下一种或多种:
- ✅ **GitHub hook trigger for GITScm polling** (推荐)
- ✅ **Poll SCM**: H/5 * * * * (每5分钟检查一次)
- ✅ **Build periodically**: H 2 * * * (每天凌晨2点)

#### Pipeline配置
1. **Definition**: Pipeline script from SCM
2. **SCM**: Git
3. **Repository URL**: 你的Git仓库地址
4. **Credentials**: 选择Git凭据
5. **Branch**: */main (或你的主分支)
6. **Script Path**: Jenkinsfile

### 3. 环境变量配置

在Pipeline配置中修改environment部分:
```groovy
environment {
    ALIYUN_HOST = '你的阿里云服务器IP'  // 修改为实际IP
    ALIYUN_USER = 'root'               // 修改为实际用户名
    DEPLOY_PATH = '/var/www/html'      // 修改为实际部署路径
}
```

## 部署流程说明

### Pipeline阶段详解

1. **检出代码**: 从Git仓库拉取最新代码
2. **安装依赖**: 使用pnpm安装项目依赖
3. **代码检查**: 执行ESLint代码质量检查
4. **构建项目**: 编译TypeScript并构建生产版本
5. **打包构建产物**: 压缩dist目录
6. **部署到阿里云**: 上传并部署到服务器
7. **健康检查**: 验证部署是否成功

### 部署策略

#### 蓝绿部署 (推荐)
- 保留旧版本作为备份
- 新版本部署完成后立即切换
- 支持快速回滚

#### 备份策略
- 每次部署前自动备份当前版本
- 保留最近5个版本的备份
- 支持一键回滚到任意备份版本

## 常见问题解决

### 1. SSH连接失败
```bash
# 检查SSH连接
ssh -v root@your-server-ip

# 检查防火墙
sudo ufw status
sudo ufw allow 22
```

### 2. 权限问题
```bash
# 设置正确的目录权限
sudo chown -R www-data:www-data /var/www/html
sudo chmod -R 755 /var/www/html
```

### 3. Nginx配置
```bash
# 检查Nginx配置
sudo nginx -t

# 重载配置
sudo systemctl reload nginx

# 查看日志
sudo tail -f /var/log/nginx/error.log
```

### 4. 构建失败
- 检查Node.js版本是否匹配
- 确认所有依赖都已正确安装
- 查看Jenkins构建日志

## 高级配置

### 1. 多环境部署
在Jenkinsfile中添加参数支持:
```groovy
parameters {
    choice(name: 'ENVIRONMENT', choices: ['dev', 'staging', 'production'], description: '选择部署环境')
    booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: '跳过测试')
}
```

### 2. 自动化测试集成
在构建阶段添加测试:
```groovy
stage('运行测试') {
    steps {
        sh 'pnpm test'
    }
    post {
        always {
            publishTestResults testResultsPattern: 'test-results.xml'
        }
    }
}
```

### 3. 钉钉/企业微信通知
安装相应插件并配置webhook通知。

### 4. Docker化部署
使用Docker进行容器化部署，提供更好的环境隔离。

## 监控和维护

### 1. 部署监控
- 设置健康检查接口
- 配置告警通知
- 监控服务器资源使用

### 2. 日志管理
```bash
# 查看部署日志
sudo tail -f /var/log/nginx/access.log
sudo tail -f /var/log/nginx/error.log
```

### 3. 性能优化
- 启用Gzip压缩
- 配置静态资源缓存
- 使用CDN加速

### 4. 安全加固
- 配置HTTPS
- 设置安全headers
- 定期更新服务器系统