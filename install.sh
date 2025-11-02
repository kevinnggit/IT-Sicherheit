#!/bin/bash

TEAM_NR="12"
HOST_IP=$1

PROJECT_DIR_NAME="IT-Sicherheit"
TARGET_DIR_NAME="team${TEAM_NR}"
ARCHIVE_NAME="${TARGET_DIR_NAME}-etp.tar.gz"

# Host-IP als Argument übergeben ?
if [ -z "$HOST_IP" ]; then
    echo "[Fehler] Du musst die IP-Adresse des Zielhosts als Argument angeben."
    echo "Beispiel: ./install.sh 10.42.1.101"
    exit 1
fi

echo "[Info] Benenne '${PROJECT_DIR_NAME}' temporär um zu '${TARGET_DIR_NAME}'..."
mv "$PROJECT_DIR_NAME" "$TARGET_DIR_NAME"

# Erstelle das Archiv mit dem umbenannten Ordner
tar -czf "$ARCHIVE_NAME" \
    --exclude="${TARGET_DIR_NAME}/bin" \
    --exclude="${TARGET_DIR_NAME}/IT_S" \
    "$TARGET_DIR_NAME"

echo "[Info] Benenne '${TARGET_DIR_NAME}' zurück zu '${PROJECT_DIR_NAME}'..."
mv "$TARGET_DIR_NAME" "$PROJECT_DIR_NAME"

scp "$ARCHIVE_NAME" "root@$HOST_IP:"

if [ $? -ne 0 ]; then
    echo "[Fehler] Dateiübertragung fehlgeschlagen."
    rm "$ARCHIVE_NAME" # Räume lokales Archiv auf
    exit 1
fi

echo "[Info] Dateiübertragung erfolgreich."
# rm "$ARCHIVE_NAME"


echo "[Info] Entpacke Archiv auf dem Zielhost unter /test/..."

ssh root@$HOST_IP "mkdir -p /root/test && tar -xvzf /root/${ARCHIVE_NAME} -C /root/test"

if [ $? -eq 0 ]; then
    echo "[Info] Archiv wurde erfolgreich auf dem Zielhost entpackt."
else
    echo "[Fehler] Entpacken auf dem Zielhost fehlgeschlagen."
fi


echo "--- Fertig ---"