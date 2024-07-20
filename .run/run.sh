#!/bin/bash
if [[ -z "${LOG_CONFIG_FILE}" ]]; then
  export LOG_CONFIG_FILE=logging.properties
fi

echo "Logging config is set to $LOG_CONFIG_FILE"
echo "Running command: java --enable-preview -jar /app/aniadd-cli.jar" "$COMMAND"
java --enable-preview -jar /app/aniadd-cli.jar "$COMMAND"
./noop.sh # Keep the container running for debugging