#!/bin/bash
if [[ -z "${LOG_CONFIG_FILE}" ]]; then
  echo "LOG_CONFIG_FILE is not set, using default /app/logging.properties"
  export LOG_CONFIG_FILE=/app/logging.properties
fi

if [[ -z "${FROM_FOLDER}" ]]; then
  echo "FROM_FOLDER is not set, setting to /from"
  export FROM_FOLDER=/from
fi

if [[ -z "${SCAN_INTERVAL}" ]]; then
  echo "SCAN_INTERVAL is not set, setting to 30"
  export SCAN_INTERVAL=30
fi

args=(anidb watch -u "$ANIDB_USERNAME" -p "$ANIDB_PASSWORD" -c "$ANIDB_CONF" --kodi --interval "$SCAN_INTERVAL")
[[ -n "$LOCAL_CACHE_FILE" ]] && args+=(--db "$LOCAL_CACHE_FILE")
[[ -n "$KODI_HOST" ]] && args+=(--kodi-host "$KODI_HOST")
[[ -n "$KODI_PORT" ]] && args+=(--kodi-port "$KODI_PORT")

java --enable-preview -jar /app/aniadd-cli.jar "${args[@]}" "$FROM_FOLDER"
