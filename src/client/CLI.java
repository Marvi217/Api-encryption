package client;

import common.*;
import java.io.*;
import java.net.Socket;
import java.security.*;
import javax.crypto.SecretKey;
import java.util.Base64;

public class CLI {
    private static int messageIdCounter = 0;
    private static String userId = null;
    private static String username = null;
    private static PrivateKey myPrivateKey = null;
    private static PublicKey myPublicKey = null;
    private static SecretKey sessionKey = null;

    public static void main(String[] args) {
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

        try (Socket socket = new Socket("localhost", 7000);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            System.out.println("=== Polaczono z serwerem ===\n");

            if (!exchangeKeys(out, in)) {
                System.out.println("Blad wymiany kluczy!");
                return;
            }

            while (true) {
                if (userId == null) {
                    System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
                    System.out.println("Wybierz opcje:");
                    System.out.println("1: Rejestracja");
                    System.out.println("2: Logowanie");
                    System.out.println("0: Wyjscie");
                    System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
                    String command = console.readLine();

                    switch (command) {
                        case "1":
                            handleRegistration(console, out, in);
                            break;
                        case "2":
                            handleLogin(console, out, in);
                            break;
                        case "0":
                            System.out.println("Zakonczono polaczenie.");
                            return;
                        default:
                            System.out.println("Nieznane polecenie.");
                    }
                } else {
                    System.out.println("\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
                    System.out.println("Witaj " + username + "! Wybierz opcje:");
                    System.out.println("1: Wyloguj");
                    System.out.println("2: Dodaj post");
                    System.out.println("3: Wyswietl ostatnie 10 postow");
                    System.out.println("4: Wyslij plik");
                    System.out.println("5: Pobierz plik");
                    System.out.println("0: Wyjscie");
                    System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

                    String command = console.readLine();
                    switch (command) {
                        case "1":
                            System.out.println("Wylogowano pomyslnie.");
                            userId = null;
                            username = null;
                            break;
                        case "2":
                            handleAddPost(console, out, in);
                            break;
                        case "3":
                            handleGetPosts(out, in);
                            break;
                        case "4":
                            handleUploadFile(console, out, in);
                            break;
                        case "5":
                            handleDownloadFile(console, out, in);
                            break;
                        case "0":
                            System.out.println("Zakonczono polaczenie.");
                            return;
                        default:
                            System.out.println("Nieznane polecenie.");
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Blad polaczenia: " + e.getMessage());
        }
    }

    private static boolean exchangeKeys(PrintWriter out, BufferedReader in) {
        try {
            sessionKey = Crypto.generateAESKey();

            String response = in.readLine();
            Message msg = Message.parse(response);

            if (!"KeyExchange".equals(msg.getMessageType())) {
                return false;
            }

            String serverPublicKeyStr = msg.getField("PublicKey");
            PublicKey serverPublicKey = Crypto.stringToPublicKey(serverPublicKeyStr);

            String encryptedSessionKey = Crypto.encryptRSA(
                    sessionKey.getEncoded(),
                    serverPublicKey
            );

            Message keyMsg = new Message("KeyExchange");
            keyMsg.setField("SessionKey", encryptedSessionKey);

            out.println(keyMsg);
            out.flush();

            System.out.println("Wymiana kluczy zakonczona\n");
            return true;

        } catch (Exception e) {
            System.err.println("Blad wymiany kluczy: " + e.getMessage());
            return false;
        }
    }

    private static void handleRegistration(BufferedReader console, PrintWriter out, BufferedReader in) {
        try {
            System.out.println("Podaj nazwe uzytkownika:");
            String regUsername = console.readLine();

            System.out.println("Generowanie kluczy RSA...");
            KeyPair keyPair = Crypto.generateRSAKeyPair();
            myPrivateKey = keyPair.getPrivate();
            myPublicKey = keyPair.getPublic();

            savePrivateKey(regUsername, myPrivateKey);

            Message request = new Message("RegistrationRequest");
            request.setField("MessageId", String.valueOf(generateMessageId()));
            request.setField("Login", regUsername);
            request.setField("PublicKey", Crypto.publicKeyToString(myPublicKey));

            Message response = sendAndReceive(request, out, in);

            if ("200".equals(response.getField("Status"))) {
                String assignedId = response.getField("UserId");
                System.out.println("Rejestracja zakonczona sukcesem. Twoje ID: " + assignedId);
            } else {
                System.out.println("Blad rejestracji: " + response.getField("Message"));
            }
        } catch (Exception e) {
            System.err.println("Blad rejestracji: " + e.getMessage());
        }
    }

    private static void handleLogin(BufferedReader console, PrintWriter out, BufferedReader in) {
        try {
            System.out.println("Podaj nazwe uzytkownika:");
            String loginUsername = console.readLine();

            myPrivateKey = loadPrivateKey(loginUsername);
            if (myPrivateKey == null) {
                System.out.println("Nie znaleziono klucza. Najpierw sie zarejestruj.");
                return;
            }

            Message challengeRequest = new Message("LoginRequest");
            challengeRequest.setField("MessageId", String.valueOf(generateMessageId()));
            challengeRequest.setField("Login", loginUsername);

            Message challengeResponse = sendAndReceive(challengeRequest, out, in);

            if (!"200".equals(challengeResponse.getField("Status"))) {
                System.out.println("Blad logowania: " + challengeResponse.getField("Message"));
                return;
            }

            String challenge = challengeResponse.getField("Challenge");
            String signature = Crypto.sign(challenge, myPrivateKey);

            Message verifyRequest = new Message("LoginVerify");
            verifyRequest.setField("MessageId", String.valueOf(generateMessageId()));
            verifyRequest.setField("Login", loginUsername);
            verifyRequest.setField("Signature", signature);

            Message verifyResponse = sendSignedMessage(verifyRequest, out, in);

            if ("200".equals(verifyResponse.getField("Status"))) {
                userId = verifyResponse.getField("UserId");
                username = loginUsername;
                System.out.println("Zalogowano pomyslnie. ID: " + userId);
            } else {
                System.out.println("Blad logowania: " + verifyResponse.getField("Message"));
            }
        } catch (Exception e) {
            System.err.println("Blad logowania: " + e.getMessage());
        }
    }

    private static void handleAddPost(BufferedReader console, PrintWriter out, BufferedReader in) {
        try {
            System.out.println("Napisz swoj post:");
            String postContent = console.readLine();

            Message request = new Message("AddPostRequest");
            request.setField("MessageId", String.valueOf(generateMessageId()));
            request.setField("UserId", userId);
            request.setField("Username", username);
            request.setField("Content", postContent);

            Message response = sendAndReceive(request, out, in);

            if ("200".equals(response.getField("Status"))) {
                System.out.println("Post dodany pomyslnie.");
            } else {
                System.out.println("Blad: " + response.getField("Message"));
            }
        } catch (Exception e) {
            System.err.println("Blad: " + e.getMessage());
        }
    }

    private static void handleGetPosts(PrintWriter out, BufferedReader in) {
        try {
            Message request = new Message("GetPostsRequest");
            request.setField("MessageId", String.valueOf(generateMessageId()));

            Message response = sendAndReceive(request, out, in);

            if ("200".equals(response.getField("Status"))) {
                System.out.println("\n=== Ostatnie 10 postow ===");
                int postNum = 1;
                while (response.hasField("Post" + postNum)) {
                    String post = response.getField("Post" + postNum);
                    String[] parts = post.split("-Post:", 2);
                    if (parts.length == 2) {
                        String user = parts[0].replace("User:", "");
                        String content = parts[1];
                        System.out.println(postNum + ". " + user + ": " + content);
                    }
                    postNum++;
                }
            } else {
                System.out.println("Blad: " + response.getField("Message"));
            }
        } catch (Exception e) {
            System.err.println("Blad: " + e.getMessage());
        }
    }

    private static void handleUploadFile(BufferedReader console, PrintWriter out, BufferedReader in) {
        try {
            System.out.println("Podaj sciezke do pliku:");
            String filePath = console.readLine();
            File file = new File(filePath);

            if (!file.exists()) {
                System.out.println("Plik nie istnieje.");
                return;
            }

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[512];
                int bytesRead;
                long offset = 0;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    String encodedData = Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(buffer, bytesRead));
                    Message chunk = new Message("UploadFileRequest");
                    chunk.setField("MessageId", String.valueOf(generateMessageId()));
                    chunk.setField("UserId", userId);
                    chunk.setField("FileName", file.getName());
                    chunk.setField("Data", encodedData);
                    chunk.setField("Offset", String.valueOf(offset));
                    sendMessage(chunk, out);
                    offset += bytesRead;
                }

                Message endMsg = new Message("UploadFileRequest");
                endMsg.setField("EndOfFile", "true");
                endMsg.setField("UserId", userId);
                endMsg.setField("FileName", file.getName());
                Message response = sendAndReceive(endMsg, out, in);
                System.out.println(response.getField("Message"));
            }
        } catch (Exception e) {
            System.err.println("Blad: " + e.getMessage());
        }
    }

    private static void handleDownloadFile(BufferedReader console, PrintWriter out, BufferedReader in) {
        try {
            Message listRequest = new Message("GetAvailableFilesRequest");
            Message listResponse = sendAndReceive(listRequest, out, in);
            String files = listResponse.getField("Files");
            if (files == null || files.isEmpty()) return;

            String[] fileArray = files.split(",");
            for (int i = 0; i < fileArray.length; i++) System.out.println((i + 1) + ". " + fileArray[i]);

            System.out.println("Podaj numer pliku:");
            int idx = Integer.parseInt(console.readLine()) - 1;

            Message downloadRequest = new Message("DownloadFileRequest");
            downloadRequest.setField("UserId", userId);
            downloadRequest.setField("FileName", fileArray[idx]);
            sendMessage(downloadRequest, out);

            File outputFile = new File("downloads/" + fileArray[idx]);
            outputFile.getParentFile().mkdirs();

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                while (true) {
                    String line = in.readLine();
                    String decrypted = Crypto.decryptAES(line, sessionKey);
                    Message chunk = Message.parse(decrypted);
                    if (chunk.hasField("Data")) {
                        fos.write(Base64.getDecoder().decode(chunk.getField("Data")));
                    }
                    if ("true".equals(chunk.getField("EndOfFile"))) break;
                }
            }
            System.out.println("Pobrano plik.");
        } catch (Exception e) {
            System.err.println("Blad: " + e.getMessage());
        }
    }

    private static void sendMessage(Message msg, PrintWriter out) throws Exception {
        String data = msg.toString();
        String encrypted = Crypto.encryptAES(data, sessionKey);
        out.println("EncryptedData:" + encrypted);
        out.flush();
    }
    private static Message sendSignedMessage(Message msg, PrintWriter out, BufferedReader in) throws Exception {
        String data = msg.toString();
        if (myPrivateKey != null) {
            data += "|Signature:" + Crypto.sign(data, myPrivateKey);
        }
        String encrypted = Crypto.encryptAES(data, sessionKey);
        out.println("EncryptedData:" + encrypted);
        out.flush();

        String line = in.readLine();
        if (line == null) throw new IOException("Brak odpowiedzi");
        String decrypted = Crypto.decryptAES(line, sessionKey);
        String[] parts = decrypted.split("\\|Signature:");
        return Message.parse(parts[0]);
    }

    private static Message sendAndReceive(Message request, PrintWriter out, BufferedReader in) throws Exception {
        sendMessage(request, out);
        String line = in.readLine();
        if (line == null) throw new IOException("Brak odpowiedzi");
        String decrypted = Crypto.decryptAES(line, sessionKey);
        String[] parts = decrypted.split("\\|Signature:");
        return Message.parse(parts[0]);
    }

    private static void savePrivateKey(String username, PrivateKey key) throws IOException {
        new File("keys").mkdirs();
        try (FileWriter fw = new FileWriter("keys/" + username + ".key")) {
            fw.write(Crypto.privateKeyToString(key));
        }
    }

    private static PrivateKey loadPrivateKey(String username) {
        try (BufferedReader br = new BufferedReader(new FileReader("keys/" + username + ".key"))) {
            return Crypto.stringToPrivateKey(br.readLine());
        } catch (Exception e) {
            return null;
        }
    }

    private static int generateMessageId() {
        return ++messageIdCounter;
    }
}