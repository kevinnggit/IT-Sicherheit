#!/bin/bash

mkdir -p bin

javac -d bin src/*.java

if [ $? -eq 0 ]; then
    echo "Build erfolgreich."
else
    echo "Build fehlgeschlagen."
    exit 1
fi