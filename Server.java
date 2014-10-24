import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;

/**
 * CSE 461
 * John Wilson, Samuel Felker, Jake Nash
 * Project 2
 */
public class Server {

    public static final int PACKET_LENGTH_A = 24; // length in bytes of first packet
    public static final byte[] HELLO_WORLD = getByteArrayFromString("hello world");
    public static final String USAGE = "Usage:\n\t java Server <port>";
    public static final int TIMEOUT = 3000; // socket timeout in ms
    private static int defaultPort = 12235;
    // track ports in use to avoid collisions
    private static final Set<Integer> portsInUse = new HashSet<Integer>();
    static {
        portsInUse.add(defaultPort);
    }

    /**
     *  Starts a server on default port which handles each request with a new thread
     */
    public static void main(String[] args) {
        if (args.length > 0) {
            try {
                defaultPort = Integer.parseInt(args[0]);
            }
            catch (NumberFormatException e) {
                System.out.println(USAGE);
                System.exit(0);
            }
        }
        try {
            System.out.println("Starting Server on port " + defaultPort);
            DatagramSocket socket = new DatagramSocket(defaultPort);
            while (true) {
                // receive a packet and handle it with a new thread
                DatagramPacket packet = new DatagramPacket(new byte[PACKET_LENGTH_A], PACKET_LENGTH_A);
                socket.receive(packet);
                new Thread(new Handler(socket, packet)).start();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     *  Handler for each packet received by Server
     */
    private static class Handler implements Runnable {

        private DatagramPacket packet;
        private DatagramSocket socketA;
        private DatagramSocket socketB;
        private ServerSocket tcpSocket;
        private Socket tcpClient;
        private int udp_port;
        private int tcp_port;
        private int num;
        private int num2;
        private int len;
        private int len2;
        private int secretA;
        private int secretB;
        private int secretC;
        private int secretD;
        private byte c;
        private byte[] studentId;

        private Handler(DatagramSocket socket, DatagramPacket packet) {
            this.socketA = socket;
            this.packet = packet;
            Random r = new Random();
            num = r.nextInt(10) + 10;
            num2 = r.nextInt(10) + 10;
            len = r.nextInt(50) + 10;
            len2 = r.nextInt(50) + 10;
            udp_port = getNewPort();
            tcp_port = getNewPort();
            secretA = r.nextInt(500);
            secretB = r.nextInt(500);
            secretC = r.nextInt(500);
            secretD = r.nextInt(500);
            c = getRandomByte();
            portsInUse.add(udp_port);
            portsInUse.add(tcp_port);
            studentId = new byte[2]; // to be filled in from data in first packet
        }

        @Override
        public void run() {
            try {
                init();
                doStageA();
                doStageB();
                doStageC();
                doStageD();
            }
            catch (InvalidDataException ie) {
                System.out.println("*** Stage A Failure ***");
            }
            catch (SocketTimeoutException se) {
                System.out.println("*** Server timed out ***");
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            finally {
                portsInUse.remove(udp_port);
                portsInUse.remove(tcp_port);
            }
        }

        /**
         *  Initializes sockets and studentId
         */
        private void init() throws SocketException, IOException {
            socketB = new DatagramSocket(udp_port);
            socketB.setSoTimeout(TIMEOUT);
            tcpSocket = new ServerSocket(tcp_port);
            tcpSocket.setSoTimeout(TIMEOUT);
            byte[] receivedData = packet.getData();
            // we know these are safe as we control the length of both arrays
            studentId[0] = receivedData[10];
            studentId[1] = receivedData[11];
        }

        /**
         *  Performs Stage A
         */
        private void doStageA() throws IOException, InvalidDataException {
            System.out.println("*** Commencing Stage A ***");
            byte[] expectedDataA = createResponse(0, 1, HELLO_WORLD);
            byte[] receivedData = packet.getData();
            if (Arrays.equals(expectedDataA, receivedData)) {
                byte[] payloadA = createPayload(num, len, udp_port, secretA);
                byte[] responseDataA = createResponse(0, 2, payloadA);
                DatagramPacket responsePacketA = new DatagramPacket(responseDataA, responseDataA.length, packet.getAddress(), packet.getPort());
                socketA.send(responsePacketA);
                System.out.println("*** Stage A Success ***");
            } else {
                throw new InvalidDataException();
            }
        }

        /**
         *  Performs Stage B, requires Stage A completed successfully
         */
        private void doStageB() throws SocketTimeoutException, IOException {
            System.out.println("*** Commencing Stage B ***");
            int packetId = 0;
            Random r = new Random();
            while (packetId < num) {
                ByteBuffer buffer = ByteBuffer.allocate(len + 4);
                buffer.putInt(packetId);
                byte[] expectedDataB = createResponse(secretA, 1, buffer.array());
                DatagramPacket packetB = new DatagramPacket(new byte[expectedDataB.length], expectedDataB.length);
                socketB.receive(packetB);
                byte[] receivedDataB = packetB.getData();
                if (Arrays.equals(expectedDataB, receivedDataB)) {
                    System.out.print("Received packet " + ( packetId + 1 ) + " of " + num + ", ");
                    if (r.nextInt(3) != 0) {
                        System.out.println("decided to ack");
                        byte[] payloadB1 = createPayload(packetId);
                        byte[] responseDataB1 = createResponse(secretA, 1, payloadB1);
                        DatagramPacket responsePacketB1 = new DatagramPacket(responseDataB1, responseDataB1.length, packet.getAddress(), packet.getPort());
                        socketB.send(responsePacketB1);
                        packetId++;
                    } else {
                        System.out.println("decided not to ack");
                    }
                }
            }
            byte[] payloadB2 = createPayload(tcp_port, secretB);
            byte[] responseDataB2 = createResponse(secretA, 2, payloadB2);
            DatagramPacket responsePacketB2 = new DatagramPacket(responseDataB2, responseDataB2.length, packet.getAddress(), packet.getPort());
            socketB.send(responsePacketB2);
        }

        /**
         *  Performs Stage C, requires Stage A-B completed successfully
         */
        private void doStageC() throws IOException {
            System.out.println("*** Commencing Stage C ***");
            tcpClient = tcpSocket.accept();
            byte[] payloadC = createPayload(num2, len2, secretC, c);
            byte[] responseDataC = createResponse(secretB, 2, payloadC);
            DataOutputStream outStream = new DataOutputStream(tcpClient.getOutputStream());
            outStream.write(responseDataC, 0, responseDataC.length);
            System.out.println("*** Stage C Success ***");
        }

        /**
         *  Performs Stage D, requires Stage A-C completed successfully
         */
        private void doStageD() throws IOException, SocketTimeoutException {
            System.out.println("*** Commencing Stage D ****");
            int count = 0;
            DataInputStream inStream = new DataInputStream(tcpClient.getInputStream());
            DataOutputStream outStream = new DataOutputStream(tcpClient.getOutputStream());
            byte[] payload = createPayload(len2, c);
            byte[] expectedData = createResponse(secretC, 1, payload);
            while (count < num2) {
                byte[] receivedData = new byte[expectedData.length]; 
                inStream.read(receivedData);
                if (Arrays.equals(expectedData, receivedData)) {
                    count++;
                    System.out.println("Received packet " + ( count + 1 ) + " of " + num2);
                }
            }
            byte[] payloadD = createPayload(secretD);
            byte[] responseDataD = createResponse(secretC, 2, payloadD);
            outStream.write(responseDataD, 0, responseDataD.length);
            System.out.println("*** Stage D Success ***");
        }

        /*
         * Creates a response from the given values
         */
        private byte[] createResponse(int secretInt, int stepInt, byte[] payload) {
            byte[] secret = get4ByteArray(secretInt);
            byte[] step = get2ByteArray(stepInt);
            byte[] payload_len = get4ByteArray(payload.length);
            int size = 12 + payload.length;
            // align to 4 bytes
            while (size % 4 != 0)
                size++;
            // now copy everything to one byte[]
            byte[] buf = new byte[size];
            int i = 0;
            for (byte b : payload_len) {
                buf[i] = b;
                i++;
            }
            for (byte b : secret) {
                buf[i] = b;
                i++;
            }
            for (byte b : step) {
                buf[i] = b;
                i++;
            }
            for (byte b : studentId) {
                buf[i] = b;
                i++;
            }
            for (byte b : payload) {
                buf[i] = b;
                i++;
            }
            return buf;
        }
    }

    /**
     *  returns a random non-negative byte
     */
    private static byte getRandomByte() {
        Random r = new Random();
        byte[] buf = new byte[1];
        byte b;
        do {
            r.nextBytes(buf);
            b = buf[0];
        } while (b < 0);
        return b;
    }

    // Returns a port that is not in use
    private static int getNewPort() {
        Random r = new Random();
        int port;
        do {
            port = r.nextInt(10000) + 10000;
        } while (portsInUse.contains(port));
        return port;
    }

    /**
     *  Creates a byte[] of length len, with byte equal to c
     */
    private static byte[] createPayload(int len, byte c) {
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++) {
            result[i] = c;
        }
        return result;
    }

    /**
     *  Creates a byte[] from the given ints
     */
    private static byte[] createPayload(int num, int len, int port, int secret) {
        byte[] result = new byte[16];
        ByteBuffer b = ByteBuffer.wrap(result);
        b.putInt(num);
        b.putInt(len);
        b.putInt(port);
        b.putInt(secret);
        return result;
    }

    /**
     *  Creates a byte[] from the given ints
     */
    private static byte[] createPayload(int port, int secret) {
        byte[] result = new byte[8];
        ByteBuffer b = ByteBuffer.wrap(result);
        b.putInt(port);
        b.putInt(secret);
        return result;
    }

    /**
     *  Creates a byte[] from the given ints and byte
     */
    private static byte[] createPayload(int num, int len, int secret, byte c) {
        byte[] result = new byte[13];
        ByteBuffer b = ByteBuffer.wrap(result);
        b.putInt(num);
        b.putInt(len);
        b.putInt(secret);
        b.put(c);
        return result;
    }

    /**
     *  Creates a byte[] from the given int
     */
    private static byte[] createPayload(int packetId) {
        byte[] result = new byte[4];
        ByteBuffer b = ByteBuffer.wrap(result);
        b.putInt(packetId);
        return result;
    }

    /*
     * Returns a 2 byte array representation of an int value
     * Network (Big Endian) ordering
     */
    public static byte[] get2ByteArray(int value) {
        ByteBuffer b = ByteBuffer.allocate(4);
        b.order(ByteOrder.BIG_ENDIAN);
        b.putInt(value);
        byte[] buff = b.array();
        byte[] result = new byte[2];
        result[0] = buff[2];
        result[1] = buff[3];
        return result;
    }
    
    /*
     * Returns a 4 byte array representation of an int value
     * Network (Big Endian) ordering
     */
    public static byte[] get4ByteArray(int value) {
        ByteBuffer b = ByteBuffer.allocate(4);
        b.order(ByteOrder.BIG_ENDIAN);
        b.putInt(value);
        byte[] buff = b.array();
        return buff;
    }

    /*
     * Returns a byte array representation of a String
     * Network (Big Endian) ordering
     */
    public static byte[] getByteArrayFromString(String s) {
        int size = s.length() + 1;
        ByteBuffer b = ByteBuffer.allocate(size * 2);
        for (char c : s.toCharArray())
            b.putChar(c);
        byte[] data = b.array();
        byte[] result = new byte[size];
        for (int i = 0; i < size; i++) {
            result[i] = data[i * 2 + 1];
        }
        return result;
    }
    
    /**
     * For debugging, prints bits in 4 byte rows to std out
     */
    public static void printBits(byte[] buf) {
        int i = 0;
        for (byte b : buf) {
            for (int j = 7; j >= 0; j--) {
                System.out.print( ( b >> j ) & 1);
            }
            i++;
            if (i % 4 == 0)
                System.out.println();
            else
                System.out.print(" ");
        }
    }

    private static class InvalidDataException extends Exception {

    }

}
