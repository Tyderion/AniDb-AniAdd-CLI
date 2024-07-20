#!/bin/sh
echo "setting up execution"

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

if [ -z "$KODI_HOST" ]
then
     echo "KODI_HOST is not set"
     exit 1
fi

if [ -z "$KODI_PORT" ]
then
  java --enable-preview -jar /app/aniadd-cli.jar anidb -u $ANIDB_USERNAME -p $ANIDB_PASSWORD -c $ANIDB_CONF connect-to-kodi --kodi=$KODI_HOST
 else
   java --enable-preview -jar /app/aniadd-cli.jar anidb -u $ANIDB_USERNAME -p $ANIDB_PASSWORD -c $ANIDB_CONF connect-to-kodi --kodi=$KODI_HOST --port=$KODI_PORT
fi