# Api-encryption

Projekt z przedmiotu Sieci Komputerowe. Aplikacja klient-serwer oparta na surowych gniazdach TCP z implementacją szyfrowania RSA/AES, uwierzytelniania challenge-response oraz transferem plików. Architektura mikroserwisowa z bramką API.

## Technologie

![Java](https://img.shields.io/badge/Java-83.5%25-ED8B00?style=flat&logo=openjdk&logoColor=white)
![RSA](https://img.shields.io/badge/RSA-2048--bit-green?style=flat)
![AES](https://img.shields.io/badge/AES-256--bit-green?style=flat)
![TCP Sockets](https://img.shields.io/badge/Transport-TCP%20Sockets-blue?style=flat)

## Funkcjonalności

- Szyfrowanie asymetryczne RSA (2048-bit) do wymiany kluczy sesyjnych
- Szyfrowanie symetryczne AES (256-bit) komunikacji
- Uwierzytelnianie challenge-response (serwer wysyła wyzwanie, klient podpisuje kluczem prywatnym)
- Kodowanie Base64 wiadomości
- Własny protokół wiadomości (klasa `Message` z polami klucz-wartość)
- Transfer plików: upload, download, listowanie dostępnych plików
- Struktury wątkowo-bezpieczne: `ConcurrentHashMap`, `CopyOnWriteArrayList`
- Persystencja danych użytkowników i postów do plików
- Mikroserwisy na osobnych portach: rejestracja, logowanie, posty, pliki
- Bramka API (API Gateway) z routingiem zapytań do właściwej usługi
- Klient CLI

## Struktura projektu

```
Api-encryption/
├── src/
│   ├── client/       # Klient CLI
│   ├── common/       # Wspólne klasy (Message, szyfrowanie, Base64)
│   └── server/       # Mikroserwisy + API Gateway
├── data/             # Persystencja danych (użytkownicy, posty)
├── keys/             # Wygenerowane klucze RSA
└── start.cmd         # Skrypt uruchamiający wszystkie usługi
```

## Uruchomienie

### Wymagania
- Java 17+
- System Windows (dla `start.cmd`) lub ręczne uruchomienie usług

### Szybki start (Windows)

```cmd
git clone https://github.com/Marvi217/Api-encryption.git
cd Api-encryption
start.cmd
```

### Ręczne uruchomienie

Skompiluj projekt:
```bash
javac -d out src/common/*.java src/server/*.java src/client/*.java
```

Uruchom każdy mikroserwis w osobnym terminalu, następnie uruchom klienta:
```bash
java -cp out client.Main
```
