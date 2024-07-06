#!/bin/sh
echo "setting up execution"

if [ "$CONTINUOUS_CHECKS" = true ]; then
   if [ -z ${CHECK_WAITTIME+x} ] || [ "$CHECK_WAITTIME" -lt 600 ]; then
     echo "Configured wait check is smaller than 600s, setting to 600s"
     CHECK_WAITTIME=600
   else
     echo "Waiting for $CHECK_WAITTIME seconds between each run"
   fi
 else
   echo "Will only execute once"
 fi

if [ -z "$ANIDB_USERNAME" ]
then
     echo "ANIDB_USERNAME is not set"
     exit 1
fi

if [ -z "$ANIDB_PASSWORD" ]
then
     echo "ANIDB_PASSWORD is not set"
     exit 1
fi

while true; do
  # check if the /from folder contains any files
  has_files=$(find /from -type f)
  if [ -n "${has_files}" ]; then
      echo "Will check files in folder"
      touch rename.sh
      chmod a+x rename.sh

      java --enable-preview -jar /app/aniadd-cli.jar anidb -u $ANIDB_USERNAME -p $ANIDB_PASSWORD -c $ANIDB_CONF scan /from

      echo "will run following commands: "
      cat rename.sh

      echo "-----------------------"
      echo "running commands"
      ./rename.sh
      rm rename.sh

      echo "Deleting all empty directories"
      find /from/* -type d -empty -delete
  else
    echo "No files to check"
  fi

  if [ "$CONTINUOUS_CHECKS" != true ]; then
    echo "Finished run."
    exit 0
  fi
  echo "Waiting for ${CHECK_WAITTIME}s"
  sleep $CHECK_WAITTIME
done