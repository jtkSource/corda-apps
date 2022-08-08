#!/usr/bin/env bash
all_args=("$@")
NODE="$1"
CONFIG_PATH="/mnt/linux-data/data/git/corda-apps/bond-issuance/config/dev"
java -jar build/libs/cordapp-client-1.0-all.jar "${CONFIG_PATH}" "${NODE}"