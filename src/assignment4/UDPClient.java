package assignment4;

import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

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
            // Enable timeout for receive() calls
            socket.setSoTimeout(TIMEOUT);
            InetAddress serverAddress = InetAddress.getByName(SERVER_ADDRESS);

            while (true) {
                System.out.print("\nEnter command: ");
                String input = scanner.nextLine();

                if (input.equalsIgnoreCase("exit")) break;

                if (input.equals("index")) {
                    requestIndex(socket, serverAddress);
                } else if (input.startsWith("get ")) {
                    String filename = input.substring(4).trim();
                    getFile(socket, serverAddress, filename);
                } else {
                    System.out.println("[Warning] Unexpected command");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendPacket(DatagramSocket socket, InetAddress address, String text) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length, address, SERVER_PORT);
        socket.send(packet);
    }

    private static void requestIndex(DatagramSocket socket, InetAddress address) {
        try {
            System.out.println("Request file list");
            // Info command is "INDEX", Fetch command is "FETCH_INDEX <id>"
            byte[] data = receiveDataReliably(socket, address, "INDEX", "FETCH_INDEX");

            if (data != null) {
                String list = new String(data, StandardCharsets.UTF_8);
                System.out.println("--- Files on Server ---");
                System.out.println(list);
                System.out.println("-----------------------");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void getFile(DatagramSocket socket, InetAddress address, String filename) {
        try {
            System.out.println("Request file: " + filename);
            // Info command is "INFO <filename>", Fetch command is "FETCH_FILE <filename> <id>"
            byte[] data = receiveDataReliably(socket, address, "INFO " + filename, "FETCH_FILE " + filename);

            if (data != null) {
                System.out.println("\n--- Start of File: " + filename + " ---");
                System.out.println(new String(data, StandardCharsets.UTF_8));
                System.out.println("--- End of File ---\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Stop-and-Wait protocol, used by both Index and Get File
    private static byte[] receiveDataReliably(DatagramSocket socket, InetAddress address, String infoCommand, String fetchCommand) throws IOException {
        // Get meta info (existence/chunks)
        long totalChunks = -1;
        for (int retries = 0; retries < MAX_RETRIES; retries++) {
            try {
                sendPacket(socket, address, infoCommand);

                byte[] buffer = new byte[1024];
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                socket.receive(response);

                String res = new String(response.getData(), 0, response.getLength(), StandardCharsets.UTF_8).trim();

                if (res.startsWith("ok ")) {
                    totalChunks = Long.parseLong(res.split(" ")[1]);
                    break;
                } else if (res.equals("error")) {
                    System.out.println("[ERROR] File not found");
                    return null;
                }
            } catch (SocketTimeoutException e) {
                System.out.println("[Warning] Timeout waiting for info, retry (" + (retries + 1) + "/" + MAX_RETRIES + ")");
            }
        }

        if (totalChunks == -1) {
            System.out.println("[ERROR] Failed to get metadata");
            return null;
        }

        System.out.println("Size: " + totalChunks + " chunks. Start receiving");

        // Fetch chunks
        ByteArrayOutputStream completeBytes = new ByteArrayOutputStream();

        for (int i = 0; i < totalChunks; i++) {
            boolean received = false;
            int retries = 0;

            while (!received && retries < MAX_RETRIES) {
                try {
                    // Construct fetch command: e.g., "FETCH_INDEX 0" or "FETCH_FILE file.txt 0"
                    String cmd = fetchCommand + " " + i;
                    sendPacket(socket, address, cmd);

                    byte[] dataBuffer = new byte[CHUNK_SIZE + HEADER_SIZE];
                    DatagramPacket dataPacket = new DatagramPacket(dataBuffer, dataBuffer.length);
                    socket.receive(dataPacket);

                    ByteBuffer wrapped = ByteBuffer.wrap(dataPacket.getData(), 0, dataPacket.getLength());

                    // Read the first 4 bytes as int
                    int receivedSeqNum = wrapped.getInt();

                    // Verify sequence number
                    if (receivedSeqNum == i) {
                        // Correct packet, extract data starting from offset 4
                        completeBytes.write(dataPacket.getData(), HEADER_SIZE, dataPacket.getLength() - HEADER_SIZE);
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
                return null;
            }
        }

        return completeBytes.toByteArray();
    }
}