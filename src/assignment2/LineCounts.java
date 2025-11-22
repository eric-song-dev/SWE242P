package assignment2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class LineCounts {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java assignment2.LineCounts <file1> <file2> ...");
            return;
        }

        // Loop through each filename provided in the command line arguments
        for (String filename : args) {
            try {
                int count = countLines(filename);
                System.out.println(filename + ": " + count);
            } catch (IOException e) {
                System.err.println("[Error] reading " + filename + " failed, e: " + e.getMessage());
            }
        }
    }

    private static int countLines(String filename) throws IOException {
        int lines = 0;
        // Try-with-resources
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            // Read the file line by line until the end is reached (End Of File / EOF)
            while (reader.readLine() != null) {
                lines++;
            }
        }

        return lines;
    }
}