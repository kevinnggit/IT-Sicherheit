#!/bin/bash

TEAM_NR="12"

if [ "$TEAM_NR" != "12" ]; then
    echo "[Fehler] Bitte passe die TEAM_NR Variable in install.sh an."
    exit 1
fi


HOST_IP=$1
ARCHIVE_NAME=team${TEAM_NR}-etp.tar.gz

tar -czf "$ARCHIVE_NAME" --exclude='./bin' --exclude='./IT_S' --exclude='./tmp' .

scp "$ARCHIVE_NAME" "root@$HOST_IP:"      # ~/

if [ $? -ne 0 ]; then
    echo "[Fehler] Dateiübertragung fehlgeschlagen."
    exit 1
fi

echo "[Info] Dateiübertragung erfolgreich."