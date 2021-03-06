#!/usr/bin/env bash

set -e

SCRIPT_DIR=$(dirname "${BASH_SOURCE[0]}")
PROJECT_DIR=$(dirname "${SCRIPT_DIR}")
cd "${PROJECT_DIR}" || exit 1

# lint all scripts, abort if there's any warning.
function shellcheck_must_pass()
{
    if [[ $(shellcheck "$1") ]]; then
        echo "shellcheck $1 failed"
        shellcheck "$1"
        exit 1
    fi
}
shellcheck_must_pass ./scripts/format-all.sh
shellcheck_must_pass ./scripts/travis.sh

# ensure source files are well formatted
./scripts/format-all.sh
if [[ $(git status -s) ]]; then
    git status -s
    echo "please format the above files before commit"
    exit 1
fi

# The new version of pegasus client is not compatible with old version server which contains old rpc protocol,
# So we use snapshot version of pegasus-tools, because we don`t have a new release version, which contains the new version of rpc protocol,
PEGASUS_PKG="pegasus-tools-1.13.SNAPSHOT-695b366-glibc2.17-release"
PEGASUS_PKG_URL="https://github.com/XiaoMi/pegasus-common/releases/download/deps/pegasus-tools-1.13.SNAPSHOT-695b366-glibc2.17-release.tar.gz"

# start pegasus onebox environment
if [ ! -f $PEGASUS_PKG.tar.gz ]; then
    wget $PEGASUS_PKG_URL
    tar xvf $PEGASUS_PKG.tar.gz
fi
cd $PEGASUS_PKG

sed -i "s#https://github.com/xiaomi/pegasus-common/raw/master/zookeeper-3.4.6.tar.gz#https://github.com/XiaoMi/pegasus-common/releases/download/deps/zookeeper-3.4.6.tar.gz#" scripts/start_zk.sh
./run.sh start_onebox -w
cd ../

if ! mvn clean test
then
    cd $PEGASUS_PKG
    ./run.sh list_onebox
    exit 1
fi
