package server;

import common.*;
import java.io.*;
import java.net.*;
import java.security.*;
import javax.crypto.SecretKey;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class APIGateway {
    private static final int GATEWAY_PORT = 7000;
    private static final Map<String, Integer> SERVICE_PORTS = new HashMap<>();
    private static KeyPair serverKeyPair;
    private static Map<Socket, SecretKey> sessionKeys = new ConcurrentHashMap<>();

    static {
        SERVICE_PORTS.put("RegistrationRequest", 6000);
        SERVICE_PORTS.put("LoginRequest", 6001);
        SERVICE_PORTS.put("LoginVerify", 6001);
        SERVICE_PORTS.put("AddPostRequest", 6002);
        SERVICE_PORTS.put("GetPostsRequest", 6003);
        SERVICE_PORTS.put("UploadFileRequest", 6004);
        SERVICE_PORTS.put("DownloadFileRequest", 6005);
        SERVICE_PORTS.put("GetAvailableFilesRequest", 6007);
    }

    public static void main(String[] args) {
        try {
            System.out.println("Generowanie kluczy serwera...");
            serverKeyPair = Crypto.generateRSAKeyPair();

            ServerSocket serverSocket = new ServerSocket(GATEWAY_PORT);
            System.out.println("API Gateway uruchomiony na porcie " + GATEWAY_PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nowe polaczenie: " + clientSocket.getInetAddress());
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket clientSocket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            if (!exchangeKeys(clientSocket, in, out)) {
                System.out.println("Blad wymiany kluczy");
                clientSocket.close();
                return;
            }

            SecretKey sessionKey = sessionKeys.get(clientSocket);

            String encryptedLine;
            while ((encryptedLine = in.readLine()) != null) {
                try {
                    System.out.println("\n" + "=".repeat(60));
                    System.out.println("OTRZYMANO ZASZYFROWANE:");
                    System.out.println(encryptedLine.substring(0, Math.min(100, encryptedLine.length())) +
                            (encryptedLine.length() > 100 ? "..." : ""));
                    System.out.flush();

                    String decrypted = Crypto.decryptAES(encryptedLine, sessionKey);

                    System.out.println("\nPO DESZYFROWANIU:");
                    System.out.println(decrypted);
                    System.out.println("=".repeat(60) + "\n");
                    System.out.flush();

                    String[] parts = decrypted.split("\\|Signature:");
                    String messageData = parts[0];

                    Message request = Message.parse(messageData);
                    System.out.println("<- " + request.getMessageType() + " (ID: " + request.getMessageId() + ")");
                    System.out.flush();

                    String messageType = request.getMessageType();

                    if ("UploadFileRequest".equals(messageType)) {
                        handleFileUpload(request, in, out, sessionKey);
                    } else if ("DownloadFileRequest".equals(messageType)) {
                        handleFileDownload(request, out, sessionKey);
                    } else {
                        Message response = forwardToService(request, messageType);

                        System.out.println("\nPRZED ZASZYFROWANIEM:");
                        System.out.println(response.toString());
                        System.out.flush();

                        String encrypted = Crypto.encryptAES(response.toString(), sessionKey);

                        System.out.println("\nPO ZASZYFROWANIU:");
                        System.out.println(encrypted.substring(0, Math.min(100, encrypted.length())) +
                                (encrypted.length() > 100 ? "..." : ""));
                        System.out.println("=".repeat(60) + "\n");
                        System.out.flush();

                        out.println("EncryptedData:" + encrypted);
                        out.flush();

                        System.out.println("-> " + response.getMessageType() + " (Status: " + response.getField("Status") + ")");
                        System.out.flush();
                    }

                } catch (Exception e) {
                    System.err.println("Blad przetwarzania: " + e.getMessage());

                    Message errorResponse = new Message("ErrorResponse");
                    errorResponse.setField("Status", "500");
                    errorResponse.setField("Message", "Blad serwera");

                    String encrypted = Crypto.encryptAES(errorResponse.toString(), sessionKey);
                    out.println("EncryptedData:" + encrypted);
                    out.flush();
                }
            }

        } catch (Exception e) {
            System.err.println("Blad polaczenia: " + e.getMessage());
        } finally {
            sessionKeys.remove(clientSocket);
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean exchangeKeys(Socket clientSocket, BufferedReader in, PrintWriter out) {
        try {
            Message keyMsg = new Message("KeyExchange");
            keyMsg.setField("PublicKey", Crypto.publicKeyToString(serverKeyPair.getPublic()));
            out.println(keyMsg.toString());
            out.flush();

            String response = in.readLine();
            Message keyResponse = Message.parse(response);

            String encryptedSessionKey = keyResponse.getField("SessionKey");
            byte[] sessionKeyBytes = Crypto.decryptRSA(encryptedSessionKey, serverKeyPair.getPrivate());

            SecretKey sessionKey = new javax.crypto.spec.SecretKeySpec(sessionKeyBytes, "AES");
            sessionKeys.put(clientSocket, sessionKey);

            System.out.println("Wymiana kluczy zakonczona");
            return true;

        } catch (Exception e) {
            System.err.println("Blad wymiany kluczy: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static Message forwardToService(Message request, String messageType) {
        Integer servicePort = SERVICE_PORTS.get(messageType);

        if (servicePort == null) {
            Message error = new Message(messageType.replace("Request", "Response"));
            error.setField("Status", "400");
            error.setField("Message", "Nieobslugiwany typ zadania");
            return error;
        }

        try (Socket serviceSocket = new Socket("localhost", servicePort);
             PrintWriter serviceOut = new PrintWriter(serviceSocket.getOutputStream(), true);
             BufferedReader serviceIn = new BufferedReader(new InputStreamReader(serviceSocket.getInputStream()))) {

            serviceOut.println(request.toString());
            serviceOut.flush();

            String responseLine = serviceIn.readLine();
            return Message.parse(responseLine);

        } catch (Exception e) {
            System.err.println("Blad komunikacji z mikrousluga: " + e.getMessage());

            Message error = new Message(messageType.replace("Request", "Response"));
            error.setField("Status", "503");
            error.setField("Message", "Usluga niedostepna");
            return error;
        }
    }

    private static void handleFileUpload(Message firstChunk, BufferedReader in, PrintWriter out, SecretKey sessionKey) {
        try {
            Integer servicePort = SERVICE_PORTS.get("UploadFileRequest");

            Socket serviceSocket = new Socket("localhost", servicePort);
            PrintWriter serviceOut = new PrintWriter(serviceSocket.getOutputStream(), true);
            BufferedReader serviceIn = new BufferedReader(new InputStreamReader(serviceSocket.getInputStream()));

            serviceOut.println(firstChunk.toString());
            serviceOut.flush();

            String encryptedLine;
            while ((encryptedLine = in.readLine()) != null) {
                String decrypted = Crypto.decryptAES(encryptedLine, sessionKey);
                String[] parts = decrypted.split("\\|Signature:");
                Message chunk = Message.parse(parts[0]);

                serviceOut.println(chunk.toString());
                serviceOut.flush();

                if ("true".equals(chunk.getField("EndOfFile"))) {
                    break;
                }
            }

            String responseLine = serviceIn.readLine();
            Message response = Message.parse(responseLine);

            String encrypted = Crypto.encryptAES(response.toString(), sessionKey);
            out.println("EncryptedData:" + encrypted);
            out.flush();

            serviceSocket.close();
            System.out.println("Upload zakonczony (Status: " + response.getField("Status") + ")");

        } catch (Exception e) {
            System.err.println("Blad uploadu: " + e.getMessage());
        }
    }

    private static void handleFileDownload(Message request, PrintWriter out, SecretKey sessionKey) {
        try {
            Integer servicePort = SERVICE_PORTS.get("DownloadFileRequest");

            Socket serviceSocket = new Socket("localhost", servicePort);
            PrintWriter serviceOut = new PrintWriter(serviceSocket.getOutputStream(), true);
            BufferedReader serviceIn = new BufferedReader(new InputStreamReader(serviceSocket.getInputStream()));

            serviceOut.println(request.toString());
            serviceOut.flush();

            String chunkLine;
            while ((chunkLine = serviceIn.readLine()) != null) {
                Message chunk = Message.parse(chunkLine);

                String encrypted = Crypto.encryptAES(chunk.toString(), sessionKey);
                out.println("EncryptedData:" + encrypted);
                out.flush();

                if ("true".equals(chunk.getField("EndOfFile"))) {
                    break;
                }
            }

            serviceSocket.close();
            System.out.println("Download zakonczony");

        } catch (Exception e) {
            System.err.println("Blad downloadu: " + e.getMessage());
        }
    }
}