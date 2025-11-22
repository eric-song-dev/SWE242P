package assignment4;

import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.nio.ByteBuffer;

public class UDPClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;
    private static final int TIMEOUT = 2000;
    private static final int MAX_RETRIES = 5;
    private static final int CHUNK_SIZE = 1024;
    private static final int HEADER_SIZE = 4;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Available commands: 'index' or 'get <filename>' or 'exit'");

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT); // Enable timeout for receive() calls
            InetAddress serverAddress = InetAddress.getByName(SERVER_ADDRESS);

            while (true) {
                System.out.print("\nEnter command: ");
                String input = scanner.nextLine();

                if (input.equalsIgnoreCase("exit")) break;

                if (input.equals("index")) {
                    requestIndex(socket, serverAddress);
                } else if (input.startsWith("get ")) {
                    String filename = input.substring(4).trim();
                    downloadFile(socket, serverAddress, filename);
                } else {
                    System.out.println("[Warning] Unexpected command");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendPacket(DatagramSocket socket, InetAddress address, String text) throws IOException {
        byte[] data = text.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, address, SERVER_PORT);
        socket.send(packet);
    }

    private static void requestIndex(DatagramSocket socket, InetAddress address) {
        try {
            sendPacket(socket, address, "INDEX");

            byte[] buffer = new byte[4096];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);

            String list = new String(response.getData(), 0, response.getLength());
            System.out.println("--- Files on Server ---");
            System.out.println(list);
        } catch (SocketTimeoutException e) {
            System.out.println("[Error] Server timed out");
        } catch (IOException e) {
            System.out.println("[Error] e: " + e.getMessage());
        }
    }

    private static void downloadFile(DatagramSocket socket, InetAddress address, String filename) {
        try {
            // Ask server for file metadata (existence/chunks)
            long totalChunks = -1;
            for (int retries = 0; retries < MAX_RETRIES; retries++) {
                try {
                    sendPacket(socket, address, "INFO " + filename);

                    byte[] buffer = new byte[1024];
                    DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                    socket.receive(response);

                    String res = new String(response.getData(), 0, response.getLength()).trim();
                    System.out.println(res);
                    if (res.startsWith("ok ")) {
                        totalChunks = Long.parseLong(res.split(" ")[1]);
                        break; // Success, exit retry loop
                    } else if (res.equals("error")) {
                        System.out.println("[ERROR] File not found on server");
                        return;
                    }
                } catch (SocketTimeoutException e) {
                    System.out.println("[Warning] Timeout, retry (" + (retries + 1) + "/" + MAX_RETRIES + ")");
                }
            }

            if (totalChunks == -1) {
                System.out.println("[ERROR] Failed to contact server for file info");
                return;
            }

            System.out.println("File found, size: " + totalChunks + " chunks");
            System.out.println("Start receiving");

            StringBuilder completeContent = new StringBuilder();

            for (int i = 0; i < totalChunks; i++) {
                boolean received = false;
                int retries = 0;

                while (!received && retries < MAX_RETRIES) {
                    try {
                        sendPacket(socket, address, "FETCH " + filename + " " + i);

                        // Buffer needs to hold Header (4 bytes) + Data (1024 bytes)
                        byte[] dataBuffer = new byte[CHUNK_SIZE + HEADER_SIZE];
                        DatagramPacket dataPacket = new DatagramPacket(dataBuffer, dataBuffer.length);
                        socket.receive(dataPacket);

                        // Wrap received bytes in ByteBuffer to read the integer header
                        ByteBuffer wrapped = ByteBuffer.wrap(dataPacket.getData(), 0, dataPacket.getLength());

                        // Read the first 4 bytes as int
                        int receivedSeqNum = wrapped.getInt();

                        // Verify Sequence Number
                        if (receivedSeqNum == i) {
                            // Correct packet, extract data starting from offset 4
                            String chunkContent = new String(dataPacket.getData(), 4, dataPacket.getLength() - 4);
                            completeContent.append(chunkContent);
                            received = true;
                        } else {
                            // Received wrong chunk like delayed packet from previous retry, just gnore it
                            System.err.println(" [Warning] Ignored duplicate or wrong chunk: " + receivedSeqNum + ", expected: " + i);
                        }

                    } catch (SocketTimeoutException e) {
                        retries++;
                        System.out.println("[Warning] Timeout chunk " + i + ", retry (" + retries + "/" + MAX_RETRIES + ")");
                    }
                }

                if (!received) {
                    System.out.println("[Error] Failed to retrieve chunk " + i);
                    return;
                }
            }

            System.out.println("\n--- Start of File: " + filename + " ---");
            System.out.println(completeContent.toString());
            System.out.println("--- End of File ---\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}