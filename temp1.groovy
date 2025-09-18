pipeline {
    agent any

    stages {
        stage('#stage武汉stage#') {
            steps {
                echo '武汉'
            }
        }
        stage('#stage欢迎stage#') {
            steps {
                echo '欢迎'
            }
        }
        stage('stage#你stage#') {
            steps {
                echo '你每个班10个学生'
            }
        }
        
    }
}
