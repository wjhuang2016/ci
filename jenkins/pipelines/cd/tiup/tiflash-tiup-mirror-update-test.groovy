def tiup_desc = ""

def tiflash_sha1, tarball_name, dir_name

def download = { name, version, os, arch ->
    if (os == "linux") {
        platform = "centos7"
    } else if (os == "darwin" && arch == "amd64") {
        platform = "darwin"
    } else if (os == "darwin" && arch == "arm64") {
        platform = "darwin-arm64"
    }  else {
        sh """
        exit 1
        """
    }

    tarball_name = "${name}-${os}-${arch}.tar.gz"

    sh """
    wget ${FILE_SERVER_URL}/download/builds/pingcap/${name}/optimization/${tag}/${tiflash_sha1}/${platform}/${tarball_name}
    """
}

def unpack = { name, version, os, arch ->
    tarball_name = "${name}-${os}-${arch}.tar.gz"

    sh """
    tar -zxf ${tarball_name}
    """
}

def pack = { name, version, os, arch ->
    sh """
    tiup package tiflash --name=${name} --release=${version} --entry=${name}/${name} --os=${os} --arch=${arch} --desc="The TiFlash Columnar Storage Engine" --hide
    tiup mirror publish ${name} ${TIDB_VERSION} package/${name}-${version}-${os}-${arch}.tar.gz ${name}/${name} --arch ${arch} --os ${os} --desc="The TiFlash Columnar Storage Engine"
    rm -rf tiflash tiflash*.tar.gz
    """
}

def update = { name, version, os, arch ->
//    try {
    download name, version, os, arch
    unpack name, version, os, arch
    pack name, version, os, arch
//    } catch (e) {
//        echo "update ${name}-${version}-${os}-${arch}: ${e}"
//        currentBuild.result = "FAILURE"
//    }
}

node("build_go1130") {
    container("golang") {
        stage("Prepare") {
            println "debug command:\nkubectl -n jenkins-ci exec -ti ${NODE_NAME} bash"
            deleteDir()
        }

        checkout scm
        def util = load "jenkins/pipelines/cd/tiup/tiup_utils.groovy"

        stage("Install tiup") {
            util.install_tiup "/usr/local/bin", PINGCAP_PRIV_KEY
        }

        if (RELEASE_TAG == "nightly" || RELEASE_TAG >= "v3.1") {
            stage("Get hash") {
                sh "curl -s ${FILE_SERVER_URL}/download/builds/pingcap/ee/gethash.py > gethash.py"

                tag = RELEASE_TAG
                if(ORIGIN_TAG != "") {
                    tiflash_sha1 = ORIGIN_TAG
                } else {
                    tiflash_sha1 = sh(returnStdout: true, script: "python gethash.py -repo=tics -version=${RELEASE_TAG} -s=${FILE_SERVER_URL}").trim()
                }
            }


            if (params.ARCH_X86) {
                stage("tiup release tiflash linux amd64") {
                    update "tiflash", RELEASE_TAG, "linux", "amd64"
                }
            }
            if (params.ARCH_ARM) {
                stage("tiup release tiflash linux arm64") {
                    update "tiflash", RELEASE_TAG, "linux", "arm64"
                }
            }
            if (params.ARCH_MAC) {
                stage("tiup release tiflash darwin amd64") {
                    update "tiflash", RELEASE_TAG, "darwin", "amd64"
                }
            }
            if (params.ARCH_MAC_ARM && RELEASE_TAG >="v5.1.0") {
                stage("tiup release tiflash darwin arm64") {
                    update "tiflash", RELEASE_TAG, "darwin", "arm64"
                }
            }

            // upload "package"
        }
    }
}