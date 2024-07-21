#!/bin/bash
echo "setting up execution"
if [[ -z "${LOG_CONFIG_FILE}" ]]; then
  export LOG_CONFIG_FILE=logging.properties
fi

if [[ -z "${FROM_FOLDER}" ]]; then
  echo "FROM_FOLDER is not set, setting to /from"
  export $FROM_FOLDER=/from
fi

java --enable-preview -jar /app/aniadd-cli.jar anidb -u $ANIDB_USERNAME -p $ANIDB_PASSWORD -c $ANIDB_CONF watch $FROM_FOLDER