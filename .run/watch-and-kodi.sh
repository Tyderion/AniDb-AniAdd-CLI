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

if [ -z "$KODI_PORT" ]
then
  if [ -z "$LOCAL_CACHE_FILE" ]
  then
    java --enable-preview -jar /app/aniadd-cli.jar anidb -u $ANIDB_USERNAME -p $ANIDB_PASSWORD -c $ANIDB_CONF watch-and-kodi --interval $SCAN_INTERVAL --kodi=$KODI_HOST $FROM_FOLDER
  else
    java --enable-preview -jar /app/aniadd-cli.jar anidb -u $ANIDB_USERNAME -p $ANIDB_PASSWORD -c $ANIDB_CONF --db $LOCAL_CACHE_FILE watch-and-kodi --interval $SCAN_INTERVAL --kodi=$KODI_HOST $FROM_FOLDER
  fi
 else
   if [ -z "$LOCAL_CACHE_FILE" ]
   then
    java --enable-preview -jar /app/aniadd-cli.jar anidb -u $ANIDB_USERNAME -p $ANIDB_PASSWORD -c $ANIDB_CONF watch-and-kodi --interval $SCAN_INTERVAL --kodi=$KODI_HOST --port=$KODI_PORT $FROM_FOLDER
   else
     java --enable-preview -jar /app/aniadd-cli.jar anidb -u $ANIDB_USERNAME -p $ANIDB_PASSWORD -c $ANIDB_CONF --db $LOCAL_CACHE_FILE watch-and-kodi --interval $SCAN_INTERVAL --kodi=$KODI_HOST --port=$KODI_PORT $FROM_FOLDER
   fi
fi