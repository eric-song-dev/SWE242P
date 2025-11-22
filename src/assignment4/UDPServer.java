package assignment4;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
 * 1. Create DatagramSocket (bind port)
 * 2. Recv/Send
 * 3. Close
 */

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
            System.err.println("[Error] Invalid directory");
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

                threadPool.execute(() -> handleClientRequest(socket, requestPacket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClientRequest(DatagramSocket socket, DatagramPacket requestPacket) {
        try {
            String command = new String(requestPacket.getData(), 0, requestPacket.getLength(), StandardCharsets.UTF_8).trim();
            System.out.println("Received: " + command);

            InetAddress clientAddress = requestPacket.getAddress();
            int clientPort = requestPacket.getPort();

            // Handle Commands
            if (command.equals("INDEX")) {
                handleIndexInfo(socket, clientAddress, clientPort);
            } else if (command.startsWith("FETCH_INDEX ")) {
                try {
                    int chunkId = Integer.parseInt(command.split(" ")[1]);
                    handleIndexChunk(socket, clientAddress, clientPort, chunkId);
                } catch (Exception e) {
                    System.err.println("[Error] Invalid FETCH_INDEX command");
                }
            } else if (command.startsWith("INFO ")) {
                String filename = command.substring(5).trim();
                handleFileInfo(socket, clientAddress, clientPort, filename);
            } else if (command.startsWith("FETCH_FILE ")) {
                String[] parts = command.split(" ");
                if (parts.length == 3) {
                    String filename = parts[1];
                    int chunkId = Integer.parseInt(parts[2]);
                    handleFileChunk(socket, clientAddress, clientPort, filename, chunkId);
                } else {
                    System.err.println("[Error] Invalid FETCH_FILE command");
                }
            } else {
                sendStringResponse(socket, clientAddress, clientPort, "Unknown command");
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

    private static String getFileListString() {
        File[] files = directory.listFiles();
        StringBuilder sb = new StringBuilder();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    sb.append(f.getName()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    // Handle INFO command: Calculate chunks for the index string
    private static void handleIndexInfo(DatagramSocket socket, InetAddress address, int port) throws IOException {
        byte[] listBytes = getFileListString().getBytes(StandardCharsets.UTF_8);
        long totalChunks = (listBytes.length + CHUNK_SIZE - 1) / CHUNK_SIZE;
        sendStringResponse(socket, address, port, "ok " + totalChunks);
    }

    // Handle FETCH_INDEX command: Send a specific chunk of the index string
    private static void handleIndexChunk(DatagramSocket socket, InetAddress address, int port, int chunkId) throws IOException {
        byte[] listBytes = getFileListString().getBytes(StandardCharsets.UTF_8);

        int start = chunkId * CHUNK_SIZE;
        if (start >= listBytes.length) return; // Out of bounds

        int length = Math.min(CHUNK_SIZE, listBytes.length - start);

        ByteBuffer packetBuffer = ByteBuffer.allocate(HEADER_SIZE + length);
        packetBuffer.putInt(chunkId);
        packetBuffer.put(listBytes, start, length);

        byte[] dataToSend = packetBuffer.array();
        DatagramPacket packet = new DatagramPacket(dataToSend, dataToSend.length, address, port);
        socket.send(packet);
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

    // Handle FETCH_FILE command: Send a specific chunk of the file
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

                // START: DEBUG / TESTING SECTION
                // ---------------------------------------------------------

                // OPTION 1: Simulate Packet Loss (Recommended for testing reliability)
                // Set probability to 0.3 (30% loss) or 0.5 (50% loss)
                /*
                if (Math.random() < 0.3) {
                    System.out.println("[DEBUG] Simulating PACKET LOSS for Chunk " + chunkId);
                    return; // Do not send the packet, forcing client to timeout
                }
                */

                // OPTION 2: Simulate Network Latency (Delay)
                // Client timeout is 2000ms, so we sleep for 2500ms to force timeout
                /*
                try {
                    System.out.println("[DEBUG] Simulating LATENCY for Chunk " + chunkId);
                    Thread.sleep(2500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                */

                // ---------------------------------------------------------
                // END: DEBUG / TESTING SECTION

                socket.send(packet);
            }
        }
    }
}