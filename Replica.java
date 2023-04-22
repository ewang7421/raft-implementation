import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.sql.Time;
import java.util.List;
import java.util.ArrayList;
import org.json.*;
import java.util.Random;

public class Replica {
    private static final String BROADCAST = "FFFF";
    private static final int TIMEOUT = 300;
    private static final int HEARTBEAT_INTERVAL = 100;
    private int port;
    private String id;
    private List<String> others;
    private DatagramSocket socket;

    private int initialTimeout;

    private long nextTimeout;
    private long nextHeartBeat;
    private String leader = "FFFF";
    private String phase;
    private int term = 0;

    private int currentVotes = 0;



    public Replica(int port, String id, List<String> others) throws SocketException, IOException{
        Random timeoutGen = new Random();

        this.port = port;
        this.id = id;
        this.others = others;
        System.out.println(String.format("Replica %s connecting to port %d", this.id, this.port));
        this.socket = new DatagramSocket(0);
        this.initialTimeout = timeoutGen.nextInt(151) + 150;
        this.nextTimeout = System.currentTimeMillis() + initialTimeout;

        System.out.println("Replica " + id + " starting up");
        System.out.println("timeout: " + this.initialTimeout);

        this.phase = "follower";

        String hello = "{ \"src\": \"" + id + "\", \"dst\": \"" + BROADCAST + "\", \"leader\": \"" + BROADCAST + "\", \"type\": \"hello\" }";
        this.send(hello);
        System.out.println("Sent hello message: " + hello);
    }

    public void send(String message) throws IOException {
        byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
        InetAddress address = InetAddress.getByName("localhost");
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
        socket.send(packet);
    }

    public void respond(String message) throws IOException {
        JSONObject jsonMessage = new JSONObject(message);
        JSONObject responseJson = new JSONObject();

        String type = jsonMessage.getString("type");
        String msgSrc = jsonMessage.getString("src");

        switch (type) {
            case "put":
            case "get":
                if(this.phase.equals("leader")){

                    responseJson.put("type", "fail");
                } else {
                    responseJson.put("type", "redirect");
                }
                String msgID = jsonMessage.getString("MID");
                responseJson.put("MID", msgID);
                break;
            case "requestVotes":
                responseJson.put("type", "voteResponse");
                responseJson.put("votedFor", true);
                break;
            case "voteResponse":
                if(jsonMessage.getBoolean("votedFor")){
                    this.currentVotes++;
                    this.checkElection();
                }
                return;
            case "hello":
                this.others.add(msgSrc);
                return;
            case "announce":
                this.leader = msgSrc;
                this.phase = "follower";
                this.nextTimeout = System.currentTimeMillis() + TIMEOUT;
                return;
            case "heartbeat":
                this.nextTimeout = System.currentTimeMillis() + TIMEOUT;
                return;
            case "fail":
                return;
            default:
                break;
        }


        responseJson.put("src", this.id);
        responseJson.put("dst", msgSrc);
        responseJson.put("leader", this.leader);


        this.send(responseJson.toString());

        System.out.println("Responded with " + responseJson);
    }

    public void run() throws IOException {
        byte[] buffer = new byte[65535];
        while (true) {
            //System.out.println(this.nextTimeout - System.currentTimeMillis());
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
            System.out.println("Received " + msg);
            this.respond(msg);

            switch (this.phase) {
                case "follower":
                    this.tryElection();
                    break;
                case "leader":
                    this.leaderJobs();
                    break;
                default:
                    break;
            }
        }
    }

    private void leaderJobs() throws IOException {
        if(System.currentTimeMillis() > this.nextHeartBeat){
            JSONObject heartBeat = new JSONObject();
            heartBeat.put("src", this.id);
            heartBeat.put("dst", BROADCAST);
            heartBeat.put("leader", this.id);
            heartBeat.put("type", "heartbeat");
            this.send(heartBeat.toString());
            this.nextHeartBeat = System.currentTimeMillis() + HEARTBEAT_INTERVAL;
            //this.nextTimeout = System.currentTimeMillis() + TIMEOUT;
        }
    }

    private void tryElection() throws IOException {
        if(System.currentTimeMillis() > this.nextTimeout){
            this.term++;
            this.phase = "candidate";

            JSONObject electionAnnouncement = new JSONObject();
            electionAnnouncement.put("src", this.id);
            electionAnnouncement.put("dst", BROADCAST);
            electionAnnouncement.put("leader", this.leader);
            electionAnnouncement.put("type", "requestVotes");

            this.send(electionAnnouncement.toString());

        }
    }

    private void checkElection() throws IOException {
        if(this.currentVotes > this.others.size()/2 && this.phase.equals("candidate")){
            this.leader = this.id;
            this.phase.equals("leader");
            JSONObject leaderAnnounce = new JSONObject();
            leaderAnnounce.put("src", this.id);
            leaderAnnounce.put("dst", BROADCAST);
            leaderAnnounce.put("type", "announce");
            leaderAnnounce.put("leader", this.id);
            this.send(leaderAnnounce.toString());
            this.nextHeartBeat = System.currentTimeMillis() + HEARTBEAT_INTERVAL;
        }

    }

    public static void main(String[] args) throws SocketException, IOException {
        int port = Integer.parseInt(args[0]);
        String id = args[1];
        List<String> others = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            others.add(args[i]);
        }
        Replica replica = new Replica(port, id, others);
        replica.run();
    }
}
