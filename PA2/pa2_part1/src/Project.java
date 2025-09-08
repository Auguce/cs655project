import java.io.*;

public class Project {
    public final static void main(String[] argv) {
        StudentNetworkSimulator simulator;

        // 重定向 System.out 到控制台和文件
        try {
            // 创建文件输出流，文件名为 "simulation_output.txt"
            FileOutputStream fos = new FileOutputStream("simulation_output.txt");

            // 创建 TeeOutputStream，将输出同时写入控制台和文件
            TeeOutputStream tos = new TeeOutputStream(System.out, fos);

            // 创建 PrintStream 并设置为 System.out
            PrintStream ps = new PrintStream(tos);

            // 设置 System.out 和 System.err
            System.setOut(ps);
            System.setErr(ps);

        } catch (IOException e) {
            System.err.println("无法创建输出文件: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        // 以下是用户提供的 Project 类的其余代码

        int nsim = -1;
        double loss = -1;
        double corrupt = -1;
        double delay = -1;
        int trace = -1;
        int seed = -1;
        int windowsize = -1;
        double timeout = -1;
        String buffer = "";
        File outputfile = new File("OutputFile");
        BufferedReader stdIn = new BufferedReader(
                new InputStreamReader(System.in));

        System.out.println("-- * Network Simulator v1.0 * --");

        while (nsim < 1) {
            System.out.print("Enter number of messages to simulate (> 0): " +
                    "[10] ");
            try {
                buffer = stdIn.readLine();
                System.out.println("");
            } catch (IOException ioe) {
                System.out.println("IOError reading your input!");
                System.exit(1);
            }

            if (buffer.equals("")) {
                nsim = 10;
            } else {
                try {
                    nsim = Integer.parseInt(buffer);
                } catch (NumberFormatException nfe) {
                    nsim = -1;
                }
            }
        }

        while (loss < 0) {
            System.out.print("Enter packet loss probability (0.0 for no " +
                    "loss): [0.0] ");
            try {
                buffer = stdIn.readLine();
                System.out.println("");
            } catch (IOException ioe) {
                System.out.println("IOError reading your input!");
                System.exit(1);
            }

            if (buffer.equals("")) {
                loss = 0;
            } else {
                try {
                    loss = (Double.valueOf(buffer)).doubleValue();
                } catch (NumberFormatException nfe) {
                    loss = -1;
                }
            }
        }

        while (corrupt < 0) {
            System.out.print("Enter packet corruption probability (0.0 " +
                    "for no corruption): [0.0] ");
            try {
                buffer = stdIn.readLine();
                System.out.println("");
            } catch (IOException ioe) {
                System.out.println("IOError reading your input!");
                System.exit(1);
            }

            if (buffer.equals("")) {
                corrupt = 0;
            } else {
                try {
                    corrupt = (Double.valueOf(buffer)).doubleValue();
                } catch (NumberFormatException nfe) {
                    corrupt = -1;
                }
            }
        }

        while (delay <= 0) {
            System.out.print("Enter average time between messages from " +
                    "sender's layer 5 (> 0.0): [1000] ");
            try {
                buffer = stdIn.readLine();
                System.out.println("");
            } catch (IOException ioe) {
                System.out.println("IOError reading your input!");
                System.exit(1);
            }

            if (buffer.equals("")) {
                delay = 1000;
            } else {
                try {
                    delay = (Double.valueOf(buffer)).doubleValue();
                } catch (NumberFormatException nfe) {
                    delay = -1;
                }
            }
        }

        while (windowsize < 1) {
            System.out.print("Enter window size (> 0): [8] ");
            try {
                buffer = stdIn.readLine();
                System.out.println("");
            } catch (IOException ioe) {
                System.out.println("IOError reading your input!");
                System.exit(1);
            }

            if (buffer.equals("")) {
                windowsize = 8;
            } else {
                try {
                    windowsize = Integer.parseInt(buffer);
                } catch (NumberFormatException nfe) {
                    windowsize = -1;
                }
            }
        }

        while (timeout <= 0) {
            System.out.print("Enter retransmission timeout (>0.0) [15.0] ");
            try {
                buffer = stdIn.readLine();
                System.out.println("");
            } catch (IOException ioe) {
                System.out.println("IOError reading your input!");
                System.exit(1);
            }

            if (buffer.equals("")) {
                timeout = 15.0;
            } else {
                try {
                    timeout = (Double.valueOf(buffer)).doubleValue();
                } catch (NumberFormatException nfe) {
                    timeout = -1;
                }
            }
        }

        while (trace < 0) {
            System.out.print("Enter trace level (>= 0): [0] ");
            try {
                buffer = stdIn.readLine();
                System.out.println("");
            } catch (IOException ioe) {
                System.out.println("IOError reading your input!");
                System.exit(1);
            }

            if (buffer.equals("")) {
                trace = 0;
            } else {
                try {
                    trace = Integer.parseInt(buffer);
                } catch (NumberFormatException nfe) {
                    trace = -1;
                }
            }
        }

        while (seed < 0) { // 修改条件以允许 seed = 0
            System.out.print("Enter random seed: [0] ");
            try {
                buffer = stdIn.readLine();
                System.out.println("");
            } catch (IOException ioe) {
                System.out.println("IOError reading your input!");
                System.exit(1);
            }

            if (buffer.equals("")) {
                seed = 0;
            } else {
                try {
                    seed = (Integer.valueOf(buffer)).intValue();
                } catch (NumberFormatException nfe) {
                    seed = -1;
                }
            }
        }

        simulator = new StudentNetworkSimulator(nsim, loss, corrupt, delay,
                trace, seed, windowsize, timeout);

        simulator.runSimulator();
    }
}
