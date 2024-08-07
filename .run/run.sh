#!/bin/bash
if [[ -z "${LOG_CONFIG_FILE}" ]]; then
  echo "LOG_CONFIG_FILE is not set, using default logging.properties"
  export LOG_CONFIG_FILE=logging.properties
fi

echo "Logging config is set to $LOG_CONFIG_FILE"
echo "Command will be read from \$COMMAND"
echo "Running command: java --enable-preview -jar /app/aniadd-cli.jar" "$COMMAND"
java --enable-preview -jar /app/aniadd-cli.jar "$COMMAND"