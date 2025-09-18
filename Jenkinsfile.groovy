pipeline {
    agent any
    
    environment {
        // GitHub仓库配置 - 使用SSH方式
        GIT_REPO = 'git@github.com:shijinke1990/jenkins_demo.git'
        GIT_BRANCH = 'main'
        // 阿里云服务器配置  
        ALIYUN_HOST = '120.55.61.109'
        ALIYUN_USER = 'root'
        DEPLOY_PATH = '/var/www/html'
        NODE_VERSION = '20'
    }
    
    // 根据内存知识配置Node.js 20.x版本
    tools {
        nodejs "NodeJS 20"
    }
    
    stages {
        stage('检出代码') {
            steps {
                echo '开始从GitHub检出代码...'
                echo "仓库地址: ${GIT_REPO}"
                echo "分支: ${GIT_BRANCH}"
                
                // 清理工作空间
                deleteDir()
                
                // 使用withCredentials以SSH方式从GitHub检出代码
                withCredentials([sshUserPrivateKey(
                    credentialsId: 'a408b264-fbfc-4193-8f32-fe850c47e93f', // 使用您的GitHub SSH凭据 ID
                    keyFileVariable: 'SSH_KEY',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh '''
                        # 配置SSH环境
                        mkdir -p ~/.ssh
                        cp "$SSH_KEY" ~/.ssh/github_key
                        chmod 600 ~/.ssh/github_key
                        
                        # 添加GitHub到known_hosts
                        ssh-keyscan -H github.com >> ~/.ssh/known_hosts 2>/dev/null || true
                        
                        # 配置Git使用此SSH密钥
                        export GIT_SSH_COMMAND="ssh -i ~/.ssh/github_key -o StrictHostKeyChecking=no"
                        
                        # 克隆仓库
                        git clone -b ${GIT_BRANCH} ${GIT_REPO} .
                    '''
                }
                
                // 显示当前提交信息
                script {
                    def gitCommit = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
                    def gitAuthor = sh(returnStdout: true, script: 'git log -1 --pretty=format:"%an"').trim()
                    def gitMessage = sh(returnStdout: true, script: 'git log -1 --pretty=format:"%s"').trim()
                    echo "提交ID: ${gitCommit}"
                    echo "提交作者: ${gitAuthor}"
                    echo "提交信息: ${gitMessage}"
                }
            }
        }
        
        stage('安装依赖') {
            steps {
                echo '安装项目依赖...'
                script {
                    if (isUnix()) {
                        sh 'npm install -g pnpm'
                        sh 'pnpm install'
                    } else {
                        bat 'npm install -g pnpm'
                        bat 'pnpm install'
                    }
                }
            }
        }
        
        stage('代码检查') {
            steps {
                echo '执行代码检查...'
                script {
                    try {
                        if (isUnix()) {
                            sh 'pnpm lint'
                        } else {
                            bat 'pnpm lint'
                        }
                    } catch (Exception e) {
                        echo "代码检查警告: ${e.getMessage()}"
                        // 不阻止构建流程
                    }
                }
            }
        }
        
        stage('构建项目') {
            steps {
                echo '开始构建项目...'
                script {
                    if (isUnix()) {
                        sh 'pnpm build'
                    } else {
                        bat 'pnpm build'
                    }
                }
                
                // 验证构建产物
                script {
                    if (fileExists('dist/index.html')) {
                        echo '✅ 构建成功，dist目录已生成'
                    } else {
                        error '❌ 构建失败，未找到dist/index.html'
                    }
                }
            }
        }
        
        stage('打包构建产物') {
            steps {
                echo '打包构建产物...'
                script {
                    if (isUnix()) {
                        sh '''
                            TIMESTAMP=$(date +%Y%m%d-%H%M%S)
                            tar -czf "dist-${TIMESTAMP}.tar.gz" dist/
                            ln -sf "dist-${TIMESTAMP}.tar.gz" dist.tar.gz
                            echo "构建包: dist-${TIMESTAMP}.tar.gz"
                        '''
                    } else {
                        // Windows环境使用PowerShell压缩
                        powershell '''
                            $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
                            Compress-Archive -Path .\\dist\\* -DestinationPath "dist-$timestamp.zip" -Force
                            Copy-Item "dist-$timestamp.zip" -Destination "dist.zip" -Force
                            Write-Host "构建包: dist-$timestamp.zip"
                        '''
                    }
                }
            }
        }
        
        stage('Docker容器部署') {
            steps {
                echo '开始Docker容器化部署到阿里云服务器...'
                
                // 使用withCredentials以SSH方式连接阿里云服务器
                withCredentials([sshUserPrivateKey(
                    credentialsId: 'e8886fbc-df55-4ec4-aae1-b596c9d7436b', // 使用您的阿里云SSH凭据 ID
                    keyFileVariable: 'SSH_KEY',
                    usernameVariable: 'SSH_USER'
                )]) {
                    script {
                        if (isUnix()) {
                            sh """
                                # 配置SSH环境
                                mkdir -p ~/.ssh
                                cp "$SSH_KEY" ~/.ssh/aliyun_key
                                chmod 600 ~/.ssh/aliyun_key
                                
                                # 上传构建产物
                                scp -i ~/.ssh/aliyun_key -o StrictHostKeyChecking=no dist.tar.gz ${ALIYUN_USER}@${ALIYUN_HOST}:/tmp/
                                
                                # 连接服务器并执行Docker部署
                                ssh -i ~/.ssh/aliyun_key -o StrictHostKeyChecking=no ${ALIYUN_USER}@${ALIYUN_HOST} '
                                    echo "开始Docker容器化部署..."
                                    
                                    # 检查Docker是否运行
                                    if ! docker info >/dev/null 2>&1; then
                                        echo "❌ Docker服务未运行，请检查Docker状态"
                                        systemctl status docker
                                        exit 1
                                    fi
                                    echo "✅ Docker服务运行正常"
                                    
                                    # 创建项目目录
                                    PROJECT_DIR="/opt/react-app"
                                    mkdir -p "$PROJECT_DIR"
                                    cd "$PROJECT_DIR"
                                    
                                    # 解压前端构建产物
                                    echo "📦 解压前端构建产物..."
                                    rm -rf dist 2>/dev/null || true
                                    tar -xzf /tmp/dist.tar.gz
                                    echo "✅ 构建产物解压完成"
                                    
                                    # 创建Nginx配置文件
                                    echo "⚙️  创建Nginx配置文件..."
                                    cat > nginx.conf << "NGINX_EOF"
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html index.htm;
    
    # 启用Gzip压缩
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_proxied any;
    gzip_comp_level 6;
    gzip_types
        text/plain
        text/css
        text/xml
        text/javascript
        application/json
        application/javascript
        application/xml+rss
        application/atom+xml
        image/svg+xml;
    
    # 处理React Router的前端路由
    location / {
        try_files \\$uri \\$uri/ /index.html;
    }
    
    # 静态资源缓存
    location ~* \\.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)\\$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
        access_log off;
    }
    
    # 安全headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "no-referrer-when-downgrade" always;
    add_header Content-Security-Policy "default-src 'self' http: https: data: blob: 'unsafe-inline'" always;
    
    # 隐藏Nginx版本
    server_tokens off;
    
    # 错误页面处理
    error_page 404 /index.html;
    error_page 500 502 503 504 /50x.html;
    location = /50x.html {
        root /usr/share/nginx/html;
    }
}
NGINX_EOF
                                    echo "✅ Nginx配置文件创建完成"
                                    
                                    # 停止并删除旧容器
                                    echo "🗑️  清理旧容器..."
                                    docker stop react-app-nginx 2>/dev/null || true
                                    docker rm react-app-nginx 2>/dev/null || true
                                    echo "✅ 旧容器清理完成"
                                    
                                    # 拉取Nginx镜像
                                    echo "📥 拉取Nginx镜像..."
                                    docker pull nginx:alpine
                                    echo "✅ Nginx镜像拉取完成"
                                    
                                    # 启动新的Nginx容器
                                    echo "🚀 启动新的Nginx容器..."
                                    docker run -d \
                                        --name react-app-nginx \
                                        --restart unless-stopped \
                                        -p 80:80 \
                                        -v "$PROJECT_DIR/dist:/usr/share/nginx/html:ro" \
                                        -v "$PROJECT_DIR/nginx.conf:/etc/nginx/conf.d/default.conf:ro" \
                                        nginx:alpine
                                    
                                    # 等待容器启动
                                    sleep 5
                                    
                                    # 检查容器状态
                                    if docker ps | grep -q react-app-nginx; then
                                        echo "✅ Nginx容器启动成功"
                                        docker ps --filter name=react-app-nginx --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
                                    else
                                        echo "❌ Nginx容器启动失败"
                                        docker logs react-app-nginx
                                        exit 1
                                    fi
                                    
                                    # 查看容器日志
                                    echo "📋 容器启动日志:"
                                    docker logs --tail 10 react-app-nginx
                                    
                                    # 清理临时文件
                                    rm -f /tmp/dist.tar.gz
                                    
                                    echo "✅ Docker容器部署完成！"
                                    echo "🌐 项目访问地址: http://$(curl -s ifconfig.me || hostname -I | awk "{print \$1}")"
                                '
                            """
                        } else {
                            // Windows环境暂不支持Docker部署
                            error "Windows环境暂不支持Docker容器部署，请使用Linux环境"
                        }
                    }
                }
            }
        }
        
        stage('Docker容器健康检查') {
            steps {
                echo '执行Docker容器部署后健康检查...'
                script {
                    // 等待容器启动
                    sleep(time: 15, unit: 'SECONDS')
                    
                    // 使用SSH检查Docker容器状态
                    withCredentials([sshUserPrivateKey(
                        credentialsId: 'e8886fbc-df55-4ec4-aae1-b596c9d7436b',
                        keyFileVariable: 'SSH_KEY',
                        usernameVariable: 'SSH_USER'
                    )]) {
                        sh """
                            # 检查Docker容器状态
                            ssh -i ~/.ssh/aliyun_key -o StrictHostKeyChecking=no ${ALIYUN_USER}@${ALIYUN_HOST} '
                                echo "=== Docker容器状态检查 ==="
                                
                                # 检查Docker服务
                                if docker info >/dev/null 2>&1; then
                                    echo "✅ Docker服务运行正常"
                                    echo "Docker版本: $(docker --version)"
                                else
                                    echo "❌ Docker服务未运行"
                                fi
                                
                                # 检查容器状态
                                if docker ps --filter name=react-app-nginx --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep -q react-app-nginx; then
                                    echo "✅ react-app-nginx容器运行正常"
                                    docker ps --filter name=react-app-nginx --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
                                    
                                    # 检查容器资源使用
                                    echo "容器资源使用情况:"
                                    docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}" react-app-nginx
                                    
                                    # 检查容器日志
                                    echo "最近5条容器日志:"
                                    docker logs --tail 5 react-app-nginx
                                else
                                    echo "❌ react-app-nginx容器未运行"
                                    echo "所有容器状态:"
                                    docker ps -a
                                fi
                                
                                # 检查端口监听状态
                                if netstat -tlnp 2>/dev/null | grep ":80 " | grep -q docker; then
                                    echo "✅ Docker容器正在监听80端口"
                                elif ss -tlnp 2>/dev/null | grep ":80 " | grep -q docker; then
                                    echo "✅ Docker容器正在监听80端口"
                                else
                                    echo "⚠️ 未检测到Docker容器在80端口监听"
                                    echo "当前端口监听情况:"
                                    netstat -tlnp | grep :80 || ss -tlnp | grep :80 || echo "无端口80监听"
                                fi
                                
                                # 检查部署文件
                                PROJECT_DIR="/opt/react-app"
                                if [ -f "$PROJECT_DIR/dist/index.html" ]; then
                                    echo "✅ 部署文件存在"
                                    echo "文件数量: $(find $PROJECT_DIR/dist -type f | wc -l)"
                                    echo "目录大小: $(du -sh $PROJECT_DIR/dist | cut -f1)"
                                else
                                    echo "❌ 部署文件不存在"
                                fi
                                
                                # 检查镜像信息
                                echo "Nginx镜像信息:"
                                docker images nginx:alpine --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"
                                
                                echo "=== 状态检查完成 ==="
                            '
                        """
                    }
                    
                    // HTTP访问测试
                    def response = sh(
                        script: "curl -s -o /dev/null -w '%{http_code}' --connect-timeout 10 --max-time 30 http://${ALIYUN_HOST} || echo '000'",
                        returnStdout: true
                    ).trim()
                    
                    echo "HTTP响应码: ${response}"
                    
                    if (response == '200') {
                        echo '✅ 网站部署成功，访问正常！'
                        echo "访问地址: http://${ALIYUN_HOST}"
                        
                        // 测试React路由
                        def routeResponse = sh(
                            script: "curl -s -o /dev/null -w '%{http_code}' --connect-timeout 5 --max-time 15 http://${ALIYUN_HOST}/nonexistent-route 2>/dev/null || echo '404'",
                            returnStdout: true
                        ).trim()
                        
                        if (routeResponse == '200') {
                            echo '✅ React Router前端路由工作正常'
                        }
                        
                        // 测试静态资源
                        def assetsResponse = sh(
                            script: "curl -s -o /dev/null -w '%{http_code}' --connect-timeout 5 --max-time 15 http://${ALIYUN_HOST}/assets/ 2>/dev/null || echo '403'",
                            returnStdout: true
                        ).trim()
                        
                        if (assetsResponse == '403' || assetsResponse == '404') {
                            echo '✅ 静态资源路径配置正常'
                        }
                        
                        // 测试Gzip压缩
                        def gzipTest = sh(
                            script: "curl -s -H 'Accept-Encoding: gzip' -I http://${ALIYUN_HOST} | grep -i 'content-encoding: gzip' || echo 'no-gzip'",
                            returnStdout: true
                        ).trim()
                        
                        if (gzipTest != 'no-gzip') {
                            echo '✅ Gzip压缩启用正常'
                        }
                        
                    } else if (response == '000') {
                        echo '⚠️  无法连接到服务器，可能原因:'
                        echo '- 网络连接问题'
                        echo '- 防火墙阻止访问'
                        echo '- Docker容器未启动'
                        echo '- 端口80未映射正确'
                    } else if (response == '404') {
                        echo '⚠️  页面未找到，可能原因:'
                        echo '- 部署文件不存在或路径不正确'
                        echo '- Docker容器内Nginx配置有误'
                        echo '- 文件挂载有问题'
                    } else {
                        echo "⚠️  网站访问异常，HTTP状态码: ${response}"
                        // 不阻止部署流程，只是警告
                    }
                }
            }
        }
    }
    
    post {
        success {
            echo '🎉 部署成功！'
            script {
                // 发送简单通知（使用Jenkins内置邮件功能）
                try {
                    mail (
                        to: 'your-email@example.com',
                        subject: "✅ 部署成功: ${env.JOB_NAME} - ${env.BUILD_NUMBER}",
                        body: "项目 ${env.JOB_NAME} 构建 ${env.BUILD_NUMBER} 已成功部署到阿里云服务器。\n\n构建时间: ${new Date()}\n构建URL: ${env.BUILD_URL}"
                    )
                } catch (Exception e) {
                    echo "邮件通知发送失败: ${e.getMessage()}"
                }
            }
        }
        
        failure {
            echo '❌ 部署失败！'
            script {
                // 发送失败通知
                try {
                    mail (
                        to: 'your-email@example.com',
                        subject: "❌ 部署失败: ${env.JOB_NAME} - ${env.BUILD_NUMBER}",
                        body: "项目 ${env.JOB_NAME} 构建 ${env.BUILD_NUMBER} 部署失败，请查看构建日志。\n\n构建时间: ${new Date()}\n构建URL: ${env.BUILD_URL}"
                    )
                } catch (Exception e) {
                    echo "邮件通知发送失败: ${e.getMessage()}"
                }
            }
        }
        
        always {
            echo '清理工作空间...'
            script {
                // 清理构建产物，但保留源代码
                if (isUnix()) {
                    sh 'rm -f *.tar.gz *.zip 2>/dev/null || true'
                } else {
                    bat 'del /Q *.tar.gz *.zip 2>nul || echo "清理完成"'
                }
                echo '清理完成'
            }
        }
    }
}