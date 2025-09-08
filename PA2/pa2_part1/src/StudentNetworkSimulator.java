import java.util.*;
import java.io.*;

public class StudentNetworkSimulator extends NetworkSimulator {
    public static final int FirstSeqNo = 0;
    private int WindowSize;
    private double RxmtInterval;
    private int LimitSeqNo;
    private int currentSequenceNumber=0;

    // Sender (A) state variables
    private int base; // Sequence number of the oldest unacknowledged packet
    private int nextSeqNum; // Next sequence number to use
    private Map<Integer, Packet> window; // Map of sequence number to Packet
    private Queue<Message> bufferA; // Buffer for A_output
    private final int maxBufferSize = 100000000;
    private int retransmissions;
    private int lastAck=-1;

    // ACK tracking: Map of sequence number to boolean indicating if ACKed
    private Map<Integer, Boolean> ackedPackets;
    private Map<Integer, Double> packetSendTime;
    private Map<Integer, Boolean> isPacketRetransmiision;
    private double totalCommunicationTime = 0;
    private int totalCommunicationCount= 0;

    // Receiver (B) state variables
    private int expectedSeqNum; // Next expected sequence number
    private Map<Integer, Packet> bufferB; // Buffer for out-of-order packets

    private int delivered; // Number of packets delivered to layer5
    private int ackSent;

    // Statistics
    private int originalPackets; // Number of original packets sent by A
    private int lostPackets; // Number of packets lost
    private int corruptedPackets; // Number of corrupted packets
    private double totalRTT; // Total Round Trip Time
    private int rttCount; // Number of RTT measurements
    private double communicationTime; // Total communication time

    // Timer
    private boolean timerRunning;
    private int duplicateAckNum;
    private int duplicateAckCount = 0;
    private double timerEndTime;

    private int currentRTTPacket;

    private boolean iscurrentRTTPacketRetransmission;

    private double currentRTTPacketSendTime;
    private double totalRTTTime;
    private boolean doingRTTMeasurment;
    private int cnt = 0;
    // Constructor
    public StudentNetworkSimulator(int numMessages,
                                   double loss,
                                   double corrupt,
                                   double avgDelay,
                                   int trace,
                                   int seed,
                                   int winsize,
                                   double delay) {
        super(numMessages, loss, corrupt, avgDelay, trace, seed);
        WindowSize = winsize;
        LimitSeqNo = winsize * 2; // Assuming SR protocol
        RxmtInterval = delay;
        aInit();
        bInit();
    }

    // Initialize sender (A)
    protected void aInit() {
        base = FirstSeqNo;
        nextSeqNum = FirstSeqNo;
        window = new HashMap<>();
        bufferA = new LinkedList<>();
        ackedPackets = new HashMap<>();
        packetSendTime = new HashMap<>();
        isPacketRetransmiision = new HashMap<>();
        retransmissions = 0;
        originalPackets = 0;
        lostPackets = 0;
        corruptedPackets = 0;
        totalRTT = 0.0;
        rttCount = 0;
        communicationTime = 0.0;
        timerRunning = false;
        duplicateAckNum = -1;
        duplicateAckCount = 0;
    }

    // Initialize receiver (B)
    protected void bInit() {
        expectedSeqNum = FirstSeqNo;
        bufferB = new HashMap<>();
        delivered = 0;
        ackSent = 0;
    }

    // Sender: Handle new message from layer5
    protected void aOutput(Message message) {
        if (bufferA.size() >= maxBufferSize) {
            System.out.println("Buffer full at sender. Aborting.");
            System.exit(1);
        }

        // Create a copy of the message and enqueue it
        Message msgCopy = new Message(message.getData());
        bufferA.offer(msgCopy);
        System.out.println("Adding message to buffer, current size:"+bufferA.size());
        // Attempt to send packets within the window
        sendPackets();
    }

    // Sender: Attempt to send packets within the window
    private void sendPackets() {
        System.out.println("Try to send message: packet "+ nextSeqNum);
        while (isInSenderWindow(nextSeqNum) && !bufferA.isEmpty()) {
            Message msg = bufferA.poll();
            String data = msg.getData();

            // Create packet with sequence number, no ack, checksum, and payload
            int checksum = calculateChecksum(data);
            Packet pkt = new Packet(nextSeqNum, -1, checksum, data);
            // Add packet to the window
            window.put(nextSeqNum, pkt);
            double current_time = getTime();
            packetSendTime.put(nextSeqNum, current_time);
            isPacketRetransmiision.put(nextSeqNum,true);
            toLayer3(A, pkt);
            originalPackets++;
            if (traceLevel > 1) {
                System.out.println("A_output: Sent packet " + pkt.getSeqnum() + " current base"+base);
            }

            // Start timer if it's the first packet in the window
            if (base == nextSeqNum && !timerRunning) {
//                startTimer(A, RxmtInterval);
                timerRunning = true;
                timerEndTime = getTime() + RxmtInterval;
            }

            // Increment nextSeqNum with wrap-around
            nextSeqNum = (nextSeqNum + 1) % LimitSeqNo;
            stopTimer(A);
            startTimer(A,RxmtInterval);
        }
    }

    // Create checksum: sum of seqnum, acknum, and payload characters
    private int calculateChecksum(String payload) {
        int sum =0;
        for (char c : payload.toCharArray()) {
            sum += (int) c;
        }
        return sum;
    }
    private boolean checkDuplicate(int ackNum){
        if (ackNum == lastAck){
            duplicateAckCount+=1;
            if (duplicateAckCount >=1){
                if (traceLevel > 0) {
                    System.out.println("A_DuplicateInterrupt: Three duplicate ack. Try retransmit first unacknowledged packets.");
                }
                Packet pkt = window.get(base);
                if (pkt == null){
                    return true;
                }
                // Retransmit all packets in the window
                toLayer3(A, pkt);
                retransmissions++;
                if (traceLevel > 1) {
                    System.out.println("A_DuplicateInterrupt: Retransmitted packet " + pkt.getSeqnum()+ " current base: " +base);
                }
                isPacketRetransmiision.replace(pkt.getSeqnum(),false);
                // Restart the timer
                stopTimer(A);
                startTimer(A, RxmtInterval);
                timerEndTime = getTime() + RxmtInterval;
                return true;
            }
        }else{
            lastAck = ackNum;
            duplicateAckCount = 0;
            return false;
        }
        return true;
    }

    // Sender: Handle incoming ACK packet
    protected void aInput(Packet packet) {
        if (isCorrupted(packet)) {
            corruptedPackets++;
            if (traceLevel > 0) {
                System.out.println("A_input: Received corrupted ACK.");
            }
            return;
        }

        int ackNum = packet.getAcknum();
        if (traceLevel > 1) {
            System.out.println("A_input: Received ACK " + ackNum);
        }

        // Check if ACK is within the window
        if (checkDuplicate(ackNum)){
            return;
        }
        if (isInWindow(ackNum)) {
            // Update base to ackNum + 1
            int move = base;
            base = (ackNum + 1) % LimitSeqNo;
            if (base<move){
                move = base+LimitSeqNo-move;
            }else{
                move = base-move;
            }
            System.out.println("A_input: Received ACK " + ackNum + " make window shift "+move+ " current base: " +base);
            // Stop the timer if all packets are acknowledged
            if (base == nextSeqNum) {
                stopTimer(A);
                timerRunning = false;
            } else {
                // Restart the timer for the next unacknowledged packet
                stopTimer(A);
                startTimer(A, RxmtInterval);
                timerEndTime = getTime() + RxmtInterval;
            }
            double endTime = getTime();
            Iterator<Map.Entry<Integer, Double>> x = packetSendTime.entrySet().iterator();
            Iterator<Map.Entry<Integer, Boolean>> y = isPacketRetransmiision.entrySet().iterator();
            while (x.hasNext()) {
                Map.Entry<Integer, Double> entry = x.next();
                Map.Entry<Integer, Boolean> entry1 = y.next();
                int seq = entry.getKey();
                Double current_time = getTime();
                if (isSeqLessThanOrEqual(seq, ackNum)) {
                    if (seq == ackNum){
                        totalCommunicationTime+=current_time-entry.getValue();
                        totalCommunicationCount++;
                        if (entry1.getValue()){
                            totalRTTTime+=current_time-entry.getValue();
                            rttCount++;
                        }
                    }
                    x.remove();
                    y.remove();
                }
            }

            // Remove acknowledged packets from the window
            Iterator<Map.Entry<Integer, Packet>> it = window.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Packet> entry = it.next();
                int seq = entry.getKey();
                if (isSeqLessThanOrEqual(seq, ackNum)) {
                    System.out.println("ackNum:"+ackNum+" remove:"+seq);
                    it.remove();
                }
            }
            // Attempt to send more packets if window has moved
            sendPackets();
        }
//        else {
//            // Duplicate ACK: retransmit the first unacknowledged packet
//            if (ackNum != duplicateAckNum){
//                duplicateAckNum = ackNum;
//                duplicateAckCount = 1;
//            }
//            else{
//                duplicateAckCount+=1;
//                if (duplicateAckCount == 3){
//                    if (traceLevel > 0) {
//                        System.out.println("A_DuplicateInterrupt: Three duplicate ack. Try retransmissing first unacknowledged packets.");
//                    }
//                    Packet pkt = window.get(base);
//                    if (pkt == null){
//                        return;
//                    }
//                    // Retransmit all packets in the window
//                    toLayer3(A, pkt);
//                    cnt+=1;
//                    retransmissions++;
//                    if (traceLevel > 1) {
//                        System.out.println("A_DuplicateInterrupt: Retransmitted packet " + pkt.getSeqnum()+ " current base: " +base);
//                    }
//                    if(pkt.getSeqnum() == currentRTTPacket){
//                        iscurrentRTTPacketRetransmission = true;
//                    }
//                    // Restart the timer
//                    stopTimer(A);
//                    startTimer(A, RxmtInterval);
//                    timerEndTime = getTime() + RxmtInterval;
//                }
//            }
//        }
    }

    // Check if seq1 <= seq2 considering wrap-around
    private boolean isSeqLessThanOrEqual(int seq1, int seq2) {
        if (seq1 <= seq2) {
            return seq2-seq1<LimitSeqNo/2;
        } else {
            return seq2-seq1 > LimitSeqNo/2;
        }
    }

    // Check if ACK number is within the current window
    private boolean isInWindow(int ackNum) {
        if (base <= ackNum && ackNum < base + WindowSize) {
            return true;
        } else if (base + WindowSize >= LimitSeqNo) { // Wrap-around
            return ackNum < (base + WindowSize) % LimitSeqNo;
        }
        return false;
    }

    // Sender: Handle timer interrupt
    protected void aTimerInterrupt() {
        if (traceLevel > 0) {
            System.out.println("A_timerInterrupt: Timer expired. Retransmitting first unacknowledged packets.");
        }

        Packet pkt = window.get(base);
        // Retransmit all packets in the window
        toLayer3(A, pkt);
        cnt+=1;
        retransmissions++;
        if (traceLevel > 1) {
            System.out.println("A_timerInterrupt: Retransmitted packet " + pkt.getSeqnum()+ " current base: " +base);
        }
        isPacketRetransmiision.replace(pkt.getSeqnum(),false);

        // Restart the timer
        stopTimer(A);
        startTimer(A, RxmtInterval);
        timerEndTime = getTime() + RxmtInterval;
    }

    // Receiver: Handle incoming data packet
    protected void bInput(Packet packet) {
        System.out.println("B_input: Received packet with payload:"+packet.getPayload() + " checkSum: "+packet.getChecksum());
        if (isCorrupted(packet)) {
            corruptedPackets++;
            if (traceLevel > 0) {
                System.out.println("B_input: Received corrupted packet.");
            }
            // Send ACK for the last correctly received packet
//            sendACK((expectedSeqNum - 1 + LimitSeqNo) % LimitSeqNo,currentSequenceNumber);
//            ackSent++;
            return;
        }

        int seqNum = packet.getSeqnum();
        String payload = packet.getPayload();

        if (traceLevel > 1) {
            System.out.println("B_input: Received packet " + seqNum);
        }

        if (seqNum == expectedSeqNum) {
            // In-order packet
            toLayer5(payload);
            delivered++;
            if (traceLevel > 1) {
                System.out.println("B_input: Delivered packet " + seqNum + " to layer5 and sent ACK.");
            }
            expectedSeqNum = (expectedSeqNum + 1) % LimitSeqNo;
            int move = 1;
            // Check buffer for the next expected packets
            while (bufferB.containsKey(expectedSeqNum)) {
                Packet bufferedPkt = bufferB.remove(expectedSeqNum);
                toLayer5(bufferedPkt.getPayload());
                delivered++;
                move++;
                expectedSeqNum = (expectedSeqNum + 1) % LimitSeqNo;
            }
            int seq = expectedSeqNum - 1;
            if (seq<0){
                seq+=LimitSeqNo;
            }
            sendACK(seq,currentSequenceNumber);
            ackSent++;
            currentSequenceNumber++;
            currentSequenceNumber = currentSequenceNumber%LimitSeqNo;
            System.out.println("B_input: Received Packet " + seqNum + " make window shift "+ move+ " current base: "+expectedSeqNum);
        } else if (isInReceiverWindow(seqNum)) {
            // Out-of-order packet, buffer it
            if (!bufferB.containsKey(seqNum)) {
                bufferB.put(seqNum, packet);
                if (traceLevel > 1) {
                    System.out.println("B_input: Buffered out-of-order packet " + seqNum);
                }
            }
            // Send ACK for the last in-order packet
            sendACK((expectedSeqNum - 1 + LimitSeqNo) % LimitSeqNo,currentSequenceNumber);
            ackSent++;
        } else {
            // Duplicate or outside window, resend ACK for the last in-order packet
            sendACK((expectedSeqNum - 1 + LimitSeqNo) % LimitSeqNo,currentSequenceNumber);
            ackSent++;
            if (traceLevel > 1) {
                System.out.println("B_input: Received duplicate or out-of-window packet " + seqNum + ". Resent ACK.");
            }
        }
    }

    // Check if sequence number is within receiver's window
    private boolean isInReceiverWindow(int seqNum) {
        // For simplicity, assume receiver window size equals sender window size
        // and accept any sequence number that hasn't been delivered yet
        int lowerBound = expectedSeqNum;
        int upperBound = (expectedSeqNum + WindowSize) % LimitSeqNo;

        if (lowerBound < upperBound) {
            return seqNum >= lowerBound && seqNum < upperBound;
        } else { // Wrap-around
            return seqNum >= lowerBound || seqNum < upperBound;
        }
    }
    private boolean isInSenderWindow(int seqNum) {
        // For simplicity, assume receiver window size equals sender window size
        // and accept any sequence number that hasn't been delivered yet
        int lowerBound = base;
        int upperBound = (base + WindowSize) % LimitSeqNo;

        if (lowerBound < upperBound) {
            return seqNum >= lowerBound && seqNum < upperBound;
        } else { // Wrap-around
            return seqNum >= lowerBound || seqNum < upperBound;
        }
    }

    // Receiver: Send ACK packet to sender
    private void sendACK(int ackNum,int seqnumber) {
        int checksum = calculateChecksum("");
        Packet ackPkt = new Packet(seqnumber, ackNum, checksum, "");
        toLayer3(B, ackPkt);
        if (traceLevel > 1) {
            System.out.println("B_input: Sent ACK " + ackNum);
        }
    }

    // Helper method to check if a packet is corrupted
    private boolean isCorrupted(Packet packet) {
        if (packet.getSeqnum()>=LimitSeqNo){
            return true;
        }
        if(packet.getAcknum()>=LimitSeqNo){
            return true;
        }
        int calculatedChecksum = calculateChecksum(packet.getPayload());
        return calculatedChecksum != packet.getChecksum();
    }

    // Sender: Simulation done, print statistics
    protected void Simulation_done() {
        System.out.println("\n\n===============STATISTICS=======================");
        System.out.println("Number of original packets transmitted by A: " + originalPackets);
        System.out.println("Number of retransmissions by A: " + retransmissions);
        System.out.println("Number of data packets delivered to layer5 at B: " + delivered);
        System.out.println("Number of ACK packets sent by B: " + ackSent);
        System.out.println("Number of corrupted packets: " + corruptedPackets);
        System.out.println("Ratio of lost packets: " + ((double) (retransmissions-corruptedPackets)/(originalPackets+retransmissions+ackSent)));
        System.out.println("Ratio of corrupted packets: " + ((double) corruptedPackets / (originalPackets+retransmissions+ackSent-retransmissions+corruptedPackets)));
        System.out.println("Average RTT: " + (rttCount > 0 ? (totalRTTTime / rttCount) : 0));
        System.out.println("Average communication time: " + totalCommunicationTime/totalCommunicationCount);
        System.out.println("==================================================");

        // PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
        System.out.println("\nEXTRA:");
        System.out.println("All RTT: " + (rttCount > 0 ? (totalRTTTime / rttCount) : 0)*rttCount);
        System.out.println("Count RTT: "+rttCount);
        System.out.println("Total time to communicate: " + totalCommunicationTime);
        System.out.println("Counter for time to communicate:: " + totalCommunicationCount);
    }
}

