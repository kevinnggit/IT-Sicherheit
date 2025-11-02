#!/bin/bash

mkdir -p bin

javac -d bin src/*.java

if [ $? -eq 0 ]; then
    echo "Tester-Build erfolgreich."
else
    echo "Tester-Build fehlgeschlagen."
    exit 1
fi
