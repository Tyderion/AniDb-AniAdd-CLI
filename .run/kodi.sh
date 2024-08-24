#!/bin/bash
if [[ -z "${LOG_CONFIG_FILE}" ]]; then
  echo "LOG_CONFIG_FILE is not set, using default logging.properties"
  export LOG_CONFIG_FILE=logging.properties
fi

if [ -z "$KODI_PORT" ]
then
  if [ -z "$LOCAL_CACHE_FILE" ]
  then
    java --enable-preview -jar /app/aniadd-cli.jar anidb connect-to-kodi -u $ANIDB_USERNAME -p $ANIDB_PASSWORD -c $ANIDB_CONF --kodi=$KODI_HOST
  else
    java --enable-preview -jar /app/aniadd-cli.jar anidb connect-to-kodi -u $ANIDB_USERNAME -p $ANIDB_PASSWORD -c $ANIDB_CONF --db $LOCAL_CACHE_FILE --kodi=$KODI_HOST
  fi
 else
   if [ -z "$LOCAL_CACHE_FILE" ]
   then
    java --enable-preview -jar /app/aniadd-cli.jar anidb connect-to-kodi -u $ANIDB_USERNAME -p $ANIDB_PASSWORD -c $ANIDB_CONF  --kodi=$KODI_HOST --port=$KODI_PORT
   else
    java --enable-preview -jar /app/aniadd-cli.jar anidb connect-to-kodi -u $ANIDB_USERNAME -p $ANIDB_PASSWORD -c $ANIDB_CONF --db $LOCAL_CACHE_FILE --kodi=$KODI_HOST --port=$KODI_PORT
   fi
fi