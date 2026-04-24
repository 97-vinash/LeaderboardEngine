import org.json.JSONObject;
import org.json.JSONArray;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class RA231103QuizHandler {
    private static final String BASE_URL = "https://devapigw.vidalhealthtpa.com/srm-quiz-task";
    private static final String GET_PATH = "/quiz/messages";
    private static final String POST_PATH = "/quiz/submit";
    private static final int TOTAL = 10;
    private static final long WAIT = 5;

    private final String regNo = "RA2311030010110";
    private final HttpClient client = HttpClient.newHttpClient();
    private final Set<String> processedPairs = new HashSet<>();
    private final Map<String, Integer> scores = new HashMap<>();

    private record LeaderboardItem(String name, int total) implements Comparable<LeaderboardItem> {
        public int compareTo(LeaderboardItem other) {
            int cmp = Integer.compare(other.total, this.total);
            return cmp != 0 ? cmp : this.name.compareTo(other.name);
        }
    }

    public void execute() throws Exception {
        List<String> rawData = new ArrayList<>();
        for (int i = 0; i < TOTAL; i++) {
            rawData.get(fetchRaw(i));
            if (i < TOTAL - 1) TimeUnit.SECONDS.sleep(WAIT);
        }

        for (String json : rawData) {
            if (json == null) continue;
            JSONObject obj = new JSONObject(json);
            JSONArray events = obj.optJSONArray("events");
            if (events == null) continue;
            for (int j = 0; j < events.length(); j++) {
                JSONObject ev = events.getJSONObject(j);
                String round = ev.getString("roundId");
                String participant = ev.getString("participant");
                int score = ev.getInt("score");
                String key = round + "@" + participant;
                if (!processedPairs.contains(key)) {
                    processedPairs.add(key);
                    scores.merge(participant, score, Integer::sum);
                }
            }
        }

        List<LeaderboardItem> board = new ArrayList<>();
        for (var e : scores.entrySet()) {
            board.add(new LeaderboardItem(e.getKey(), e.getValue()));
        }
        Collections.sort(board);

        int grand = board.stream().mapToInt(i -> i.total).sum();
        System.out.println("\n=== RANKINGS ===");
        board.forEach(i -> System.out.println(i.name + " -> " + i.total));
        System.out.println("\nTOTAL: " + grand);

        sendToServer(board);
    }

    private String fetchRaw(int pollIdx) throws IOException, InterruptedException {
        String url = BASE_URL + GET_PATH + "?regNo=" + regNo + "&poll=" + pollIdx;
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            System.err.println("Poll " + pollIdx + " error: " + resp.statusCode());
            return null;
        }
        return resp.body();
    }

    private void sendToServer(List<LeaderboardItem> board) throws IOException, InterruptedException {
        JSONObject payload = new JSONObject();
        payload.put("regNo", regNo);
        JSONArray arr = new JSONArray();
        for (LeaderboardItem item : board) {
            JSONObject entry = new JSONObject();
            entry.put("participant", item.name);
            entry.put("totalScore", item.total);
            arr.put(entry);
        }
        payload.put("leaderboard", arr);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + POST_PATH))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200) {
            JSONObject res = new JSONObject(resp.body());
            System.out.println("Server: " + res.getString("message"));
            System.out.println("Submitted=" + res.getInt("submittedTotal") + " Expected=" + res.getInt("expectedTotal"));
            System.out.println("Correct? " + res.getBoolean("isCorrect"));
        } else {
            System.err.println("Submit HTTP " + resp.statusCode());
        }
    }

    public static void main(String[] args) {
        try {
            new RA231103QuizHandler().execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
