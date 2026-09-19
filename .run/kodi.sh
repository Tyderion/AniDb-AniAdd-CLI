#!/bin/bash
if [[ -z "${LOG_CONFIG_FILE}" ]]; then
  echo "LOG_CONFIG_FILE is not set, using default /app/logging.properties"
  export LOG_CONFIG_FILE=/app/logging.properties
fi

args=(anidb connect-to-kodi -u "$ANIDB_USERNAME" -p "$ANIDB_PASSWORD" -c "$ANIDB_CONF")
[[ -n "$LOCAL_CACHE_FILE" ]] && args+=(--db "$LOCAL_CACHE_FILE")
[[ -n "$KODI_HOST" ]] && args+=(--kodi-host "$KODI_HOST")
[[ -n "$KODI_PORT" ]] && args+=(--kodi-port "$KODI_PORT")

java --enable-preview -jar /app/aniadd-cli.jar "${args[@]}"
