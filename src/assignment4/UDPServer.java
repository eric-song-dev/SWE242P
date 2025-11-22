package assignment4;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UDPServer {
    private static final int PORT = 12345;
    private static final int CHUNK_SIZE = 1024;
    private static final int HEADER_SIZE = 4;
    private static File directory;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java assignment4.UDPServer <directory_path>");
            return;
        }

        directory = new File(args[0]);
        if (!directory.exists() || !directory.isDirectory()) {
            System.out.println("[Error] Invalid directory");
            return;
        }

        System.out.println("Server started on port " + PORT);
        System.out.println("Files directory: " + directory.getAbsolutePath());

        ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            while (true) {
                // Allocate new buffer for each request to ensure thread safety
                byte[] receiveBuffer = new byte[1024];
                DatagramPacket requestPacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                socket.receive(requestPacket);

                threadPool.execute(() -> {
                    try {
                        String command = new String(requestPacket.getData(), 0, requestPacket.getLength(), StandardCharsets.UTF_8).trim();
                        System.out.println("Received: " + command);

                        InetAddress clientAddress = requestPacket.getAddress();
                        int clientPort = requestPacket.getPort();

                        // Handle Commands
                        if (command.equals("INDEX")) {
                            handleIndex(socket, clientAddress, clientPort);
                        } else if (command.startsWith("INFO ")) {
                            String filename = command.substring(5).trim();
                            handleFileInfo(socket, clientAddress, clientPort, filename);
                        } else if (command.startsWith("FETCH ")) {
                            String[] parts = command.split(" ");
                            if (parts.length == 3) {
                                String filename = parts[1];
                                int chunkId = Integer.parseInt(parts[2]);
                                handleFileChunk(socket, clientAddress, clientPort, filename, chunkId);
                            } else {
                                System.out.println("[Error] Command format wrong");
                            }
                        } else {
                            sendStringResponse(socket, clientAddress, clientPort, "Unknown command");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendStringResponse(DatagramSocket socket, InetAddress address, int port, String message) throws IOException {
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        socket.send(packet);
    }

    private static void handleIndex(DatagramSocket socket, InetAddress address, int port) throws IOException {
        File[] files = directory.listFiles();
        StringBuilder sb = new StringBuilder();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    sb.append(f.getName()).append("\n");
                }
            }
        }
        // Send the list. Note: If list is huge, this might exceed UDP limit,
        // but for this assignment assuming list fits in one packet is usually acceptable.
        sendStringResponse(socket, address, port, sb.toString());
    }

    // Handle INFO command: Check if file exists and calculate total chunks
    private static void handleFileInfo(DatagramSocket socket, InetAddress address, int port, String filename) throws IOException {
        File file = new File(directory, filename);
        if (file.exists() && file.isFile()) {
            long fileSize = file.length();
            // Calculate total chunks needed, equal to ceiling division
            long totalChunks = (fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE;
            sendStringResponse(socket, address, port, "ok " + totalChunks);
        } else {
            sendStringResponse(socket, address, port, "error");
        }
    }

    // Handle FETCH command: Read specific chunk from file
    private static void handleFileChunk(DatagramSocket socket, InetAddress address, int port, String filename, int chunkId) throws IOException {
        File file = new File(directory, filename);
        if (!file.exists()) return;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long offset = (long) chunkId * CHUNK_SIZE;
            raf.seek(offset);

            byte[] fileBuffer = new byte[CHUNK_SIZE];
            int bytesRead = raf.read(fileBuffer);

            if (bytesRead > 0) {
                // Allocate buffer: 4 bytes for Header (Sequence Number) + File Data
                ByteBuffer packetBuffer = ByteBuffer.allocate(HEADER_SIZE + bytesRead);

                // Put Header: Write the chunkId as an integer (4 bytes)
                packetBuffer.putInt(chunkId);

                // Put Data: Write the actual file content
                packetBuffer.put(fileBuffer, 0, bytesRead);

                byte[] dataToSend = packetBuffer.array();

                DatagramPacket packet = new DatagramPacket(dataToSend, dataToSend.length, address, port);
                socket.send(packet);
            }
        }
    }
}
