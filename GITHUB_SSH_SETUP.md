# GitHub SSH 配置完整指南

## 当前状态
✅ **临时解决方案**: 已将仓库地址改为HTTPS方式 `https://github.com/shijinke1990/jenkins_demo.git`
⚠️ **推荐方案**: 配置SSH密钥以获得更好的安全性和性能

## 问题分析

根据错误日志，主要问题是：
1. Jenkins中没有找到名为 `github-ssh-key` 的凭据
2. SSH主机密钥验证失败

## 解决方案

### 方案1：继续使用HTTPS（简单）

**优点**: 无需配置SSH密钥，立即可用
**缺点**: 对于私有仓库需要用户名密码

当前Jenkinsfile已更新为HTTPS方式，可以直接使用。

### 方案2：配置SSH密钥（推荐）

#### 步骤1：在Jenkins服务器生成SSH密钥

```bash
# 1. 登录Jenkins服务器
docker exec -it jenkins bash  # 如果Jenkins运行在Docker中
# 或直接SSH到Jenkins服务器

# 2. 切换到jenkins用户
su - jenkins

# 3. 生成SSH密钥对
ssh-keygen -t rsa -b 4096 -C "jenkins@yourcompany.com" -f ~/.ssh/github_rsa

# 4. 查看公钥（复制此内容到GitHub）
cat ~/.ssh/github_rsa.pub

# 5. 查看私钥（复制此内容到Jenkins凭据）
cat ~/.ssh/github_rsa
```

#### 步骤2：添加公钥到GitHub

1. 登录GitHub
2. 进入 **Settings** → **SSH and GPG keys**
3. 点击 **New SSH key**
4. 粘贴公钥内容并保存

#### 步骤3：在Jenkins中配置SSH凭据

1. 进入Jenkins管理界面
2. **Manage Jenkins** → **Manage Credentials**
3. **System** → **Global credentials (unrestricted)** → **Add Credentials**
4. 选择类型：**SSH Username with private key**
5. 配置信息：
   - **ID**: `github-ssh-key`
   - **Description**: `GitHub SSH Key for repository access`
   - **Username**: `git`
   - **Private Key**: 选择 "Enter directly"，粘贴私钥内容
   - **Passphrase**: 如果设置了密码则填入，否则留空

#### 步骤4：配置SSH主机密钥验证

在Jenkins服务器上执行：

```bash
# 方法1: 手动添加GitHub到known_hosts
ssh-keyscan -H github.com >> ~/.ssh/known_hosts

# 方法2: 第一次手动连接GitHub
ssh -T git@github.com

# 验证配置
ssh -T git@github.com -i ~/.ssh/github_rsa
# 成功时会显示: Hi shijinke1990! You've successfully authenticated...
```

#### 步骤5：更新Jenkinsfile使用SSH

如果要使用SSH方式，需要将Jenkinsfile改回：

```groovy
environment {
    GIT_REPO = 'git@github.com:shijinke1990/jenkins_demo.git'
    // ... 其他配置
}

// 在checkout部分恢复credentialsId
checkout([
    $class: 'GitSCM',
    branches: [[name: "*/${GIT_BRANCH}"]],
    userRemoteConfigs: [[
        url: "${GIT_REPO}",
        credentialsId: 'github-ssh-key'
    ]]
])
```

## Docker环境特殊配置

如果Jenkins运行在Docker容器中：

### 1. 持久化SSH配置

在docker-compose.yml中添加SSH目录映射：

```yaml
services:
  jenkins:
    image: jenkins/jenkins:lts
    volumes:
      - jenkins_home:/var/jenkins_home
      - jenkins_ssh:/var/jenkins_home/.ssh  # SSH配置持久化
    # ... 其他配置
```

### 2. 在容器中配置SSH

```bash
# 进入Jenkins容器
docker exec -it jenkins bash

# 确保SSH目录权限正确
chown -R jenkins:jenkins /var/jenkins_home/.ssh
chmod 700 /var/jenkins_home/.ssh
chmod 600 /var/jenkins_home/.ssh/*
```

## 验证配置

### 测试SSH连接

```bash
# 在Jenkins服务器上测试
ssh -T git@github.com

# 克隆测试
git clone git@github.com:shijinke1990/jenkins_demo.git /tmp/test-clone
```

### Jenkins Pipeline测试

创建一个简单的测试Pipeline：

```groovy
pipeline {
    agent any
    stages {
        stage('Test Git Clone') {
            steps {
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: "*/main"]],
                    userRemoteConfigs: [[
                        url: "git@github.com:shijinke1990/jenkins_demo.git",
                        credentialsId: 'github-ssh-key'
                    ]]
                ])
                sh 'ls -la'
            }
        }
    }
}
```

## 故障排除

### 常见错误及解决方案

#### 1. "Host key verification failed"
```bash
# 解决方案：添加GitHub到known_hosts
ssh-keyscan -H github.com >> ~/.ssh/known_hosts
```

#### 2. "Permission denied (publickey)"
- 检查公钥是否正确添加到GitHub
- 检查私钥是否正确配置在Jenkins
- 检查SSH密钥文件权限

#### 3. "Could not read from remote repository"
- 检查仓库URL是否正确
- 检查是否有仓库访问权限
- 验证SSH密钥配置

#### 4. Jenkins凭据找不到
- 确认凭据ID完全匹配: `github-ssh-key`
- 检查凭据是否在正确的scope下
- 重启Jenkins服务

## 推荐的最佳实践

1. **使用专用的部署密钥**: 为每个项目创建专门的SSH密钥
2. **定期轮换密钥**: 定期更新SSH密钥以提高安全性
3. **限制权限**: 只授予必要的仓库访问权限
4. **备份配置**: 备份SSH密钥和Jenkins配置
5. **监控访问**: 定期检查GitHub的SSH密钥使用情况

## 下一步

当前Pipeline已使用HTTPS方式，可以正常运行。如果需要更高的安全性和性能，请按照上述步骤配置SSH密钥。

配置完成后，可以将Jenkinsfile恢复为SSH方式来获得更好的性能。