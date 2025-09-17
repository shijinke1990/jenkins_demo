pipeline {
    agent any
    
    environment {
        // 阿里云服务器配置
        ALIYUN_HOST = '你的阿里云服务器IP'
        ALIYUN_USER = 'root'  // 或其他用户名
        DEPLOY_PATH = '/var/www/html'  // 部署目录
        NODE_VERSION = '20'  // Node.js版本
    }
    
    tools {
        nodejs "${NODE_VERSION}"
    }
    
    stages {
        stage('检出代码') {
            steps {
                echo '开始检出代码...'
                checkout scm
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
                    if (isUnix()) {
                        sh 'pnpm lint'
                    } else {
                        bat 'pnpm lint'
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
            }
        }
        
        stage('打包构建产物') {
            steps {
                echo '打包构建产物...'
                script {
                    if (isUnix()) {
                        sh 'tar -czf dist.tar.gz dist/'
                    } else {
                        // Windows环境使用PowerShell压缩
                        powershell 'Compress-Archive -Path .\\dist\\* -DestinationPath dist.zip -Force'
                    }
                }
            }
        }
        
        stage('部署到阿里云') {
            steps {
                echo '开始部署到阿里云服务器...'
                script {
                    // 使用SSH凭据部署
                    sshagent(['aliyun-ssh-key']) {
                        if (isUnix()) {
                            sh """
                                # 上传构建产物
                                scp -o StrictHostKeyChecking=no dist.tar.gz ${ALIYUN_USER}@${ALIYUN_HOST}:/tmp/
                                
                                # 连接服务器并部署
                                ssh -o StrictHostKeyChecking=no ${ALIYUN_USER}@${ALIYUN_HOST} '
                                    # 备份旧版本
                                    if [ -d ${DEPLOY_PATH}_backup ]; then
                                        rm -rf ${DEPLOY_PATH}_backup
                                    fi
                                    if [ -d ${DEPLOY_PATH} ]; then
                                        mv ${DEPLOY_PATH} ${DEPLOY_PATH}_backup
                                    fi
                                    
                                    # 创建部署目录
                                    mkdir -p ${DEPLOY_PATH}
                                    
                                    # 解压新版本
                                    cd ${DEPLOY_PATH}
                                    tar -xzf /tmp/dist.tar.gz --strip-components=1
                                    
                                    # 设置权限
                                    chown -R www-data:www-data ${DEPLOY_PATH}
                                    chmod -R 755 ${DEPLOY_PATH}
                                    
                                    # 重启Nginx（如果使用）
                                    systemctl reload nginx
                                    
                                    # 清理临时文件
                                    rm -f /tmp/dist.tar.gz
                                '
                            """
                        } else {
                            // Windows环境使用pscp和plink
                            bat """
                                pscp -i "C:\\path\\to\\private\\key.ppk" -scp dist.zip ${ALIYUN_USER}@${ALIYUN_HOST}:/tmp/
                                plink -i "C:\\path\\to\\private\\key.ppk" ${ALIYUN_USER}@${ALIYUN_HOST} "cd ${DEPLOY_PATH} && unzip -o /tmp/dist.zip && rm /tmp/dist.zip"
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
                    // 检查网站是否正常访问
                    def response = sh(
                        script: "curl -s -o /dev/null -w '%{http_code}' http://${ALIYUN_HOST}",
                        returnStdout: true
                    ).trim()
                    
                    if (response == '200') {
                        echo '网站部署成功，访问正常！'
                    } else {
                        error "网站访问异常，HTTP状态码: ${response}"
                    }
                }
            }
        }
    }
    
    post {
        success {
            echo '部署成功！'
            // 可以发送成功通知
            emailext (
                subject: "部署成功: ${env.JOB_NAME} - ${env.BUILD_NUMBER}",
                body: "项目 ${env.JOB_NAME} 构建 ${env.BUILD_NUMBER} 已成功部署到阿里云服务器。",
                to: "your-email@example.com"
            )
        }
        
        failure {
            echo '部署失败！'
            // 可以发送失败通知
            emailext (
                subject: "部署失败: ${env.JOB_NAME} - ${env.BUILD_NUMBER}",
                body: "项目 ${env.JOB_NAME} 构建 ${env.BUILD_NUMBER} 部署失败，请查看构建日志。",
                to: "your-email@example.com"
            )
        }
        
        always {
            echo '清理工作空间...'
            cleanWs()
        }
    }
}