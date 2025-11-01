#!/bin/bash

TEAM_NR="12"
HOST_IP=$1

CURRENT_DIR_NAME="IT-SICHERHEIT"
TARGET_DIR_NAME="team${TEAM_NR}"
ARCHIVE_NAME="${TARGET_DIR_NAME}-etp.tar.gz"


if [ "$TEAM_NR" != "12" ]; then
    echo "[Fehler] Bitte passe die TEAM_NR Variable in install.sh an."
    exit 1
fi

cd ..

mv "$CURRENT_DIR_NAME" "$TARGET_DIR_NAME"

tar -czf "$ARCHIVE_NAME" --exclude='./bin' --exclude='./IT_S' "$TARGET_DIR_NAME"

mv "$TARGET_DIR_NAME" "$CURRENT_DIR_NAME"

scp "$ARCHIVE_NAME" "root@$HOST_IP:"      # ~/

if [ $? -ne 0 ]; then
    echo "[Fehler] Dateiübertragung fehlgeschlagen."
    rm "$ARCHIVE_NAME"
    cd "$CURRENT_DIR_NAME"
    exit 1
fi

echo "[Info] Dateiübertragung erfolgreich."
#rm "$ARCHIVE_NAME"
cd "$CURRENT_DIR_NAME"