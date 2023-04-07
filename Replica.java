import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.List;
import java.util.ArrayList;

public class Replica {
    private static final String BROADCAST = "FFFF";
    private int port;
    private String id;
    private List<String> others;
    private DatagramSocket sockeft;

    public Replica(int port, String id, List<String> others) throws SocketException, IOException{
        this.port = port;
        this.id = id;
        this.others = others;
        this.socket = new DatagramSocket(port);

        System.out.println("Replica " + id + " starting up");

        String hello = "{ \"src\": \"" + id + "\", \"dst\": \"" + BROADCAST + "\", \"leader\": \"" + BROADCAST + "\", \"type\": \"hello\" }";
        this.send(hello);
        System.out.println("Sent hello message: " + hello);
    }

    public void send(String message) throws IOException {
        byte[] buffer = message.getBytes("utf-8");
        for (String other : this.others) {
            InetAddress address = InetAddress.getByName("localhost");
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
            socket.send(packet);
        }
    }

    public void run() throws IOException {
        byte[] buffer = new byte[65535];
        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), "utf-8");
            System.out.println("Received message: " + msg);
        }
    }

    public static void main(String[] args) throws SocketException, IOException {
        int port = Integer.parseInt(args[0]);
        String id = args[1];
        List<String> others = new ArrayList<String>();
        for (int i = 2; i < args.length; i++) {
            others.add(args[i]);
        }
        Replica replica = new Replica(port, id, others);
        replica.run();
    }
}
