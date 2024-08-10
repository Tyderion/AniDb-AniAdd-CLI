#!/bin/bash
if [[ -z "${LOG_CONFIG_FILE}" ]]; then
  echo "LOG_CONFIG_FILE is not set, using default logging.properties"
  export LOG_CONFIG_FILE=logging.properties
fi

if [[ -z "${FROM_FOLDER}" ]]; then
  echo "FROM_FOLDER is not set, setting to /from"
  export FROM_FOLDER=/from
fi

if [ -z "$LOCAL_CACHE_FILE" ]
then
  java --enable-preview -jar /app/aniadd-cli.jar anidb -u $ANIDB_USERNAME -p $ANIDB_PASSWORD -c $ANIDB_CONF  scan $FROM_FOLDER
else
  java --enable-preview -jar /app/aniadd-cli.jar anidb -u $ANIDB_USERNAME -p $ANIDB_PASSWORD -c $ANIDB_CONF --db $LOCAL_CACHE_FILE scan $FROM_FOLDER
fi