pipeline {
  agent any

  parameters {
    string(name: 'DEPLOY_DIR', defaultValue: 'E:\\jenkins_demo_deploy', description: '静态站点部署目标目录（Windows 路径）')
    booleanParam(name: 'CLEAN_INSTALL', defaultValue: true, description: '是否使用干净安装（等同 pnpm install 的默认行为）')
  }

  options {
    ansiColor('xterm')
    timestamps()
    buildDiscarder(logRotator(numToKeepStr: '20'))
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Setup Node & pnpm') {
      steps {
        // 显示 Node/npm 版本，启用 corepack 并激活 pnpm（使用锁文件）
        bat 'node --version || ver > nul'
        bat 'npm --version || ver > nul'
        bat 'corepack enable'
        bat 'corepack prepare pnpm@9 --activate'
        bat 'pnpm --version'
      }
    }

    stage('Install') {
      steps {
        bat 'pnpm install --frozen-lockfile'
      }
    }

    stage('Build') {
      steps {
        bat 'pnpm run build'
      }
      post {
        always {
          archiveArtifacts artifacts: 'dist/**', fingerprint: true, onlyIfSuccessful: false
        }
      }
    }

    stage('Deploy') {
      when {
        expression { return fileExists('dist') }
      }
      steps {
        // 使用 PowerShell 清空并复制构建产物到部署目录
        powershell '''
          $ErrorActionPreference = "Stop"
          $dest = "$env:DEPLOY_DIR"
          if ([string]::IsNullOrWhiteSpace($dest)) { throw "DEPLOY_DIR 未设置" }
          if (!(Test-Path $dest)) { New-Item -ItemType Directory -Path $dest | Out-Null }
          if (Test-Path (Join-Path $dest '*')) { Remove-Item -Path (Join-Path $dest '*') -Recurse -Force }
          Copy-Item -Path (Join-Path 'dist' '*') -Destination $dest -Recurse -Force
        '''
      }
    }
  }

  post {
    success {
      echo '✅ 构建与部署完成'
    }
    failure {
      echo '❌ 构建或部署失败，请检查日志'
    }
    always {
      echo "Node: $(bat(returnStatus: true, script: 'node --version > nul & echo %ERRORLEVEL%') == 0 ? 'available' : 'missing')"
    }
  }
}


