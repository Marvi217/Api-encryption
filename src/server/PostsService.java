package server;

import common.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class PostsService {
    private static final int ADD_PORT = 6002;
    private static final int GET_PORT = 6003;
    private static List<Post> posts = new CopyOnWriteArrayList<>();

    static class Post {
        String username; // Zmiana z userId
        String content;
        long timestamp;

        Post(String username, String content) {
            this.username = username; // Zmiana
            this.content = content;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static void main(String[] args) {
        loadPosts();

        new Thread(() -> runAddPostServer()).start();

        new Thread(() -> runGetPostsServer()).start();
    }

    private static void runAddPostServer() {
        try (ServerSocket serverSocket = new ServerSocket(ADD_PORT)) {
            System.out.println("Add Post Service uruchomiony na porcie " + ADD_PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleAddPost(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void runGetPostsServer() {
        try (ServerSocket serverSocket = new ServerSocket(GET_PORT)) {
            System.out.println("Get Posts Service uruchomiony na porcie " + GET_PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleGetPosts(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleAddPost(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String requestLine = in.readLine();
            Message request = Message.parse(requestLine);

            System.out.println("<- Dodaj post od: " + request.getField("Username"));

            Message response = new Message("AddPostResponse");
            response.setField("MessageId", request.getMessageId());

            String username = request.getField("Username");
            String content = request.getField("Content");

            if (username == null || content == null) {
                response.setField("Status", "400");
                response.setField("Message", "Brak wymaganych pol");
            } else {
                posts.add(new Post(username, content));
                savePosts();

                response.setField("Status", "200");
                response.setField("Message", "Post dodany");

                System.out.println("Post dodany (lacznie: " + posts.size() + ")");
            }

            out.println(response.toString());
            out.flush();

        } catch (IOException e) {
            System.err.println("Blad: " + e.getMessage());
        }
    }

    private static void handleGetPosts(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String requestLine = in.readLine();
            Message request = Message.parse(requestLine);

            System.out.println("<- Pobierz posty");

            Message response = new Message("GetPostsResponse");
            response.setField("MessageId", request.getMessageId());
            response.setField("Status", "200");

            int start = Math.max(0, posts.size() - 10);
            int postNum = 1;

            for (int i = posts.size() - 1; i >= start; i--) {
                Post post = posts.get(i);
                response.setField("Post" + postNum, "User:" + post.username + "-Post:" + post.content);
                postNum++;
            }

            response.setField("Message", "Zwrocono " + (postNum - 1) + " postow");

            System.out.println("Wyslano " + (postNum - 1) + " postow");

            out.println(response.toString());
            out.flush();

        } catch (IOException e) {
            System.err.println("Blad: " + e.getMessage());
        }
    }

    private static void loadPosts() {
        File file = new File("data/posts.dat");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|", 2);
                if (parts.length == 2) {
                    posts.add(new Post(parts[0], parts[1]));
                }
            }
            System.out.println("Wczytano " + posts.size() + " postow");
        } catch (IOException e) {
            System.err.println("Blad wczytywania: " + e.getMessage());
        }
    }

    private static void savePosts() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("data/posts.dat"))) {
            for (Post post : posts) {
                bw.write(post.username + "|" + post.content);
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Blad zapisywania: " + e.getMessage());
        }
    }
}