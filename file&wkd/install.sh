#!/bin/bash

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}--- Team 12: Installation & Start ---${NC}"

# 1. Prüfen, ob src existiert
if [ -d "src" ]; then
    echo "[OK] 'src' Ordner gefunden."
else
    echo -e "${RED}[FEHLER] 'src' Ordner nicht gefunden! Ich kann nichts bauen.${NC}"
    exit 1
fi

# 2. Prüfen, ob bin existiert, sonst erstellen
if [ -d "bin" ]; then
    echo "[OK] 'bin' Ordner existiert bereits."
else
    echo "[INFO] 'bin' Ordner fehlt. Erstelle ihn..."
    mkdir bin
fi

# 3. Build-Skript ausführen
if [ -f "build.sh" ]; then
    echo -e "\n${GREEN}--- Starte Kompilierung (build.sh) ---${NC}"
    chmod +x build.sh
    ./build.sh
    
    # Prüfen, ob Build erfolgreich war
    if [ $? -ne 0 ]; then
        echo -e "${RED}[FEHLER] Kompilierung fehlgeschlagen. Abbruch.${NC}"
        exit 1
    fi
else
    echo -e "${RED}[FEHLER] 'build.sh' nicht gefunden!${NC}"
    exit 1
fi

# 4. Run ausführen
if [ -f "run.sh" ]; then
    echo -e "\n${GREEN}--- Starte Server (run.sh) ---${NC}"
    echo "[INFO] Server benötigt Root-Rechte für Port 80 (Passwort ggf. eingeben):"
    chmod +x run.sh
    ./run.sh
else
    echo -e "${RED}[FEHLER] 'run.sh' nicht gefunden!${NC}"
    exit 1
fi

