package assignment3;

import java.io.*;
import java.net.*;
import java.util.Scanner;

/*
 * FileClient
 * 1. Create Socket
 * 2. Connect Server
 * 3. Send/Recv
 * 4. Close
 */
public class FileClient {

    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Available commands: 'index' or 'get <filename>' or 'exit'");

        while (true) {
            System.out.print("\nEnter command: ");
            String userCommand = scanner.nextLine();

            if (userCommand.equalsIgnoreCase("exit")) break;

            try (Socket socket = new Socket(InetAddress.getByName(SERVER_ADDRESS), SERVER_PORT)) {
                // Setup input and output streams
                // Use true here to auto flush buffer instantly
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Handle the response
                System.out.println("------------------------");
                if (userCommand.equals("index")) {
                    // Send command to server
                    out.println(userCommand);
                    System.out.println("File List:");
                    String line;
                    while ((line = in.readLine()) != null) {
                        System.out.println(line);
                    }
                } else if (userCommand.startsWith("get ")) {
                    // Send command to server
                    out.println(userCommand);
                    String status = in.readLine();
                    System.out.println(status);

                    if ("ok".equals(status)) {
                        System.out.println("File found. Content:");
                        String line;
                        while ((line = in.readLine()) != null) {
                            System.out.println(line);
                        }
                    } else if ("error".equals(status)) {
                        System.out.println("[Error] File not found");
                    } else {
                        System.out.println("[Warning] Unexpected status: " + status);
                    }
                } else {
                    System.out.println("[Warning] Unexpected command");
                }
                System.out.println("------------------------");

            } catch (IOException e) {
                System.out.println("[Error] Connection error, e: " + e.getMessage());
            }
        }

        scanner.close();
    }
}