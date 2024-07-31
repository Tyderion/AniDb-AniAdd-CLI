#!/bin/bash
if [[ -z "${LOG_CONFIG_FILE}" ]]; then
  echo "LOG_CONFIG_FILE is not set, using default logging.properties"
  export LOG_CONFIG_FILE=logging.properties
fi

if [[ -z "${FROM_FOLDER}" ]]; then
  echo "FROM_FOLDER is not set, setting to /from"
  export FROM_FOLDER=/from
fi

if [[ -z "${SCAN_INTERVAL}" ]]; then
  echo "SCAN_INTERVAL is not set, setting to 30"
  export SCAN_INTERVAL=30
fi

java --enable-preview -jar /app/aniadd-cli.jar anidb -u $ANIDB_USERNAME -p $ANIDB_PASSWORD -c $ANIDB_CONF watch --interval $SCAN_INTERVAL $FROM_FOLDER