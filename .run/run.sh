#!/bin/bash
echo "${@:2}"
if [[ -z "${LOG_CONFIG_FILE}" ]]; then
  export LOG_CONFIG_FILE=logging.properties
fi
#java --enable-preview -jar /app/aniadd-cli.jar "${@:2}"