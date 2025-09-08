import java.net.*;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GreetServer {
    private ServerSocket serverSocket;
    private ExecutorService executorService;

    // Start the server and listen on the specified port
    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("Server started, listening on port " + port);

        // Create a thread pool with a fixed number of threads
        executorService = Executors.newFixedThreadPool(10); // Adjust the pool size as needed

        // Continuously listen for client connections
        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected: " + clientSocket.getInetAddress());

            // Submit a new client handler to the thread pool
            executorService.submit(new ClientHandler(clientSocket));
        }
    }

    // Stop the server and shutdown the thread pool
    public void stop() throws IOException {
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
            System.out.println("Server stopped.");
        }
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            System.out.println("Thread pool shut down.");
        }
    }

    // Main function, start the server
    public static void main(String[] args) {
        GreetServer server = new GreetServer();
        int port = 6666; // Default port

        // Optional: get port number from command line arguments
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default port 6666");
            }
        }

        try {
            server.start(port);
        } catch (IOException e) {
            System.err.println("Server failed to start: " + e.getMessage());
        }
    }

    // Inner class to handle client connections
    private static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                String message;

                // Connection establishment phase
                message = in.readLine();
                if (message == null) {
                    System.out.println("Client disconnected before sending a message.");
                    closeResources();
                    return;
                }

                if ("hello server".equalsIgnoreCase(message.trim())) {
                    out.println("hello client");
                    System.out.println("Received 'hello server', replied 'hello client'");
                } else {
                    out.println("unrecognised greeting");
                    System.out.println("Received unrecognized greeting, replied 'unrecognised greeting'");
                    closeResources();
                    return;
                }

                // Command processing phase
                while ((message = in.readLine()) != null) {
                    System.out.println("Received client command: " + message);
                    if ("t".equalsIgnoreCase(message.trim())) {
                        out.println("200:OK:Closing Connection");
                        System.out.println("Received 't', closing connection.");
                        break;
                    }

                    // Parse command parameters
                    String[] tokens = message.trim().split("\\s+");
                    if (tokens.length < 5) {
                        out.println("404 ERROR: Invalid Connection Setup Message");
                        System.out.println("Invalid protocol phase, replied '404 ERROR: Invalid Connection Setup Message'");
                        break;
                    }

                    String protocolPhase = tokens[0];
                    String measurementType = tokens[1].toLowerCase();
                    int numberOfProbes;
                    int messageSize;
                    int serverDelay;

                    // Validate parameters
                    if (!"s".equals(protocolPhase)) {
                        out.println("404 ERROR: Invalid Connection Setup Message");
                        System.out.println("Invalid protocol phase, replied '404 ERROR: Invalid Connection Setup Message'");
                        break;
                    }

                    if (!measurementType.equals("rtt") && !measurementType.equals("tput")) {
                        out.println("404 ERROR: Invalid Connection Setup Message");
                        System.out.println("Invalid measurement type, replied '404 ERROR: Invalid Connection Setup Message'");
                        break;
                    }

                    try {
                        numberOfProbes = Integer.parseInt(tokens[2]);
                        messageSize = Integer.parseInt(tokens[3]);
                        serverDelay = Integer.parseInt(tokens[4]);
                    } catch (NumberFormatException e) {
                        out.println("404 ERROR: Invalid Connection Setup Message");
                        System.out.println("Numeric parameter format error, replied '404 ERROR: Invalid Connection Setup Message'");
                        break;
                    }

                    // Perform measurement operation
                    boolean end = false;
                    if (measurementType.equals("rtt")) {
                        end = handleRTT(numberOfProbes, messageSize, serverDelay);
                    } else if (measurementType.equals("tput")) {
                        end = handleTput(numberOfProbes, messageSize, serverDelay);
                    }
                    if (!end) {
                        break;
                    }
                }

                closeResources();
            } catch (IOException e) {
                System.err.println("Client handler error: " + e.getMessage());
            }
        }

        // Handle RTT measurement
        private boolean handleRTT(int probes, int size, int delay) throws IOException {
            out.println("200 OK:Ready");
            System.out.println("Starting RTT measurement: Number of probes=" + probes + ", message size=" + size + " bytes, server delay=" + delay + " ms");
            String response = in.readLine();

            for (int i = 0; i < probes; i++) {
                if (response == null) {
                    System.out.println("Client disconnected.");
                    return false;
                }

                String[] tokens = response.trim().split("\\s+");
                if (tokens.length < 3) {
                    System.out.println("Invalid measurement message format.");
                    out.println("404 ERROR: Invalid Measurement Message");
                    return false;
                }

                if (!tokens[1].equals(String.valueOf(i + 1))) {
                    System.out.println("Sequence number mismatch. Should be " + (i + 1) + ", but was " + tokens[1]);
                    out.println("404 ERROR: Invalid Measurement Message");
                    return false;
                }

                if (tokens[2].length() != size) {
                    System.out.println("Data length mismatch.");
                    out.println("404 ERROR: Invalid Measurement Message");
                    return false;
                }

                String probeMessage = "probe " + (i + 1) + " " + tokens[2];
                out.println(probeMessage);
                System.out.println("Sent: " + probeMessage);

                response = in.readLine();
                if (response == null) {
                    System.out.println("Client disconnected.");
                    return false;
                }
                System.out.println("Received reply: " + response);
            }

            out.println("rtt measurement completed");
            System.out.println("RTT measurement completed, replied 'rtt measurement completed'");
            return true;
        }

        // Handle Throughput measurement
        private boolean handleTput(int probes, int size, int delay) throws IOException {
            out.println("200 OK:Ready");
            System.out.println("Starting throughput measurement: Number of probes=" + probes + ", message size=" + size + " bytes, server delay=" + delay + " ms");
            String response = in.readLine();

            for (int i = 0; i < probes; i++) {
                if (response == null) {
                    System.out.println("Client disconnected.");
                    return false;
                }

                String[] tokens = response.trim().split("\\s+");
                if (tokens.length < 3) {
                    System.out.println("Invalid measurement message format.");
                    out.println("404 ERROR: Invalid Measurement Message");
                    return false;
                }

                if (!tokens[1].equals(String.valueOf(i + 1))) {
                    System.out.println("Sequence number mismatch. Should be " + (i + 1) + ", but was " + tokens[1]);
                    out.println("404 ERROR: Invalid Measurement Message");
                    return false;
                }

                if (tokens[2].length() != size) {
                    System.out.println("Data length mismatch.");
                    out.println("404 ERROR: Invalid Measurement Message");
                    return false;
                }

                String probeMessage = "probe " + (i + 1) + " " + tokens[2];
                out.println(probeMessage);
                System.out.println("Sent: " + probeMessage);

                response = in.readLine();
                if (response == null) {
                    System.out.println("Client disconnected.");
                    return false;
                }
                System.out.println("Received reply: " + response);
            }

            out.println("throughput measurement completed");
            System.out.println("Throughput measurement completed, replied 'throughput measurement completed'");
            return true;
        }

        // Generate message content of specified size
        private String generateMessage(int size) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < size; i++) {
                sb.append("a"); // Generate repeated 'a' characters
            }
            return sb.toString();
        }

        // Close all resources associated with the client
        private void closeResources() {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
                System.out.println("Closed connection with client: " + clientSocket.getInetAddress());
            } catch (IOException e) {
                System.err.println("Error closing resources: " + e.getMessage());
            }
        }
    }
}
