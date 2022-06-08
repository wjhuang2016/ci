/*
* @ARCH_ARM
* @ARCH_X86
* @ARCH_MAC
* @ARCH_MAC_ARM

* @FORCE_REBUILD 是否需要强制重新构建（false 则按照hash检查文件服务器上是否存在对应二进制，存在则不构建）
* @RELEASE_BRANCH 预发布分支，所有构建代码基于这个分支拉取
* @RELEASE_TAG
* @TIDB_PRM_ISSUE 默认为空，当填写了 issue id 的时候，sre-bot 会自动更新各组件 hash 到 issue 上

* @TIUP_MIRRORS
* @TIKV_BUMPVERION_HASH
* @TIKV_BUMPVERSION_PRID
*/


tidb_sha1 = ""
tikv_sha1 = ""
pd_sha1 = ""
tiflash_sha1 = ""
br_sha1 = ""
binlog_sha1 = ""
lightning_sha1 = ""
tools_sha1 = ""
cdc_sha1 = ""
dm_sha1 = ""
dumpling_sha1 = ""
ng_monitoring_sha1 = ""
tidb_ctl_githash = ""
enterprise_plugin_sha1 = ""

def OS_LINUX = "linux"
def OS_DARWIN = "darwin"
def ARM64 = "arm64"
def AMD64 = "amd64"
def PLATFORM_CENTOS = "centos7"
def PLATFORM_DARWIN = "darwin"
def PLATFORM_DARWINARM = "darwin-arm64"

label = "${JOB_NAME}-${BUILD_NUMBER}"

def run_with_pod(Closure body) {
    def cloud = "kubernetes"
    def namespace = "jenkins-cd"
    def pod_go_docker_image = 'hub.pingcap.net/jenkins/centos7_golang-1.18:add_tool_yajl'
    def jnlp_docker_image = "jenkins/inbound-agent:4.3-4"
    podTemplate(label: label,
            cloud: cloud,
            namespace: namespace,
            idleMinutes: 0,
            containers: [
                    containerTemplate(
                            name: 'golang', alwaysPullImage: false,
                            image: "${pod_go_docker_image}", ttyEnabled: true,
                            resourceRequestCpu: '8000m', resourceRequestMemory: '12Gi',
                            command: '/bin/sh -c', args: 'cat',
                            envVars: [containerEnvVar(key: 'GOPATH', value: '/go')],

                    )
            ],
            volumes: [
                    emptyDirVolume(mountPath: '/tmp', memory: false),
                    emptyDirVolume(mountPath: '/home/jenkins', memory: false)
            ],
    ) {
        node(label) {
            println "debug command:\nkubectl -n ${namespace} exec -ti ${NODE_NAME} bash"
            body()
        }
    }
}

run_with_pod {
    container("golang") {
        stage('generate tags') {
            steps {
                script {
                    def component = "br"
                    def cmd = "curl -L -s https://registry.hub.docker.com/v1/repositories/pingcap/$component/tags | json_reformat | grep -i name | awk '{print \$2}' | sed 's/\"//g' | sort -u"
                    def tags = sh(returnStdout: true, script: "$cmd").trim()
                    println "$tags"
                }
            }
        }

        stage('Build') {
            builds = [:]
            def count = 0

            for (tag in "$tags") {
                count += 1
                echo "正在处理第${count}个版本：${tag}"
                build job: "jenkins-images-sync", wait: true,
                        parameters: [
                                [$class: 'StringParameterValue', name: 'SOURCE_IMAGE', value: "$SOURCE_IMAGE" + "$tag"],
                                [$class: 'StringParameterValue', name: 'TARGET_IMAGE', value: "$TARGET_IMAGE" + "$tag"],
                        ]
            }
        }
    }
}


