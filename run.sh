#!/bin/sh

ls /shows
ls /from

java -jar /app/aniadd-cli.jar --no-gui -u $ANIDB_USERNAME -p $ANIDB_PASSWORD -c $ANIDB_CONF -d /from

#mv "/from/file.mkv" "/shows/NieR Automata Ver1.1a/NieR Automata Ver1.1a - 01v2 - Or Not to [B]e [Hi10][www][1920x1080][HEVC][F64B59F4].mkv"

cat rename.sh

while true; do
  sleep 200
done