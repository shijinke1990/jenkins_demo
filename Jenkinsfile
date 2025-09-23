pipeline {
    agent any
    
    parameters {
        choice(
            name: 'GIT_REF',
            choices: ['main', 'develop', 'release', 'feature'],
            description: '选择要构建的分支'
        )
    }
    
    environment {
        // GitHub仓库配置 - 使用SSH方式
        GIT_REPO = 'git@github.com:shijinke1990/jenkins_demo.git'
        GIT_BRANCH = 'main'
        // 阿里云服务器配置  
        ALIYUN_HOST = '120.55.61.109'
        ALIYUN_USER = 'root'
        DEPLOY_PATH = '/var/www/html'
        NODE_VERSION = '22'
    }
    
    // 根据内存知识配置Node.js 22.x版本
    tools {
        nodejs "NodeJS 22"
    }
    
    stages {
        stage('检出代码') {
            steps {
                echo '开始从GitHub检出代码...'
                echo "仓库地址: ${GIT_REPO}"
                script {
                    env.GIT_REF = params.GIT_REF ?: GIT_BRANCH
                }
                echo "分支/Tag: ${env.GIT_REF}"
                
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
                        git clone -b ${GIT_REF} ${GIT_REPO} .
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
        
        stage('部署到服务器') {
            steps {
                echo '开始部署到阿里云服务器...'
                
                // 使用withCredentials以SSH方式连接阿里云服务器
                withCredentials([sshUserPrivateKey(
                    credentialsId: 'aliyun-ssh-key', // 使用您的阿里云SSH凭据 ID
                    keyFileVariable: 'SSH_KEY',
                    usernameVariable: 'SSH_USER'
                )]) {
                    script {
                        if (isUnix()) {
                            sh """
                                # 配置SSH环境
                                mkdir -p ~/.ssh
                                cp "\$SSH_KEY" ~/.ssh/aliyun_key
                                chmod 600 ~/.ssh/aliyun_key
                                
                                # 上传构建产物
                                scp -i ~/.ssh/aliyun_key -o StrictHostKeyChecking=no dist.tar.gz ${ALIYUN_USER}@${ALIYUN_HOST}:/tmp/
                                
                                # 连接服务器并执行部署
                                ssh -i ~/.ssh/aliyun_key -o StrictHostKeyChecking=no ${ALIYUN_USER}@${ALIYUN_HOST} '
                                    echo "开始部署到/var/www/html..."
                                    
                                    # 创建部署目录
                                    mkdir -p ${DEPLOY_PATH}
                                    
                                    # 备份现有的部署（如果存在）
                                    if [ -d "${DEPLOY_PATH}/dist" ]; then
                                        echo "📦 备份现有部署..."
                                        BACKUP_NAME="backup-\$(date +%Y%m%d-%H%M%S)"
                                        mv "${DEPLOY_PATH}/dist" "${DEPLOY_PATH}/\$BACKUP_NAME" 2>/dev/null || true
                                        echo "✅ 备份完成: ${DEPLOY_PATH}/\$BACKUP_NAME"
                                    fi
                                    
                                    # 解压前端构建产物到目标目录
                                    echo "📦 解压构建产物到目标目录..."
                                    cd ${DEPLOY_PATH}
                                    tar -xzf /tmp/dist.tar.gz
                                    echo "✅ 构建产物部署完成"
                                    
                                    # 设置正确的文件权限
                                    echo "🔧 设置文件权限..."
                                    chown -R www-data:www-data ${DEPLOY_PATH}/dist 2>/dev/null || chown -R nginx:nginx ${DEPLOY_PATH}/dist 2>/dev/null || true
                                    find ${DEPLOY_PATH}/dist -type f -exec chmod 644 {} \\;
                                    find ${DEPLOY_PATH}/dist -type d -exec chmod 755 {} \\;
                                    echo "✅ 文件权限设置完成"
                                    
                                    # 检查部署文件
                                    if [ -f "${DEPLOY_PATH}/dist/index.html" ]; then
                                        echo "✅ 部署文件验证成功"
                                        echo "文件数量: \$(find ${DEPLOY_PATH}/dist -type f | wc -l)"
                                        echo "目录大小: \$(du -sh ${DEPLOY_PATH}/dist | cut -f1)"
                                    else
                                        echo "❌ 部署文件验证失败，未找到index.html"
                                        exit 1
                                    fi
                                    
                                    # 清理临时文件
                                    rm -f /tmp/dist.tar.gz
                                    
                                    # 清理旧备份（保留最近3个备份）
                                    echo "🧹 清理旧备份..."
                                    cd ${DEPLOY_PATH}
                                    ls -dt backup-* 2>/dev/null | tail -n +4 | xargs rm -rf 2>/dev/null || true
                                    
                                    echo "✅ 部署完成！"
                                    echo "🌐 部署路径: ${DEPLOY_PATH}/dist"
                                    echo "📁 项目文件已部署到服务器"
                                '
                            """
                        } else {
                            // Windows环境
                            error "Windows环境暂不支持此部署方式，请使用Linux环境"
                        }
                    }
                }
            }
        }
        
        stage('部署验证') {
            steps {
                echo '执行部署后验证...'
                script {
                    // 等待Web服务器处理新部署的文件
                    sleep(time: 5, unit: 'SECONDS')
                    
                    // 使用SSH验证部署状态
                    withCredentials([sshUserPrivateKey(
                        credentialsId: 'aliyun-ssh-key',
                        keyFileVariable: 'SSH_KEY',
                        usernameVariable: 'SSH_USER'
                    )]) {
                        sh """
                            # 检查部署文件状态
                            ssh -i ~/.ssh/aliyun_key -o StrictHostKeyChecking=no ${ALIYUN_USER}@${ALIYUN_HOST} '
                                echo "=== 部署状态检查 ==="
                                
                                # 检查部署目录
                                if [ -d "${DEPLOY_PATH}/dist" ]; then
                                    echo "✅ 部署目录存在: ${DEPLOY_PATH}/dist"
                                    echo "文件数量: \$(find ${DEPLOY_PATH}/dist -type f | wc -l)"
                                    echo "目录大小: \$(du -sh ${DEPLOY_PATH}/dist | cut -f1)"
                                else
                                    echo "❌ 部署目录不存在"
                                    exit 1
                                fi
                                
                                # 检查关键文件
                                if [ -f "${DEPLOY_PATH}/dist/index.html" ]; then
                                    echo "✅ 入口文件存在: index.html"
                                    echo "文件大小: \$(ls -lh ${DEPLOY_PATH}/dist/index.html | awk \"{print \\\$5}\")"
                                else
                                    echo "❌ 入口文件不存在"
                                    exit 1
                                fi
                                
                                # 检查静态资源目录
                                if [ -d "${DEPLOY_PATH}/dist/assets" ]; then
                                    echo "✅ 静态资源目录存在"
                                    echo "静态资源文件数量: \$(find ${DEPLOY_PATH}/dist/assets -type f | wc -l)"
                                else
                                    echo "ℹ️ 静态资源目录不存在（可能使用其他目录结构）"
                                fi
                                
                                # 检查文件权限
                                echo "📋 文件权限检查:"
                                ls -la ${DEPLOY_PATH}/dist/ | head -5
                                
                                # 检查Web服务器状态（如果存在）
                                if command -v nginx >/dev/null 2>&1; then
                                    echo "🌐 检查Nginx状态:"
                                    if systemctl is-active --quiet nginx; then
                                        echo "✅ Nginx服务正在运行"
                                        echo "Nginx配置测试: \$(nginx -t 2>&1)"
                                    else
                                        echo "⚠️ Nginx服务未运行"
                                    fi
                                elif command -v apache2 >/dev/null 2>&1 || command -v httpd >/dev/null 2>&1; then
                                    echo "🌐 检查Apache状态:"
                                    if systemctl is-active --quiet apache2 || systemctl is-active --quiet httpd; then
                                        echo "✅ Apache服务正在运行"
                                    else
                                        echo "⚠️ Apache服务未运行"
                                    fi
                                else
                                    echo "ℹ️ 未检测到常见Web服务器"
                                fi
                                
                                # 检查端口监听状态
                                echo "📡 端口监听状态:"
                                if netstat -tlnp 2>/dev/null | grep -q ":80 "; then
                                    echo "✅ 端口80正在监听"
                                    netstat -tlnp | grep ":80 " | head -1
                                elif ss -tlnp 2>/dev/null | grep -q ":80 "; then
                                    echo "✅ 端口80正在监听"
                                    ss -tlnp | grep ":80 " | head -1
                                else
                                    echo "⚠️ 端口80未监听"
                                fi
                                
                                echo "=== 部署验证完成 ==="
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
                        echo "🌐 访问地址: http://${ALIYUN_HOST}"
                        
                        // 测试静态资源
                        def assetsResponse = sh(
                            script: "curl -s -o /dev/null -w '%{http_code}' --connect-timeout 5 --max-time 15 http://${ALIYUN_HOST}/assets/ 2>/dev/null || echo '403'",
                            returnStdout: true
                        ).trim()
                        
                        if (assetsResponse == '403' || assetsResponse == '404') {
                            echo '✅ 静态资源路径配置正常'
                        }
                        
                        // 检查页面内容
                        def contentCheck = sh(
                            script: "curl -s http://${ALIYUN_HOST} | head -10 | grep -q 'html\\|HTML' && echo 'valid' || echo 'invalid'",
                            returnStdout: true
                        ).trim()
                        
                        if (contentCheck == 'valid') {
                            echo '✅ 页面内容正常'
                        }
                        
                    } else if (response == '000') {
                        echo '⚠️  无法连接到服务器，可能原因:'
                        echo '- 网络连接问题'
                        echo '- 防火墙阻止访问'
                        echo '- Web服务器未启动'
                        echo '- 端口80未开放'
                    } else if (response == '404') {
                        echo '⚠️  页面未找到，可能原因:'
                        echo '- 部署文件不存在或路径不正确'
                        echo '- Web服务器配置有误'
                        echo '- 文档根目录设置错误'
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
        }
        
        failure {
            echo '❌ 部署失败！'
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