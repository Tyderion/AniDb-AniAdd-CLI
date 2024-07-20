#!/bin/bash
if [[ -z "${LOG_CONFIG_FILE}" ]]; then
  export LOG_CONFIG_FILE=logging.properties
fi
echo "Logging file is set to $LOG_CONFIG_FILE"
while true; do
  echo "Noop"
  sleep 60
done