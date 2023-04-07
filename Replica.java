import java.util.List;

public class Replica {
    private final int port;
    private final String id;
    private final List<String> others;

    public Replica(int port, String id, List<String> others) {
        this.port = port;
        this.id = id;
        this.others = others;
    }

    public static void main(String[] args) {
        return;
    }
}