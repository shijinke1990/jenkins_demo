# Jenkins 插件安装和配置指南

## 问题解决方案

根据您的错误信息，我们需要解决以下问题：

### 1. NodeJS工具配置问题

**错误**: `No jenkins.plugins.nodejs.tools.NodeJSInstallation named 22 found`

**解决方案**:
1. 安装NodeJS插件
2. 配置NodeJS工具

#### 安装NodeJS插件
1. 进入 **Manage Jenkins** → **Manage Plugins**
2. 在 **Available** 标签页搜索 "NodeJS"
3. 勾选 **NodeJS Plugin** 并点击 **Install without restart**

#### 配置NodeJS工具
1. 进入 **Manage Jenkins** → **Global Tool Configuration**
2. 找到 **NodeJS** 部分
3. 点击 **Add NodeJS**
4. 配置信息：
   - **Name**: `Node-20` (重要：必须与Jenkinsfile中的名称匹配)
   - **Version**: 选择 NodeJS 20.x
   - **Global npm packages to install**: 填入 `pnpm` (可选)
   - **Global npm packages refresh hours**: 72
5. 点击 **Save**

### 2. cleanWs方法不存在

**错误**: `No such DSL method 'cleanWs' found`

**解决方案**: 安装Workspace Cleanup插件

1. 进入 **Manage Jenkins** → **Manage Plugins**
2. 在 **Available** 标签页搜索 "Workspace Cleanup"
3. 勾选 **Workspace Cleanup Plugin** 并安装

### 3. emailext方法不存在

**错误**: `No such DSL method 'emailext' found`

**解决方案**: 安装Email Extension插件

1. 进入 **Manage Jenkins** → **Manage Plugins**
2. 在 **Available** 标签页搜索 "Email Extension"
3. 勾选 **Email Extension Plugin** 并安装

## 必需插件列表

以下是运行Pipeline所需的插件：

### 核心插件（通常已安装）
- ✅ **Pipeline Plugin** - Pipeline支持
- ✅ **Git Plugin** - Git集成
- ✅ **SSH Agent Plugin** - SSH密钥管理

### 需要手动安装的插件
- 🔧 **NodeJS Plugin** - Node.js环境支持
- 🧹 **Workspace Cleanup Plugin** - 工作空间清理
- 📧 **Email Extension Plugin** - 邮件通知

### 可选但推荐的插件
- 🎨 **Blue Ocean** - 现代化Pipeline UI
- 🔔 **DingTalk Plugin** - 钉钉通知
- 📊 **Build Monitor Plugin** - 构建监控
- 🐳 **Docker Plugin** - Docker支持

## 快速安装脚本

您可以使用Jenkins CLI批量安装插件：

```bash
# 下载Jenkins CLI
wget http://localhost:8080/jnlpJars/jenkins-cli.jar

# 批量安装插件
java -jar jenkins-cli.jar -s http://localhost:8080/ -auth admin:your-password install-plugin \
    nodejs \
    ws-cleanup \
    email-ext \
    blueocean

# 重启Jenkins
java -jar jenkins-cli.jar -s http://localhost:8080/ -auth admin:your-password restart
```

## 配置步骤总结

### 步骤1: 安装插件
按照上述说明安装必需插件

### 步骤2: 配置NodeJS
1. **Manage Jenkins** → **Global Tool Configuration**
2. 添加NodeJS工具，名称设为 `Node-20`

### 步骤3: 配置邮件
1. **Manage Jenkins** → **Configure System**
2. 配置 **E-mail Notification** 或 **Extended E-mail Notification**

### 步骤4: 更新Jenkinsfile
使用修复后的Jenkinsfile或简化版本

## 两个Jenkinsfile版本

### 1. 完整版本 (Jenkinsfile)
- 包含所有功能
- 需要安装上述所有插件
- 更全面的错误处理

### 2. 简化版本 (Jenkinsfile-simple)  
- 减少插件依赖
- 使用内置功能
- 更稳定，适合初次使用

## 推荐配置流程

1. **先使用简化版本**: 将 `Jenkinsfile-simple` 重命名为 `Jenkinsfile`
2. **验证基本功能**: 确保代码拉取和构建正常
3. **逐步添加功能**: 安装插件后使用完整版本

## 故障排除

### 如果插件安装失败
1. 检查Jenkins版本兼容性
2. 检查网络连接
3. 使用离线安装方式

### 如果NodeJS仍然出错
1. 确认工具名称匹配: `Node-20`
2. 检查NodeJS版本是否正确安装
3. 重启Jenkins服务

### 如果SSH连接失败
1. 验证SSH密钥配置
2. 检查服务器防火墙设置
3. 测试手动SSH连接

## 验证配置

配置完成后，创建一个测试Pipeline来验证：

1. 创建新的Pipeline项目
2. 使用 `Jenkinsfile-simple`
3. 触发构建
4. 查看控制台输出，确认所有阶段正常执行

如果仍有问题，请提供具体的错误信息，我将进一步协助解决。