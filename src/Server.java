import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;

/**
 *  Main starts ServerA, which then creates and starts a ServerB for each client that sends a correct
 *  packet.
 */
public class Server {

    private static final int DEFAULT_PORT = 12235;
    private static final int LAST_THREE_SID = 345; // make sure this is the same as in Client!
    private static final byte[] EXPECTED_DATA_A = createResponse(0, 1, getByteArrayFromString("hello world"));
    private static final int TIMEOUT = 3000; // socket timeout in ms
    private static final Random R = new Random();
    // track ports in use to avoid collisions
    private static final HashSet<Integer> portsInUse = new HashSet<Integer>();
    static {
        portsInUse.add(DEFAULT_PORT);
    }

    /**
     *  Starts a server on default port which handles each request with a new thread
     */
    public static void main(String[] args) {
        try {
            System.out.println("Starting Server A..");
            DatagramSocket socket = new DatagramSocket(DEFAULT_PORT);
            while (true) {
                // receive a packet and handle it with a new thread
                DatagramPacket packet = new DatagramPacket(new byte[EXPECTED_DATA_A.length], EXPECTED_DATA_A.length);
                socket.receive(packet);
                new Thread(new ServerA(socket, packet)).start();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class ServerA implements Runnable {

        private DatagramPacket packet;
        private DatagramSocket socket;

        private ServerA(DatagramSocket socket, DatagramPacket packet) {
            this.socket = socket;
            this.packet = packet;
        }

        @Override
        public void run() {
            try {
                System.out.println("Packet Received");
                byte[] receivedData = packet.getData();
                if (Arrays.equals(EXPECTED_DATA_A, receivedData)) {
                    // received correct packet, respond and start serverB
                    System.out.println("Correct packet received!");
                    // randomly generate reasonable numbers
                    int num = R.nextInt(10) + 10;
                    int len = R.nextInt(50) + 10;
                    int udp_port = getNewPort();
                    int secretA = R.nextInt(500);
                    // start serverB before responding, so it's ready
                    ServerB serverB = new ServerB(num, len, udp_port, secretA);
                    new Thread(serverB).start();
                    // now create response and send it
                    byte[] payload = createPayload(num, len, udp_port, secretA);
                    byte[] response = createResponse(0, 1, payload);
                    InetAddress address = packet.getAddress();
                    int port = packet.getPort();
                    DatagramPacket responsePacket = new DatagramPacket(response, response.length, address, port);
                    socket.send(responsePacket);
                } else {
                    // nothing to do if incorrect packet received, just die
                    // these lines are for debugging
                    System.out.println("Received packet:");
                    printBits(receivedData);
                    System.out.println("Expected packet:");
                    printBits(EXPECTED_DATA_A);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class ServerB implements Runnable {

        private int num;
        private int len;
        private int port;
        private int secretA;

        private ServerB(int num, int len, int port, int secretA) {
            this.num = num;
            this.len = len;
            this.port = port;
            this.secretA = secretA; 
        }

        @Override
        public void run() {
            try {
                System.out.println("Starting Server B..");
                DatagramSocket socket = new DatagramSocket(port);
                socket.setSoTimeout(TIMEOUT);
                int packetId = 0;
                while (packetId < num) {
                    ByteBuffer buffer = ByteBuffer.allocate(len + 4);
                    buffer.putInt(packetId);
                    byte[] expectedData = createResponse(secretA, 1, buffer.array());
                    // receive a packet and handle it with a new thread
                    DatagramPacket packet = new DatagramPacket(new byte[expectedData.length], expectedData.length);
                    socket.receive(packet);
                    byte[] receivedData = packet.getData();
                    if (Arrays.equals(expectedData, receivedData)) {
                        System.out.println("Correct Packet Received");
                        // TODO: randomly decide to ack, increase packetId if we ack
                    }
                }
            }
            catch (SocketTimeoutException se) {
                // socket timed out, die quietly
                System.out.println("Server B timed out");
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            finally {
                portsInUse.remove(port);
            }
        }

    }

    // Returns a port that is not in use
    private static int getNewPort() {
        int port;
        do {
            port = R.nextInt(10000) + 10000;
        } while (portsInUse.contains(port));
        return port;
    }

    /**
     *  Creates a 16 byte payload from the given ints
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

    /*
    * Creates a response from the given values
    */
    private static byte[] createResponse(int secretInt, int stepInt, byte[] payload) {
        byte[] digits = get2ByteArray(LAST_THREE_SID);
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
        for (byte b : digits) {
            buf[i] = b;
            i++;
        }
        for (byte b : payload) {
            buf[i] = b;
            i++;
        }
        return buf;
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

}