# ----------------------------------------------------------------------------------------------------------
#                                        Labortagebuch Aufgabe 04
# ----------------------------------------------------------------------------------------------------------


## Phase 1: Anomalien im Studierenden-Netz (10.42.2.0/24)
## ----------------------------------------------------------------------------------------------------------

1.1 - Verdächtige host finden und ihr verhalten dokumentieren

### 2025-12-21T17:02+01 beide
* Das Netzwerk-interface finden
ip a

1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536 qdisc noqueue state UNKNOWN group default qlen 1000
    link/loopback 00:00:00:00:00:00 brd 00:00:00:00:00:00
    inet 127.0.0.1/8 scope host lo
       valid_lft forever preferred_lft forever
    inet6 ::1/128 scope host noprefixroute 
       valid_lft forever preferred_lft forever
2: br_its_25: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc fq_codel state UP group default qlen 1000
    link/ether 52:54:00:b7:c1:18 brd ff:ff:ff:ff:ff:ff
    altname enp1s0
    inet 10.42.2.112/24 brd 10.42.2.255 scope global br_its_25
       valid_lft forever preferred_lft forever
    inet6 fe80::5054:ff:feb7:c118/64 scope link 
       valid_lft forever preferred_lft forever
3: enp7s0: <BROADCAST,MULTICAST> mtu 1500 qdisc noop state DOWN group default qlen 1000
    link/ether 52:54:00:60:78:30 brd ff:ff:ff:ff:ff:ff

* tcpdump hat nicht funktioniert
root@its25team12:~/team12# tcpdump -i br_its_25 -n icmp
-bash: tcpdump: command not found

### 2025-12-21T17:13+01 pauchebousen
packete manuel runtergeladen und ins host kopieren

* in der VM:
wget http://ftp.de.debian.org/debian/pool/main/libp/libpcap/libpcap0.8_1.10.3-1_amd64.deb
wget http://ftp.de.debian.org/debian/pool/main/t/tcpdump/tcpdump_4.99.3-1_amd64.deb

* ins host:
pauchebousen@debian:~$
  $ scp *.deb pauchebousen@10.42.2.112:/home/pauchebousen/
  libpcap0.8_1.10.3-1_amd64.deb                                                                                                                                                                               100%  153KB 778.2KB/s   00:00    
  tcpdump_4.99.3-1_amd64.deb   
- packete sind auf der team host und müssen entpackt werden

### 2025-12-21T17:22+01 kevnguefack
- tcpdump manuel installieren (entpacken)
  root@its25team12:~# dpkg -i libpcap0.8_1.10.3-1_amd64.deb
- nun ist tcpdump installiert und bereit

* Dann abhören
root@its25team12:~# tcpdump -i br_its_25 -n
- output ist der TCP-Datenstrom (Request-Response-Pattern)
- der output muss analysiert werden, um die verdächtigen host zu finden
  und ein Auszug muss für die Abgabe gespeichert werden.

### 2025-12-21T17:30+01 kevnguefack
- tcpdump ausgeführt,
- Verdächtige Hosts 10.42.2.131 und .135 identifiziert.
  Verdächtiger Traffic auf UDP 31337 festgestellt.
  tcpdump-Auszug erstellt und Beobachtung verfasst.
- Nun muss der Angriff blockiert werden und es wird ein basis Ruleset geschrieben.

### 2025-12-22T12:51+01 pauchebousen
- base_firewall.nft geschrieben und mit "nft -f base_firewall.nft" verwendet,
  um Baseline-Ruleset neu zu schreiben.
- baseline-ruleset aktualisiert: policy drop input/forward, output accept
  ct state established,related, loopback accept
- Nun müssen verdächtige host geloggt werden

### 2025-12-22T13:56+01 pauchebousen
- base_firewall1_3.nft geschrieben und mit "nft -f base_firewall1_3.nft" verwendet, um die Baseline-Ruleset neu zu schreiben.
- baseline-ruleset aktualisiert: verdächtige host blokiert und geloggt.
- Angriff-Versuche werden in die Loggs aufgenommen und es wird gezählt, wie oft der Angriff kommt. Der
  Server antwortet nicht mehr (kein unreachable). Sobald ein packet von einem der beiden IPs kommt, wird es in  die 
  loggs geschrieben und weggeworfen.
- Nun muss geprüft werden, ob das alles geklappt hat.

### 2025-12-22T15:22+01 pauchebousen
- journalctl abgefragt: journalctl -k -g "BLOCKED_STUD" | tail -n 20
- Wir sehen die geblokten IPs. Im Gegensatz zu tcpdump sind jetzt auch unterschiedliche ports und Protokolls zu sehen,
  wie TCP, 6500. Es steht auch SRC und DST (sender und empfänger).
- Nun muss ein .pcap erstellt werden

### 2025-12-23T15:43+01 kevnguefack
- .pcap erstellt: tcpdump -i br_its_25 \( host 10.42.2.135 or host 10.42.2.131 \) and not port 22 -w phase01/loesung1_4.pcap
  oder  tcpdump -i br_its_25 host 10.42.2.131 or host 10.42.2.135 -w loesung1_4.pcap

- In die .pcap geschaut: tcpdump -r phase01/loesung1_4.pcap -n -X | less
  oder tcpdump -r loesung1_4.pcap -A
- indikatoren wurden extrahirt 

# Phase 02: 
# ----------------------------------------------------------------------------------------------------------------

### 2025-12-27T10:16+01 kevnguefack
- Prüfen, ob enps0 neu aufgetaucht ist: ip a
- Interface aufwäcken: ip link set enp7s0 up

- alte IPs löschen: ip addr flush dev enp7s0
- neue IP-Adresse zuweisen: ip addr add 10.42.10.12/24 dev enp7s0
- prüfen, ob sie sitzt: ip addr show enp7s0

### 2025-12-27T10:42+01 kevnguefack
- Persistenz: 
  vim /etc/network/interfaces.d/enp7s0
- in die datei schreiben wir:
  auto enp7s0
  iface enp7s0 inet static
    address 10.42.10.12/24
- IP ist persistent drine 
- Nun muss das subnetz gescant werden

### 2025-12-27T12:10+01 pauchebousen
- Wir haben zunächst versucht, mit netcat (in einer schleife)
  zu scannen: netz durchsuchen mit scan.sh
- dann nmap installiert

### 2025-12-28T12:00+01 pauchebousen
- nmap installiert
  apt install nmap
- subnetz scannen, um zu finden, welche IPs aktiv sind
  nmap -sn 10.42.10.0/24 -oN discovery_sn.txt

- beide IPs nach offenen Ports scannen
nmap -p- -sV 10.42.10.10 10.42.10.101
Starting Nmap 7.93 ( https://nmap.org ) at 2025-12-28 11:30 CET

### 2025-12-28T13:23+01 kevnguefack
- versuchen, sich mit netcat zu verbinden und den beweis zu sichern:
  nc 10.42.10.10 7902 > service_riss.txt
  aber mit nc bekommen wir keine Ausgabe. Vielleicht, weil dopwars nicht installiert ist.

- tcpdump im hintergrund starten, um zu hören: tcpdump -i enp7s0 host 10.42.10.10 and port 7902 -X &
- dann verbinden wir uns wieder mit nc und sehen:
  13:37:58.795929 IP 10.42.10.12.49204 > 10.42.10.10.7902: Flags [S], seq 1065629466, win 64240, options [mss 1460,sackOK,TS val 1010229594 ecr 0,nop,wscale 7], length 0
	0x0000:  4500 003c 21a1 4000 4006 f0b1 0a2a 0a0c  E..<!.@.@....*..
	0x0010:  0a2a 0a0a c034 1ede 3f84 371a 0000 0000  .*...4..?.7.....
	0x0020:  a002 faf0 2898 0000 0204 05b4 0402 080a  ....(...........
	0x0030:  3c36 e15a 0000 0000 0103 0307            <6.Z........
13:37:58.796593 IP 10.42.10.10.7902 > 10.42.10.12.49204: Flags [S.], seq 1719659739, ack 1065629467, win 65160, options [mss 1460,sackOK,TS val 1329192484 ecr 1010229594,nop,wscale 7], length 0
	0x0000:  4500 003c 0000 4000 4006 1253 0a2a 0a0a  E..<..@.@..S.*..
	0x0010:  0a2a 0a0c 1ede c034 667f ecdb 3f84 371b  .*.....4f...?.7.
	0x0020:  a012 fe88 2898 0000 0204 05b4 0402 080a  ....(...........
	0x0030:  4f39 de24 3c36 e15a 0103 0307            O9.$<6.Z....
13:37:58.796700 IP 10.42.10.12.49204 > 10.42.10.10.7902: Flags [.], ack 1, win 502, options [nop,nop,TS val 1010229595 ecr 1329192484], length 0
	0x0000:  4500 0034 21a2 4000 4006 f0b8 0a2a 0a0c  E..4!.@.@....*..
	0x0010:  0a2a 0a0a c034 1ede 3f84 371b 667f ecdc  .*...4..?.7.f...
	0x0020:  8010 01f6 2890 0000 0101 080a 3c36 e15b  ....(.......<6.[
	0x0030:  4f39 de24                                O9.$

### 2025-12-28T14:15+01
- dopwars installieren: apt install dopewars
  sich verbinden als root: /usr/games/dopewars -t -o 10.42.10.10
  sich verbinden als normal user: dopewars -t -o 10.42.10.10
- spielen und score notieren

- dopwars spielen:
  dopewars -o -h 10.42.10.10 -p 7902 (ohne -o wird nur das Hilfe-Manuel gedruckt)
  Hashish    3 @ $536

- nethack installieren: apt install nethack-console
  spielen: nethack


