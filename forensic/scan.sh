#!/bin/bash

echo "Starte Scan im Netz 10.42.10.0/24..."
for i in {1..254}; do
    ip="10.42.10.$i"
    # 1. Ping Check (Ist er wach?) -W 1 heißt 1 Sekunde Timeout
    if ping -c 1 -W 1 $ip > /dev/null 2>&1; then
        echo "Host gefunden: $ip"
    fi
done


