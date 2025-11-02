#!/bin/bash

MAIN_CLASS_TH="TesterHost"

echo "Starte Tester Host..."
echo "Argumente: $@"

java -cp bin $MAIN_CLASS_TH "$@"