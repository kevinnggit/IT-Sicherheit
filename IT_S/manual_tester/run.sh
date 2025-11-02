#!/bin/bash

MAIN_CLASS_TH="TesterHost"

echo "Starte Tester Host..."
echo "Argumente: $@"

java -cp bin/IT_S/manual_tester/src/ $MAIN_CLASS_TH "$@"