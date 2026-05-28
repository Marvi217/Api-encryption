package server;

import common.*;
import java.io.*;
import java.net.*;
import java.security.PublicKey;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

public class LoginService {
    private static final int PORT = 6001;
    private static ConcurrentHashMap<String, UserData> users = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, String> challenges = new ConcurrentHashMap<>();

    static class UserData {
        String publicKey;
        String userId;
        UserData(String pk, String id) { this.publicKey = pk; this.userId = id; }
    }

    public static void main(String[] args) {
        loadUsers();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Login Service uruchomiony na porcie " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String requestLine = in.readLine();
            if (requestLine == null) return;
            Message request = Message.parse(requestLine);
            String messageType = request.getMessageType();

            if ("LoginRequest".equals(messageType)) {
                handleLoginRequest(request, out);
            } else if ("LoginVerify".equals(messageType)) {
                handleLoginVerify(request, out);
            }

        } catch (IOException e) {
            System.err.println("Blad: " + e.getMessage());
        }
    }

    private static void handleLoginRequest(Message request, PrintWriter out) {
        loadUsers();
        String username = request.getField("Login");
        UserData user = users.get(username);

        Message response = new Message("LoginResponse");
        response.setField("MessageId", request.getMessageId());

        if (user == null) {
            response.setField("Status", "404");
            response.setField("Message", "Uzytkownik nie istnieje");
        } else {
            byte[] challengeBytes = Crypto.randomBytes(32);
            String challenge = Base64.getEncoder().encodeToString(challengeBytes);
            challenges.put(username, challenge);

            response.setField("Status", "200");
            response.setField("Challenge", challenge);
            response.setField("Message", "Challenge wygenerowany");
        }

        out.println(response);
        out.flush();
    }

    private static void handleLoginVerify(Message request, PrintWriter out) {
        loadUsers();
        Message response = new Message("LoginVerifyResponse");
        response.setField("MessageId", request.getMessageId());

        String username = request.getField("Login");
        String signature = request.getField("Signature");
        String challenge = challenges.get(username);
        UserData user = users.get(username);

        if (challenge == null || user == null) {
            response.setField("Status", "401");
            response.setField("Message", "Brak sesji logowania");
        } else {
            try {
                PublicKey publicKey = Crypto.stringToPublicKey(user.publicKey);
                boolean valid = Crypto.verify(challenge, signature, publicKey);

                if (valid) {
                    response.setField("Status", "200");
                    response.setField("UserId", user.userId);
                    response.setField("Message", "Zalogowano pomyslnie");
                    challenges.remove(username);
                } else {
                    response.setField("Status", "401");
                    response.setField("Message", "Nieprawidlowy podpis");
                }
            } catch (Exception e) {
                response.setField("Status", "500");
                response.setField("Message", "Blad: " + e.getMessage());
            }
        }
        out.println(response.toString());
        out.flush();
    }

    private static void loadUsers() {
        File file = new File("data/users.dat");
        if (!file.exists()) return;

        users.clear();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|", 3);
                if (parts.length == 3) {
                    users.put(parts[0], new UserData(parts[1], parts[2]));
                }
            }
        } catch (IOException e) {
            System.err.println("Blad wczytywania: " + e.getMessage());
        }
    }
}