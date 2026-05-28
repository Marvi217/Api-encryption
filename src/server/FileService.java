package server;

import common.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class FileService {
    private static final int UPLOAD_PORT = 6004;
    private static final int DOWNLOAD_PORT = 6005;
    private static final int LIST_PORT = 6007;
    private static final String STORAGE_DIR = "data/files/";

    public static void main(String[] args) {
        new File(STORAGE_DIR).mkdirs();

        new Thread(FileService::runUploadServer).start();
        new Thread(FileService::runDownloadServer).start();
        new Thread(FileService::runListServer).start();
    }

    private static void runUploadServer() {
        try (ServerSocket serverSocket = new ServerSocket(UPLOAD_PORT)) {
            System.out.println("File Upload Service uruchomiony na porcie " + UPLOAD_PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleUpload(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void runDownloadServer() {
        try (ServerSocket serverSocket = new ServerSocket(DOWNLOAD_PORT)) {
            System.out.println("File Download Service uruchomiony na porcie " + DOWNLOAD_PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleDownload(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void runListServer() {
        try (ServerSocket serverSocket = new ServerSocket(LIST_PORT)) {
            System.out.println("File List Service uruchomiony na porcie " + LIST_PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleList(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleUpload(Socket socket) {
        String fileName = null;
        FileOutputStream fos = null;

        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String line;
            while ((line = in.readLine()) != null) {
                Message chunk = Message.parse(line);

                if (fileName == null) {
                    fileName = chunk.getField("FileName");
                    System.out.println("<- Upload: " + fileName);
                    fos = new FileOutputStream(STORAGE_DIR + fileName);
                }

                String data = chunk.getField("Data");
                if (data != null) {
                    byte[] decoded = Base64.getDecoder().decode(data);
                    fos.write(decoded);
                }

                if ("true".equals(chunk.getField("EndOfFile"))) {
                    fos.close();

                    Message response = new Message("UploadFileResponse");
                    response.setField("MessageId", chunk.getMessageId());
                    response.setField("Status", "204");
                    response.setField("Message", "Plik zapisany");

                    out.println(response.toString());
                    out.flush();

                    System.out.println("-> Zapisano: " + fileName);
                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("Blad uploadu: " + e.getMessage());
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void handleDownload(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String requestLine = in.readLine();
            Message request = Message.parse(requestLine);

            String fileName = request.getField("FileName");
            System.out.println("<- Download: " + fileName);

            File file = new File(STORAGE_DIR + fileName);

            if (!file.exists()) {
                Message error = new Message("DownloadFileResponse");
                error.setField("Status", "404");
                error.setField("Message", "Plik nie istnieje");
                error.setField("EndOfFile", "true");
                out.println(error.toString());
                return;
            }

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[512];
                int bytesRead;
                long offset = 0;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    String encodedData = Base64.getEncoder().encodeToString(
                            Arrays.copyOf(buffer, bytesRead)
                    );

                    Message chunk = new Message("DownloadFileResponse");
                    chunk.setField("MessageId", request.getMessageId());
                    chunk.setField("Status", "200");
                    chunk.setField("FileName", fileName);
                    chunk.setField("Offset", String.valueOf(offset));
                    chunk.setField("Data", encodedData);

                    out.println(chunk.toString());
                    out.flush();

                    offset += bytesRead;
                }

                Message endMsg = new Message("DownloadFileResponse");
                endMsg.setField("MessageId", request.getMessageId());
                endMsg.setField("Status", "200");
                endMsg.setField("FileName", fileName);
                endMsg.setField("EndOfFile", "true");

                out.println(endMsg.toString());
                out.flush();

                System.out.println("-> Wyslano: " + fileName + " (" + offset + " bajtow)");
            }

        } catch (Exception e) {
            System.err.println("Blad downloadu: " + e.getMessage());
        }
    }

    private static void handleList(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String requestLine = in.readLine();
            Message request = Message.parse(requestLine);

            System.out.println("<- Lista plikow");

            File dir = new File(STORAGE_DIR);
            File[] files = dir.listFiles();

            StringBuilder fileList = new StringBuilder();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        if (fileList.length() > 0) fileList.append(",");
                        fileList.append(file.getName());
                    }
                }
            }

            Message response = new Message("GetAvailableFilesResponse");
            response.setField("MessageId", request.getMessageId());
            response.setField("Status", "200");
            response.setField("Files", fileList.toString());

            out.println(response.toString());
            out.flush();

            System.out.println("-> Wyslano liste: " + (files != null ? files.length : 0) + " plikow");

        } catch (Exception e) {
            System.err.println("Blad: " + e.getMessage());
        }
    }
}