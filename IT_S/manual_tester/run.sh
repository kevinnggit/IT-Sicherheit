#!/bin/bash

MAIN_CLASS_TH="IT_S.manual_tester.src.TesterHost"

echo "Starte Tester Host..."
echo "Argumente: $@"

java -cp bin $MAIN_CLASS_TH "$@"
