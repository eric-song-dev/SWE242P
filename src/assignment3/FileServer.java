package assignment3;

import java.io.*;
import java.net.*;

/*
 * FileServer
 * 1. Create Socket
 * 2. Bind Port
 * 3. Listen
 * 4. Accept Connection
 * 5. Send/Recv
 * 6. Close
 */
public class FileServer {

    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final int PORT = 12345;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java FileServer <directory_path>");
            return;
        }

        File directory = new File(args[0]);
        if (!directory.exists() || !directory.isDirectory()) {
            System.err.println("[Error] The path specified is not a valid directory");
            return;
        }

        System.out.println("Server started on port " + PORT);
        System.out.println("Files directory: " + directory.getAbsolutePath());

        // Start the TCP Server
        // Create socket, Bind port, and Listen
        try (ServerSocket serverSocket = new ServerSocket(PORT, 0, InetAddress.getByName(SERVER_ADDRESS))) {
            while (true) {
                // Accept an incoming client connection (blocking call)
                try (Socket clientSocket = serverSocket.accept()) {
                    System.out.println("Client connected: " + clientSocket.getInetAddress());

                    // Setup input and output streams
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                    String command = in.readLine();
                    if (command == null) continue;

                    System.out.println("Received command: " + command);

                    // Process the command
                    if (command.equals("index")) {
                        handleIndexCommand(out, directory);
                    } else if (command.startsWith("get ")) {
                        String filename = command.substring(4).trim();
                        handleGetCommand(out, directory, filename);
                    } else {
                        out.println("Unknown command");
                    }

                    System.out.println("Closing connection");
                } catch (IOException e) {
                    System.err.println("[Error] Establish connection, e: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[Error] Could not start server, e: " + e.getMessage());
        }
    }

    private static void handleIndexCommand(PrintWriter out, File directory) {
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    out.println(file.getName());
                }
            }
        }
    }

    private static void handleGetCommand(PrintWriter out, File directory, String filename) {
        File fileToSend = new File(directory, filename);

        if (fileToSend.exists() && fileToSend.isFile()) {
            out.println("ok");

            // Read the file and send it line by line
            try (BufferedReader fileReader = new BufferedReader(new FileReader(fileToSend))) {
                String line;
                while ((line = fileReader.readLine()) != null) {
                    out.println(line);
                }
            } catch (IOException e) {
                System.err.println("[Error] Read " + filename + " failed, e: " + e.getMessage());
            }
        } else {
            out.println("error");
        }
    }
}