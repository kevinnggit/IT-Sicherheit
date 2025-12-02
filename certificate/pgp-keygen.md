#!/bin/bash
# PGP Key Generierung unter Debian/Ubuntu

# zu root wechseln mit : su -
# Datum korrigieren mit : date -s "2025-11-21 15:48:00"
# apt update
# apt upgrade
# apt install gnupg sudo
# gpg --version

# Schlüssel generieren
# gpg --quick-gen-key "Name (Comment) <email@hs-bremerhaven.de>" ed25519 sign 1y
# gpg --quick-add-key <keyid/fingerprint> rsa4096 encr 1y
# gpg --quick-gen-key "vname Name <vname.name@smail.hs-bremerhaven.de>" rsa4096 cert,sign 2y
# gpg --list-keys
# gpg --list-keys --with-colons "vname.name@smail.hs-bremerhaven.de"

# zum löschen : gpg --delete-secret-key <FINGERPRINT>
#            gpg --delete-key <FINGERPRINT>

# Schlüssel exportieren
# gpg --export --armor vname.name@smail.hs-bremerhaven.de > public-key.asc
# gpg --export-secret-keys vname.name@smail.hs-bremerhaven.de > private-key.asc

# Da sireal nicht erreichbar ist bzw. nicht funktioniert
# simulieren wir im localen PCmit:
   gpg --quick-gen-key "IMPACT Simulator <impact.simulator@smail.hs-bremerhaven.de>"
   gpg --list-keys "IMPACT Simulator"
   gpg --import public-key.asc
   gpg --default-key "IMPACT Simulator" --sign-key "kevin.nguefackdjoukeng@smail.hs-bremerhaven.de"
   gpg --export -a "kevin.nguefackdjoukeng@smail.hs-bremerhaven.de" > public-key-signed.asc
   gpg --list-packets public-key-signed.asc | grep "kevin.nguefackdjoukeng@smail.hs-bremerhaven.de" -A 5


# um die die Schlüsseln zu tauschen und signieren, kopieren wir sie beide ins root Verzeichnis
# und jeder holt sich das von dem Partner
# su -
# cp /root/publickey.asc /home/kevnguefack/
# importieren: gpg --import public-key.asc
# gpg --fingerprint "paulevanelle.chebousen@smail-hs.bremerhaven.de"  --> prüfen, ob es der richtige Schlüssel ist
# Dann signieren : gpg --default-key "issuer name" --sign-key "vname.name@smail.hs-bremerhaven.de"
# wieder ins root verschieben
# jeder importiert wieder die signierte version seines Schlüssels
# im prüfen gpg --list-signatures kevin.nguefackdjoukeng@smail.hs-bremerhaven.de

# zum laufen: Anwendung starten mit run.sh nach build.sh
#                                                                      team-nr.example.org
# zum testen: curl -v "http://its_25_teamnr:8000/well-known/openpgpkey/smail.hs-bremerhaven.de/hu/00000000000000000000000000000000?l=vname.name" --output mein_download.asc

# Ausgabe konvertieren mit: gpg --show-keys mein_download.asc
#                           gpg --enarmor < mein_download.asc
