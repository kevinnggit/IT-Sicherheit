# TODO Liste - ETP Server

## 🎯 

* [ ] **()** Eigene Implementierung von `modpow` (Modularer Exponentiation).
    * Ersetze `ciphertext.modPow(d, N)` durch eine eigene "Square-and-Multiply"-Implementierung in `Krypto.java`.
* [ ] **()** Eigene Implementierung des Erweiterten Euklidischen Algorithmus.
    * Ersetze `e.modInverse(phi)` durch eine eigene Implementierung, die auf einem selbst geschriebenen `ggT` (Euklidischer Algorithmus) basiert.
* [ ] **()** Spoofing-Angriff demonstrieren.
    * Zeige, wie dein Client dazu gebracht werden kann, mit einem falschen Server zu reden.
* [ ] **()** Machine-in-the-Middle (MitM) Angriff.
    * Lese die Nachricht, die der Client an den Server schickt.
* [ ] **()** MitM-Verteidigungskonzept.
    * Entwickle ein Konzept, um den MitM-Angriff zu verhindern (Stichwort: Zertifikate / Public-Key-Infrastruktur).

## 🛠️ Verbesserungen (Code-Qualität)

* [ ] **Robustes Padding:** Die aktuelle "Suche nach dem ersten `0x00`-Byte" in `ClientHandler.java` ist fehleranfällig (siehe Vorzeichen-Bit von `toByteArray()`). Untersuche und implementiere eine robustere Methode, um das Null-Byte-Padding zu entfernen (z.B. von rechts nach links suchen).
* [ ] **Krypto-Klasse härten:** Füge Checks in `Krypto.java` hinzu (z.B. sicherstellen, dass `bitLength` eine gerade Zahl und > 64 ist).
* [ ] **Logging:** Ersetze `System.out.println` durch ein einfaches Logging-Framework oder schreibe Ausgaben in eine Log-Datei statt auf `stdout` (außer der entschlüsselten Nachricht, die auf `stdout` MUSS).