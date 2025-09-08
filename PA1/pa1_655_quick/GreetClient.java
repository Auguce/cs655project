import java.net.*;
import java.io.*;
import java.util.Scanner;

public class GreetClient {
    // 现有成员变量
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;

    // 新增成员变量
    private BufferedWriter rttWriter;
    private BufferedWriter tputWriter;

    // Start connection to the specified server and port
    public void startConnection(String ip, int port) throws IOException {
        clientSocket = new Socket(ip, port);
        System.out.println("Connected to server " + ip + ":" + port);

        out = new PrintWriter(clientSocket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

        rttWriter = new BufferedWriter(new FileWriter("rtt_results.txt", true)); // 追加模式
        tputWriter = new BufferedWriter(new FileWriter("throughput_results.txt", true)); // 追加模式
    }

    // Send a message and receive the server's response
    public String sendMessage(String msg) throws IOException {
        out.println(msg);
        String resp = in.readLine();
        return resp;
    }

    // Stop the connection and close all resources
    public void stopConnection() throws IOException {
        try {
            if (in != null)
                in.close();
            if (out != null)
                out.close();
            if (clientSocket != null && !clientSocket.isClosed())
                clientSocket.close();
            System.out.println("Connection closed.");

            // 关闭文件写入对象
            if (rttWriter != null)
                rttWriter.close();
            if (tputWriter != null)
                tputWriter.close();
        } catch (IOException e) {
            System.err.println("Error occurred while closing resources: " + e.getMessage());
        }
    }

    // Main function, start the client and handle user input
    public static void main(String[] args) {
        GreetClient client = new GreetClient();
        String ip = "127.0.0.1"; // Default IP
        int port = 6666; // Default port

        // Optional: get IP and port number from command line arguments
        if (args.length > 0) {
            ip = args[0];
        }
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default port 6666");
            }
        }

        try {
            Scanner scanner = new Scanner(System.in);
            client.startConnection(ip, port);
            System.out.println("Please enter 'hello server' to establish connection");
            String userInput = scanner.nextLine();
            while (!userInput.equals("hello server")) {
                System.out.println("Please enter 'hello server'");
                userInput = scanner.nextLine(); // Added to allow user to retry
            }
            // Connection establishment phase
            String initialResponse = client.sendMessage("hello server");
            if ("hello client".equalsIgnoreCase(initialResponse.trim())) {
                System.out.println("Received server response: " + initialResponse);
            } else {
                System.out.println("Did not receive expected server response, closing connection.");
                client.stopConnection();
                return;
            }

            // Command input phase

            System.out.println(
                    "Please enter command parameters (format: <PROTOCOL PHASE> <MEASUREMENT TYPE> <NUMBER OF PROBES> <MESSAGE SIZE> <SERVER DELAY>)");
            System.out.println("For example: s rtt 10 100 50");
            System.out.println("Enter 't' to end the connection.");

            while (true) {
                System.out.print("> ");
                userInput = scanner.nextLine();

                // Send command to server
                String response = client.sendMessage(userInput);
                System.out.println("Server reply: " + response);
                if (response.equals("404 ERROR: Invalid Connection Setup Message")) {
                    client.stopConnection();
                    break;
                }
                if ("t".equalsIgnoreCase(userInput.trim())) {
                    break;
                }
                if (response.equals("200 OK:Ready")) {
                    System.out.println("You can start testing, enter 'continue' to proceed");
                } else {
                    System.out.println("Failed to establish connection with server");
                    continue;
                }
                String temp = userInput;
                // Check if need to enter measurement phase
                String[] tokens = temp.trim().split("\\s+");
                if (tokens.length == 5 && "s".equalsIgnoreCase(tokens[0])) {
                    String measurementType = tokens[1].toLowerCase();
                    int numberOfProbes;
                    int messageSize;
                    int serverDelay;

                    try {
                        numberOfProbes = Integer.parseInt(tokens[2]);
                        messageSize = Integer.parseInt(tokens[3]);
                        serverDelay = Integer.parseInt(tokens[4]);
                    } catch (NumberFormatException e) {
                        System.err.println("Numeric parameter format error.");
                        continue;
                    }
                    if ("rtt".equals(measurementType)) {
                        boolean feedback = performRTT(client, numberOfProbes, messageSize, serverDelay, scanner);
                        if (!feedback) {
                            break;
                        }
                    } else if ("tput".equals(measurementType)) {
                        boolean feedback = performTput(client, numberOfProbes, messageSize, serverDelay, scanner);
                        if (!feedback) {
                            break;
                        }
                    } else {
                        System.out.println("Unknown measurement type.");
                    }
                }

                // Check if need to end connection
            }

            scanner.close();
            client.stopConnection();
        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }

    // Perform RTT measurement
    private static String[] checkMPCommond(GreetClient client, Scanner scanner) throws IOException {
        System.out.println(
                "Please enter input in the form <PROTOCOL PHASE><WS><PROBE SEQUENCE NUMBER><WS><PAYLOAD> for MP testing");
        String userInput = scanner.nextLine();
        String[] tokens = userInput.trim().split("\\s+");
        while (true) {
            if (tokens.length == 3 && "m".equalsIgnoreCase(tokens[0])) {
                String measurementType = tokens[1].toLowerCase();
                int numberOfProbes;
                int messageSize;
                int serverDelay;

                try {
                    numberOfProbes = Integer.parseInt(tokens[1]);
                    messageSize = Integer.parseInt(tokens[2]);
                    return tokens;
                } catch (NumberFormatException e) {
                    System.err.println("Numeric parameter format error.");
                }

            } else {
                System.err.println(
                        "Numeric parameter format error. Please enter input in the form <PROTOCOL PHASE><WS><PROBE SEQUENCE NUMBER><WS><PAYLOAD>");
                System.err.println("Example input: m 1 20");
            }
            userInput = scanner.nextLine();
            tokens = userInput.trim().split("\\s+");
        }
    }

    // Perform RTT measurement
    private static Boolean performRTT(GreetClient client, int probes, int size, int delay, Scanner scanner)
            throws IOException {

        // 写入开始测量的指示文本
        String startMessage = "Starting RTT measurement: Number of probes=" + probes + ", message size="
                + size + " bytes, server delay=" + delay + " ms";
        client.rttWriter.write(startMessage);
        client.rttWriter.newLine();
        client.rttWriter.flush();

        System.out.println("Starting RTT measurement: Number of probes=" + probes + ", message size=" + size
                + " bytes, server delay=" + delay + " ms");
        double[] rttTimes = new double[probes];
        for (int i = 0; i < probes; i++) {
            String probeMessage = "probe " + (i + 1) + " " + generateMessage(size);
            long startTime = System.nanoTime();
            String response = client.sendMessage(probeMessage);
            long endTime = System.nanoTime();
            rttTimes[i] = (endTime - startTime) / 1000000.0; // 转换为毫秒
            if (response.equals("404 ERROR: Invalid Measurement Message")) {
                System.out.println("Received message: 404 ERROR: Invalid Measurement Message");
                return false;
            }
            // 将结果写入文件
            if (response != null && !response.isEmpty()) {
                String result = "Probe " + (i + 1) + ": RTT = " + rttTimes[i] + " ms";
                client.rttWriter.write(result);
                client.rttWriter.newLine();
                // 可选：不打印到终端
            } else {
                String result = "Probe " + (i + 1) + ": No reply received | RTT: N/A";
                client.rttWriter.write(result);
                client.rttWriter.newLine();
                // 可选：不打印到终端
            }
        }
        client.out.println("RTT test ended");
        // 计算平均RTT
        double totalRTT = 0;
        for (double rtt : rttTimes) {
            totalRTT += rtt;
        }
        double averageRTT = totalRTT / probes;
        String response = client.in.readLine();
        // 将平均RTT写入文件
        String avgResult = "Average RTT for message size " + size + " bytes: " + averageRTT + " ms";
        client.rttWriter.write(avgResult);
        client.rttWriter.newLine();
        client.rttWriter.flush(); // 刷新缓冲区

        // 写入结束测量的指示文本
        String endMessage = "RTT measurement completed for message size " + size + " bytes.";
        client.rttWriter.write(endMessage);
        client.rttWriter.newLine();
        client.rttWriter.flush();

        System.out.println("RTT measurement completed for message size " + size + " bytes.");
        return true;
    }

    // Perform Throughput measurement
    private static boolean performTput(GreetClient client, int probes, int size, int delay, Scanner scanner)
            throws IOException {
        // 写入开始测量的指示文本
        String startMessage = "Starting Throughput measurement: Number of probes=" + probes + ", message size="
                + size + " bytes, server delay=" + delay + " ms";
        client.tputWriter.write(startMessage);
        client.tputWriter.newLine();
        client.tputWriter.flush();

        System.out.println("Starting Throughput measurement: Number of probes=" + probes + ", message size="
                + size + " bytes, server delay=" + delay + " ms");

        long startTime = System.nanoTime();

        for (int i = 0; i < probes; i++) {
            String probeMessage = "probe " + (i + 1) + " " + generateMessage(size);
            String response = client.sendMessage(probeMessage);
            if (response.equals("404 ERROR: Invalid Measurement Message")) {
                System.out.println("Received message: 404 ERROR: Invalid Measurement Message");
                return false;
            }
            // 不需要打印每个探测的回复，可以省略
        }
        long endTime = System.nanoTime();
        double duration = (endTime - startTime) / 1000000.0; // 转换为毫秒

        // 计算吞吐量 (Mbps)
        long totalBytes = (long) probes * size;
        double totalBits = totalBytes * 8;
        double durationSeconds = duration / 1000.0;
        double tputMbps = (totalBits / durationSeconds) / 1_000_000.0;

        client.out.println("throughput measurement completed");
        String response = client.in.readLine();

        // 将吞吐量结果写入文件
        String result = "Throughput for message size " + size + " bytes: " + tputMbps + " Mbps";
        client.tputWriter.write(result);
        client.tputWriter.newLine();
        client.tputWriter.flush(); // 刷新缓冲区

        // 写入结束测量的指示文本
        String endMessage = "Throughput measurement completed for message size " + size + " bytes.";
        client.tputWriter.write(endMessage);
        client.tputWriter.newLine();
        client.tputWriter.flush();

        System.out.println("Throughput measurement completed for message size " + size + " bytes.");
        return true;
    }

    // Generate message content of specified size
    private static String generateMessage(int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            sb.append("a"); // Generate repeated 'a' characters
        }
        return sb.toString();
    }
}
