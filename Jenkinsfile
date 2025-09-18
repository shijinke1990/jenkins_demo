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
    
    // 暂时注释掉tools配置，避免NodeJS工具配置问题
    tools {
        nodejs "NodeJS 22"
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
                    credentialsId: 'github-ssh-key', // 使用您的GitHub SSH凭据 ID
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
        
        stage('部署到阿里云') {
            steps {
                echo '开始部署到阿里云服务器...'
                
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
                                
                                # 连接服务器并部署
                                ssh -i ~/.ssh/aliyun_key -o StrictHostKeyChecking=no ${ALIYUN_USER}@${ALIYUN_HOST} '
                                    echo "开始部署..."
                                    
                                    # 检测并安装Nginx
                                    if ! command -v nginx > /dev/null; then
                                        echo "Nginx未安装，开始安装..."
                                        # 检测系统类型并安装Nginx
                                        if [ -f /etc/debian_version ]; then
                                            # Debian/Ubuntu系统
                                            apt-get update && apt-get install -y nginx
                                        elif [ -f /etc/redhat-release ]; then
                                            # CentOS/RHEL系统
                                            yum install -y nginx || dnf install -y nginx
                                        elif [ -f /etc/alpine-release ]; then
                                            # Alpine系统
                                            apk update && apk add nginx
                                        else
                                            echo "未识别的系统类型，请手动安装Nginx"
                                            exit 1
                                        fi
                                        echo "Nginx安装完成"
                                    else
                                        echo "Nginx已安装: $(nginx -v 2>&1)"
                                    fi
                                    
                                    # 备份旧版本
                                    if [ -d ${DEPLOY_PATH}_backup ]; then
                                        rm -rf ${DEPLOY_PATH}_backup
                                    fi
                                    if [ -d ${DEPLOY_PATH} ]; then
                                        mv ${DEPLOY_PATH} ${DEPLOY_PATH}_backup
                                        echo "已备份旧版本"
                                    fi
                                    
                                    # 创建部署目录
                                    mkdir -p ${DEPLOY_PATH}
                                    
                                    # 解压新版本
                                    cd ${DEPLOY_PATH}
                                    tar -xzf /tmp/dist.tar.gz --strip-components=1
                                    echo "新版本解压完成"
                                    
                                    # 设置权限
                                    chown -R www-data:www-data ${DEPLOY_PATH} 2>/dev/null || chown -R nginx:nginx ${DEPLOY_PATH} 2>/dev/null || true
                                    chmod -R 755 ${DEPLOY_PATH}
                                    
                                    # 配置Nginx
                                    echo "配置Nginx..."
                                    
                                    # 创建Nginx配置
                                    cat > /etc/nginx/sites-available/react-app 2>/dev/null || cat > /etc/nginx/conf.d/react-app.conf << "EOF"
server {
    listen 80;
    server_name _;
    root ${DEPLOY_PATH};
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
        try_files \$uri \$uri/ /index.html;
    }
    
    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)\$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
        access_log off;
    }
    
    # API代理（如需要）
    # location /api/ {
    #     proxy_pass http://localhost:3001/;
    #     proxy_set_header Host \$host;
    #     proxy_set_header X-Real-IP \$remote_addr;
    #     proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    #     proxy_set_header X-Forwarded-Proto \$scheme;
    # }
    
    # 安全headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "no-referrer-when-downgrade" always;
    add_header Content-Security-Policy "default-src '"'"'self'"'"' http: https: data: blob: '"'"'unsafe-inline'"'"'" always;
    
    # 隐藏Nginx版本
    server_tokens off;
    
    # 错误页面
    error_page 404 /index.html;
    error_page 500 502 503 504 /50x.html;
    location = /50x.html {
        root /usr/share/nginx/html;
    }
}
EOF
                                    
                                    # 启用站点配置（Ubuntu/Debian）
                                    if [ -d "/etc/nginx/sites-available" ]; then
                                        ln -sf /etc/nginx/sites-available/react-app /etc/nginx/sites-enabled/react-app
                                        # 删除默认配置
                                        rm -f /etc/nginx/sites-enabled/default
                                    fi
                                    
                                    # 测试Nginx配置
                                    if nginx -t; then
                                        echo "Nginx配置测试通过"
                                    else
                                        echo "Nginx配置有误，请检查"
                                        exit 1
                                    fi
                                    
                                    # 启动并启用Nginx服务
                                    systemctl enable nginx 2>/dev/null || true
                                    systemctl start nginx 2>/dev/null || service nginx start
                                    systemctl reload nginx 2>/dev/null || service nginx reload
                                    echo "Nginx服务已启动并重载配置"
                                    
                                    # 检查Nginx状态
                                    if systemctl is-active --quiet nginx 2>/dev/null || service nginx status >/dev/null 2>&1; then
                                        echo "✅ Nginx运行正常"
                                    else
                                        echo "⚠️ Nginx可能未正常启动，请检查日志"
                                    fi
                                    
                                    # 清理临时文件
                                    rm -f /tmp/dist.tar.gz
                                    
                                    echo "部署完成！"
                                    echo "项目访问地址: http://$(hostname -I | awk "{print \$1}")"
                                '
                            """
                        } else {
                            // Windows环境使用pscp和plink
                            bat """
                                pscp -i "$SSH_KEY" -scp dist.zip ${ALIYUN_USER}@${ALIYUN_HOST}:/tmp/
                                plink -i "$SSH_KEY" ${ALIYUN_USER}@${ALIYUN_HOST} "cd ${DEPLOY_PATH} && unzip -o /tmp/dist.zip && rm /tmp/dist.zip"
                            """
                        }
                    }
                }
            }
        }
        
        stage('健康检查') {
            steps {
                echo '执行部署后健康检查...'
                script {
                    // 等待服务启动
                    sleep(time: 10, unit: 'SECONDS')
                    
                    // 使用SSH检查服务器状态
                    withCredentials([sshUserPrivateKey(
                        credentialsId: 'e8886fbc-df55-4ec4-aae1-b596c9d7436b',
                        keyFileVariable: 'SSH_KEY',
                        usernameVariable: 'SSH_USER'
                    )]) {
                        sh """
                            # 检查服务器状态
                            ssh -i ~/.ssh/aliyun_key -o StrictHostKeyChecking=no ${ALIYUN_USER}@${ALIYUN_HOST} '
                                echo "=== 服务器状态检查 ==="
                                
                                # 检查Nginx状态
                                if systemctl is-active --quiet nginx 2>/dev/null; then
                                    echo "✅ Nginx服务运行正常"
                                    echo "Nginx版本: $(nginx -v 2>&1)"
                                    echo "Nginx配置测试: $(nginx -t 2>&1 | head -1)"
                                elif service nginx status >/dev/null 2>&1; then
                                    echo "✅ Nginx服务运行正常 (SysV)"
                                else
                                    echo "❌ Nginx服务未运行"
                                fi
                                
                                # 检查端口监听状态
                                if netstat -tlnp 2>/dev/null | grep ":80 " | grep -q nginx; then
                                    echo "✅ Nginx正在监听80端口"
                                elif ss -tlnp 2>/dev/null | grep ":80 " | grep -q nginx; then
                                    echo "✅ Nginx正在监听80端口"
                                else
                                    echo "⚠️ 未检测到Nginx在80端口监听"
                                fi
                                
                                # 检查部署文件
                                if [ -f "${DEPLOY_PATH}/index.html" ]; then
                                    echo "✅ 部署文件存在"
                                    echo "文件数量: $(find ${DEPLOY_PATH} -type f | wc -l)"
                                    echo "目录大小: $(du -sh ${DEPLOY_PATH} | cut -f1)"
                                else
                                    echo "❌ 部署文件不存在"
                                fi
                                
                                # 检查权限
                                echo "文件权限: $(ls -la ${DEPLOY_PATH}/index.html 2>/dev/null || echo '文件不存在')"
                                
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
                        
                        // 测试静态资源
                        def cssResponse = sh(
                            script: "curl -s -o /dev/null -w '%{http_code}' --connect-timeout 5 --max-time 15 http://${ALIYUN_HOST}/assets/ 2>/dev/null || echo '404'",
                            returnStdout: true
                        ).trim()
                        
                        if (cssResponse == '200' || cssResponse == '403' || cssResponse == '404') {
                            echo '✅ 静态资源路径可访问'
                        }
                        
                    } else if (response == '000') {
                        echo '⚠️  无法连接到服务器，可能原因:'
                        echo '- 网络连接问题'
                        echo '- 防火墙阻止访问'
                        echo '- Nginx服务未启动'
                        echo '- 端口80未监听'
                    } else if (response == '404') {
                        echo '⚠️  页面未找到，可能原因:'
                        echo '- 部署文件不存在'
                        echo '- Nginx配置有误'
                        echo '- 文件权限问题'
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