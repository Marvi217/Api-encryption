package server;

import common.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

public class RegistrationService {
    private static final int PORT = 6000;
    private static ConcurrentHashMap<String, UserData> users = new ConcurrentHashMap<>();
    private static int userIdCounter = 1000;

    static class UserData {
        String username;
        String publicKey;
        int userId;
        UserData(String username, String publicKey, int userId) {
            this.username = username;
            this.publicKey = publicKey;
            this.userId = userId;
        }
    }

    public static void main(String[] args) {
        loadUsers();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Registration Service uruchomiony na porcie " + PORT);
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
            String username = request.getField("Login");
            String publicKey = request.getField("PublicKey");

            Message response = new Message("RegistrationResponse");
            response.setField("MessageId", request.getMessageId());

            if (username == null || publicKey == null) {
                response.setField("Status", "400");
                response.setField("Message", "Blad danych");
            } else if (users.containsKey(username)) {
                response.setField("Status", "409");
                response.setField("Message", "Uzytkownik istnieje");
            } else {
                int newUserId = userIdCounter++;
                users.put(username, new UserData(username, publicKey, newUserId));
                saveUsers();
                response.setField("Status", "200");
                response.setField("UserId", String.valueOf(newUserId));
                response.setField("Message", "OK");
            }
            out.println(response.toString());
        } catch (IOException e) {
            System.err.println("Blad: " + e.getMessage());
        }
    }

    private static void loadUsers() {
        File file = new File("data/users.dat");
        if (!file.exists()) {
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            int maxId = 1000;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|", 3);
                if (parts.length == 3) {
                    int id = Integer.parseInt(parts[2]);
                    users.put(parts[0], new UserData(parts[0], parts[1], id));
                    if (id >= maxId) maxId = id + 1;
                }
            }
            userIdCounter = maxId;
        } catch (Exception e) {
            System.err.println("Blad wczytywania");
        }
    }

    private static void saveUsers() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("data/users.dat"))) {
            for (UserData user : users.values()) {
                bw.write(user.username + "|" + user.publicKey + "|" + user.userId);
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Blad zapisu");
        }
    }
}