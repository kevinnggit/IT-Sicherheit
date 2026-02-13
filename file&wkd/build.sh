#!/bin/bash

echo "[BUILD] Erstelle Ausgabeverzeichnis bin/"
mkdir -p bin

echo "[BUILD] Kompiliere Java-Quellen..."

javac -d bin -sourcepath src src/MainServer.java src/handlers/*.java src/services/*.java

if [ $? -eq 0 ]; then
    echo "[BUILD] Erfolgreich abgeschlossen."
else
    echo "[BUILD] Fehler beim Kompilieren!"
    exit 1
fi
