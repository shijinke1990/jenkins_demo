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
        // 部署目录已修改为 /var/www/html
        NODE_VERSION = '22'
        // 项目域名配置
        PROJECT_DOMAIN = 'demo.boluobo.com'
        PROJECT_NAME = 'demo-boluobo-app'
    }
    
    // 根据内存知识配置Node.js 20.x版本
    tools {
        nodejs "NodeJS 22"
    }
    
    stages {
        stage('检出代码') {
            steps {
                echo '开始从GitHub检出代码...'
                echo "仓库地址: ${GIT_REPO}"
                echo "分支: ${GIT_BRANCH}"
                echo "目标域名: ${PROJECT_DOMAIN}"
                
                // 清理工作空间
                deleteDir()
                
                // 使用withCredentials以SSH方式从GitHub检出代码
                withCredentials([sshUserPrivateKey(
                    credentialsId: 'github-ssh-key', // GitHub SSH凭据 ID
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
                            TIMESTAMP=\$(date +%Y%m%d-%H%M%S)
                            tar -czf "${PROJECT_NAME}-${TIMESTAMP}.tar.gz" dist/
                            ln -sf "${PROJECT_NAME}-${TIMESTAMP}.tar.gz" "${PROJECT_NAME}.tar.gz"
                            echo "构建包: ${PROJECT_NAME}-${TIMESTAMP}.tar.gz"
                        '''
                    } else {
                        // Windows环境使用PowerShell压缩
                        powershell '''
                            $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
                            $projectName = $env:PROJECT_NAME
                            Compress-Archive -Path .\\dist\\* -DestinationPath "$projectName-$timestamp.zip" -Force
                            Copy-Item "$projectName-$timestamp.zip" -Destination "$projectName.zip" -Force
                            Write-Host "构建包: $projectName-$timestamp.zip"
                        '''
                    }
                }
            }
        }
        
        stage('部署到服务器') {
            steps {
                echo '开始部署到阿里云服务器...'
                echo "目标服务器: ${ALIYUN_HOST}"
                echo "部署路径: /var/www/${PROJECT_DOMAIN}"
                
                // 使用SSH凭据部署到服务器
                withCredentials([sshUserPrivateKey(
                    credentialsId: 'e8886fbc-df55-4ec4-aae1-b596c9d7436b',
                    keyFileVariable: 'SSH_KEY',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh """
                        # 配置SSH环境
                        mkdir -p ~/.ssh
                        cp "\$SSH_KEY" ~/.ssh/aliyun_key
                        chmod 600 ~/.ssh/aliyun_key
                        
                        # 添加阿里云服务器到known_hosts
                        ssh-keyscan -H ${ALIYUN_HOST} >> ~/.ssh/known_hosts 2>/dev/null || true
                        
                        echo "=== 开始部署 ${PROJECT_DOMAIN} ==="
                        
                        # 在服务器上创建项目目录结构并处理版本更新
                        ssh -i ~/.ssh/aliyun_key -o StrictHostKeyChecking=no ${ALIYUN_USER}@${ALIYUN_HOST} '
                            PROJECT_WEB_DIR="${DEPLOY_PATH}"
                            BACKUP_DIR="/var/www/backups/html"
                            TIMESTAMP=\$(date +%Y%m%d-%H%M%S)
                            
                            echo "📁 准备部署目录..."
                            
                            # 创建备份目录
                            mkdir -p "\$BACKUP_DIR"
                            
                            # 如果项目目录已存在，先备份
                            if [ -d "\$PROJECT_WEB_DIR" ] && [ "\$(ls -A \$PROJECT_WEB_DIR 2>/dev/null)" ]; then
                                echo "📦 备份现有文件到: \$BACKUP_DIR/backup-\$TIMESTAMP"
                                cp -r "\$PROJECT_WEB_DIR" "\$BACKUP_DIR/backup-\$TIMESTAMP"
                                
                                # 清理旧文件（保留.htaccess等隐藏文件）
                                echo "🧹 清理现有部署文件..."
                                find "\$PROJECT_WEB_DIR" -type f -not -name '.*' -delete 2>/dev/null || true
                                find "\$PROJECT_WEB_DIR" -type d -empty -not -path "\$PROJECT_WEB_DIR" -delete 2>/dev/null || true
                            else
                                echo "📁 创建新的项目目录: \$PROJECT_WEB_DIR"
                                mkdir -p "\$PROJECT_WEB_DIR"
                            fi
                            
                            # 设置目录权限
                            chown -R www-data:www-data "\$PROJECT_WEB_DIR" 2>/dev/null || chown -R nginx:nginx "\$PROJECT_WEB_DIR" 2>/dev/null || true
                            chmod -R 755 "\$PROJECT_WEB_DIR"
                            
                            # 清理超过7天的备份文件
                            find "\$BACKUP_DIR" -name "backup-*" -type d -mtime +7 -exec rm -rf {} + 2>/dev/null || true
                            
                            echo "✅ 目录准备完成"
                        '
                        
                        echo "📤 传输构建产物到服务器..."
                        
                        # 传输构建产物
                        if [ -f "${PROJECT_NAME}.tar.gz" ]; then
                            # 上传压缩包
                            scp -i ~/.ssh/aliyun_key -o StrictHostKeyChecking=no "${PROJECT_NAME}.tar.gz" ${ALIYUN_USER}@${ALIYUN_HOST}:/tmp/
                            
                            # 在服务器上解压并部署
                            ssh -i ~/.ssh/aliyun_key -o StrictHostKeyChecking=no ${ALIYUN_USER}@${ALIYUN_HOST} '
                                PROJECT_WEB_DIR="${DEPLOY_PATH}"
                                
                                echo "📦 解压构建产物..."
                                cd /tmp
                                tar -xzf "${PROJECT_NAME}.tar.gz"
                                
                                echo "🚀 部署文件到目标目录..."
                                # 将dist目录下的所有文件复制到项目目录
                                cp -r dist/* "\$PROJECT_WEB_DIR/"
                                
                                # 设置正确的文件权限
                                chown -R www-data:www-data "\$PROJECT_WEB_DIR" 2>/dev/null || chown -R nginx:nginx "\$PROJECT_WEB_DIR" 2>/dev/null || true
                                find "\$PROJECT_WEB_DIR" -type f -exec chmod 644 {} +
                                find "\$PROJECT_WEB_DIR" -type d -exec chmod 755 {} +
                                
                                # 清理临时文件
                                rm -f "/tmp/${PROJECT_NAME}.tar.gz"
                                rm -rf "/tmp/dist"
                                
                                echo "✅ 文件部署完成"
                                echo "📊 部署统计信息:"
                                echo "  - 文件数量: \$(find \$PROJECT_WEB_DIR -type f | wc -l)"
                                echo "  - 目录大小: \$(du -sh \$PROJECT_WEB_DIR | cut -f1)"
                                echo "  - 部署时间: \$(date)"
                            '
                        elif [ -f "${PROJECT_NAME}.zip" ]; then
                            # Windows环境生成的zip文件
                            scp -i ~/.ssh/aliyun_key -o StrictHostKeyChecking=no "${PROJECT_NAME}.zip" ${ALIYUN_USER}@${ALIYUN_HOST}:/tmp/
                            
                            ssh -i ~/.ssh/aliyun_key -o StrictHostKeyChecking=no ${ALIYUN_USER}@${ALIYUN_HOST} '
                                PROJECT_WEB_DIR="${DEPLOY_PATH}"
                                
                                echo "📦 解压构建产物..."
                                cd /tmp
                                unzip -q "${PROJECT_NAME}.zip"
                                
                                echo "🚀 部署文件到目标目录..."
                                cp -r dist/* "\$PROJECT_WEB_DIR/"
                                
                                # 设置正确的文件权限
                                chown -R www-data:www-data "\$PROJECT_WEB_DIR" 2>/dev/null || chown -R nginx:nginx "\$PROJECT_WEB_DIR" 2>/dev/null || true
                                find "\$PROJECT_WEB_DIR" -type f -exec chmod 644 {} +
                                find "\$PROJECT_WEB_DIR" -type d -exec chmod 755 {} +
                                
                                # 清理临时文件
                                rm -f "/tmp/${PROJECT_NAME}.zip"
                                rm -rf "/tmp/dist"
                                
                                echo "✅ 文件部署完成"
                            '
                        else
                            echo "❌ 未找到构建产物文件"
                            exit 1
                        fi
                        
                        echo "🎉 部署完成！"
                    """
                }
                
                // 部署后立即验证关键文件
                script {
                    echo '📋 验证部署文件...'
                    
                    withCredentials([sshUserPrivateKey(
                        credentialsId: 'e8886fbc-df55-4ec4-aae1-b596c9d7436b',
                        keyFileVariable: 'SSH_KEY',
                        usernameVariable: 'SSH_USER'
                    )]) {
                        def deploymentCheck = sh(
                            script: """
                                ssh -i ~/.ssh/aliyun_key -o StrictHostKeyChecking=no ${ALIYUN_USER}@${ALIYUN_HOST} '
                                    PROJECT_WEB_DIR="${DEPLOY_PATH}"
                                    if [ -f "\$PROJECT_WEB_DIR/index.html" ]; then
                                        echo "SUCCESS"
                                    else
                                        echo "FAILED"
                                        ls -la "\$PROJECT_WEB_DIR" 2>/dev/null || echo "目录不存在"
                                    fi
                                '
                            """,
                            returnStdout: true
                        ).trim()
                        
                        if (deploymentCheck.contains('SUCCESS')) {
                            echo '✅ 部署文件验证成功'
                        } else {
                            echo '❌ 部署文件验证失败'
                            echo "检查结果: ${deploymentCheck}"
                            error '部署验证失败，停止流水线'
                        }
                    }
                }
            }
        }
       
        stage('部署验证') {
            steps {
                echo '执行部署验证和健康检查...'
                script {
                    // 等待Nginx重载完成
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
                                echo "=== ${PROJECT_DOMAIN} 服务器部署状态检查 (部署到 ${DEPLOY_PATH}) ==="
                                
                                # 检查Nginx服务状态
                                if systemctl is-active --quiet nginx; then
                                    echo "✅ Nginx服务运行正常"
                                    echo "Nginx状态: \$(systemctl is-active nginx)"
                                    echo "Nginx进程: \$(ps aux | grep nginx | grep -v grep | wc -l) 个进程"
                                else
                                    echo "❌ Nginx服务未运行"
                                    systemctl status nginx --no-pager -l
                                fi
                                
                                # 检查站点配置
                                if [ -f "/etc/nginx/sites-enabled/${PROJECT_DOMAIN}" ]; then
                                    echo "✅ 站点配置已启用: /etc/nginx/sites-enabled/${PROJECT_DOMAIN}"
                                else
                                    echo "❌ 站点配置未启用"
                                fi
                                
                                # 检查端口监听状态
                                if netstat -tlnp 2>/dev/null | grep ":80 " | grep -q nginx; then
                                    echo "✅ Nginx正在监听80端口"
                                elif ss -tlnp 2>/dev/null | grep ":80 " | grep -q nginx; then
                                    echo "✅ Nginx正在监听80端口"
                                else
                                    echo "⚠️ 未检测到Nginx在80端口监听"
                                    echo "当前端口监听情况:"
                                    netstat -tlnp | grep :80 || ss -tlnp | grep :80 || echo "无端口80监听"
                                fi
                                
                                # 检查部署文件
                                PROJECT_WEB_DIR="${DEPLOY_PATH}"
                                if [ -f "\$PROJECT_WEB_DIR/index.html" ]; then
                                    echo "✅ 部署文件存在: \$PROJECT_WEB_DIR"
                                    echo "文件数量: \$(find \$PROJECT_WEB_DIR -type f | wc -l)"
                                    echo "目录大小: \$(du -sh \$PROJECT_WEB_DIR | cut -f1)"
                                    echo "文件权限: \$(ls -ld \$PROJECT_WEB_DIR | awk "{print \\\$1 \\\" \\\" \\\$3 \\\" \\\" \\\$4}")"
                                else
                                    echo "❌ 部署文件不存在: \$PROJECT_WEB_DIR/index.html"
                                    ls -la "\$PROJECT_WEB_DIR" 2>/dev/null || echo "目录不存在"
                                fi
                                
                                # 检查Nginx错误日志
                                if [ -f "/var/log/nginx/error.log" ]; then
                                    ERROR_COUNT=\$(wc -l < "/var/log/nginx/error.log" 2>/dev/null || echo "0")
                                    echo "错误日志条数: \$ERROR_COUNT"
                                    if [ "\$ERROR_COUNT" -gt 0 ]; then
                                        echo "最近5条错误日志:"
                                        tail -5 "/var/log/nginx/error.log" 2>/dev/null || echo "无法读取错误日志"
                                    fi
                                fi
                                
                                # Nginx配置测试
                                echo "🧪 执行Nginx配置测试..."
                                if nginx -t 2>/dev/null; then
                                    echo "✅ Nginx配置语法正确"
                                else
                                    echo "❌ Nginx配置语法错误"
                                    nginx -t
                                fi
                                
                                echo "=== 状态检查完成 ==="
                            '
                        """
                    }
                    
                    // HTTP访问测试
                    def response = sh(
                        script: "curl -s -o /dev/null -w '%{http_code}' --connect-timeout 10 --max-time 30 -H 'Host: ${PROJECT_DOMAIN}' http://${ALIYUN_HOST} || echo '000'",
                        returnStdout: true
                    ).trim()
                    
                    echo "HTTP响应码 (${PROJECT_DOMAIN}): ${response}"
                    
                    if (response == '200') {
                        echo "✅ 网站部署成功，${PROJECT_DOMAIN} 访问正常！"
                        echo "访问地址: http://${PROJECT_DOMAIN}"
                        echo "服务器IP: http://${ALIYUN_HOST}"
                        echo "部署路径: ${DEPLOY_PATH}"
                        
                        // 测试React路由
                        def routeResponse = sh(
                            script: "curl -s -o /dev/null -w '%{http_code}' --connect-timeout 5 --max-time 15 -H 'Host: ${PROJECT_DOMAIN}' http://${ALIYUN_HOST}/nonexistent-route 2>/dev/null || echo '404'",
                            returnStdout: true
                        ).trim()
                        
                        if (routeResponse == '200') {
                            echo '✅ React Router前端路由工作正常'
                        } else {
                            echo "⚠️ React Router响应码: ${routeResponse}"
                        }
                        
                        // 测试Gzip压缩
                        def gzipTest = sh(
                            script: "curl -s -H 'Accept-Encoding: gzip' -H 'Host: ${PROJECT_DOMAIN}' -I http://${ALIYUN_HOST} | grep -i 'content-encoding: gzip' || echo 'no-gzip'",
                            returnStdout: true
                        ).trim()
                        
                        if (gzipTest != 'no-gzip') {
                            echo '✅ Gzip压缩启用正常'
                        } else {
                            echo '⚠️ Gzip压缩未检测到'
                        }
                        
                        // 测试静态资源缓存
                        def cacheTest = sh(
                            script: "curl -s -H 'Host: ${PROJECT_DOMAIN}' -I http://${ALIYUN_HOST}/assets/ | grep -i 'cache-control' || echo 'no-cache'",
                            returnStdout: true
                        ).trim()
                        
                        if (cacheTest != 'no-cache') {
                            echo '✅ 静态资源缓存配置正常'
                        }
                        
                    } else if (response == '000') {
                        echo '⚠️  无法连接到服务器，可能原因:'
                        echo '- 网络连接问题'
                        echo '- 防火墙阻止访问'
                        echo '- Nginx服务未启动'
                        echo '- 端口80未正确配置'
                        echo '- 域名DNS解析问题'
                    } else if (response == '404') {
                        echo '⚠️  页面未找到，可能原因:'
                        echo '- 部署文件不存在或路径不正确'
                        echo '- Nginx站点配置有误'
                        echo '- 文件权限问题'
                        echo '- 站点未正确启用'
                    } else if (response == '403') {
                        echo '⚠️  访问被禁止，可能原因:'
                        echo '- 文件权限不正确'
                        echo '- Nginx配置禁止访问'
                        echo '- SELinux策略限制'
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
            echo "🎉 ${PROJECT_DOMAIN} 部署成功！"
            script {
                // 发送简单通知（使用Jenkins内置邮件功能）
                try {
                    mail (
                        to: 'your-email@example.com',
                        subject: "✅ ${PROJECT_DOMAIN} 部署成功: ${env.JOB_NAME} - ${env.BUILD_NUMBER}",
                        body: "项目 ${env.JOB_NAME} 构建 ${env.BUILD_NUMBER} 已成功部署到阿里云服务器。\n\n" +
                              "域名访问: http://${PROJECT_DOMAIN}\n" +
                              "构建时间: ${new Date()}\n" +
                              "构建URL: ${env.BUILD_URL}"
                    )
                } catch (Exception e) {
                    echo "邮件通知发送失败: ${e.getMessage()}"
                }
            }
        }
        
        failure {
            echo "❌ ${PROJECT_DOMAIN} 部署失败！"
            script {
                // 发送失败通知
                try {
                    mail (
                        to: 'your-email@example.com',
                        subject: "❌ ${PROJECT_DOMAIN} 部署失败: ${env.JOB_NAME} - ${env.BUILD_NUMBER}",
                        body: "项目 ${env.JOB_NAME} 构建 ${env.BUILD_NUMBER} 部署失败，请查看构建日志。\n\n" +
                              "目标域名: ${PROJECT_DOMAIN}\n" +
                              "构建时间: ${new Date()}\n" +
                              "构建URL: ${env.BUILD_URL}"
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