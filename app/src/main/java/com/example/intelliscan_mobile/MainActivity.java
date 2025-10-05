package com.example.intelliscan_mobile;

import android.os.Bundle;

import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private Button btnPing, btnPortScan, btnSpeed;
    private TextView tvOutput;
    private ExecutorService exec;
    private Handler uiHandler;
    private OkHttpClient httpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnPing = findViewById(R.id.btnPing);
        btnPortScan = findViewById(R.id.btnPortScan);
        btnSpeed = findViewById(R.id.btnSpeed);
        tvOutput = findViewById(R.id.tvOutput);

        exec = Executors.newSingleThreadExecutor();
        uiHandler = new Handler(Looper.getMainLooper());
        httpClient = new OkHttpClient();

        btnPing.setOnClickListener(v -> runPingTest("8.8.8.8", 4));
        btnPortScan.setOnClickListener(v -> runPortScan("192.168.1.1", 20, 1024)); // uses a default gateway example
        btnSpeed.setOnClickListener(v -> runSpeedTest("https://speed.hetzner.de/10MB.bin")); // uses an example file

        GeminiClient geminiClient = new GeminiClient();
        geminiClient.callLLMSummarize("Summarize the following text: 'Artificial Intelligence is intelligence demonstrated by machines, in contrast to the natural intelligence displayed by humans and animals.'", new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> tvOutput.setText("[LLM] HTTP error: " + response.code()));
                    return;
                }

                String responseBody = response.body().string();

                try {
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    String summary = jsonResponse.getJSONArray("content").getString(0);

                    // Update UI safely
                    runOnUiThread(() -> tvOutput.setText(summary));

                } catch (JSONException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> tvOutput.setText("[LLM] JSON parse error"));
                }
            }
        });

    }

    // Append text safely to UI
    private void appendOutput(String s) {
        uiHandler.post(() -> tvOutput.append(s + "\n"));
    }

    // Ping test (InetAddress.isReachable)
    private void runPingTest(String host, int count) {
        appendOutput("Starting ping to " + host + "...");
        exec.submit(() -> {
            try {
                InetAddress addr = InetAddress.getByName(host);
                for (int i = 0; i < count; i++) {
                    long start = System.currentTimeMillis();
                    boolean reachable = addr.isReachable(2000); // 2s timeout
                    long rtt = System.currentTimeMillis() - start;
                    appendOutput(String.format("Ping %d: %s (rtt=%d ms)", i+1, reachable ? "reachable" : "unreachable", rtt));
                    Thread.sleep(300);
                }
                // Call summarizer
                String raw = tvOutput.getText().toString();
                callLLMSummarize("Summarize this ping output for a networking student:\n\n" + raw);
            } catch (Exception e) {
                appendOutput("Ping error: " + e.getMessage());
            }
        });
    }

    // Simple port scan using sockets (RUN LOCAL-ONLY / limited range)
    private void runPortScan(String host, int startPort, int endPort) {
        appendOutput("Starting limited port scan on " + host + " from " + startPort + " to " + endPort);
        exec.submit(() -> {
            ArrayList<Integer> open = new ArrayList<>();
            for (int port = startPort; port <= endPort; port++) {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host, port), 150); // 150ms timeout
                    open.add(port);
                    appendOutput("Port " + port + " OPEN");
                } catch (IOException ignored) {
                    // closed / filtered - skip
                }
            }
            if (open.isEmpty()) appendOutput("No open ports found in range.");
            // Summarize discovered ports
            StringBuilder result = new StringBuilder("Port scan results:\n");
            for (int p : open) result.append("Open: ").append(p).append("\n");
            callLLMSummarize("Summarize these port-scan results for a student (explain typical services):\n\n" + result.toString());
        });
    }

    // Simple speed test (download a file and measure throughput)
    private void runSpeedTest(String fileUrl) {
        appendOutput("Starting speed test (download): " + fileUrl);
        exec.submit(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(fileUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                conn.connect();

                long contentLength = conn.getContentLengthLong();
                InputStream in = new BufferedInputStream(conn.getInputStream());
                byte[] buffer = new byte[8192];
                long read = 0;
                int n;
                long start = System.currentTimeMillis();
                while ((n = in.read(buffer)) != -1) {
                    read += n;
                    // small safeguard: stop early after ~5MB to save time
                    if (read > 5 * 1024 * 1024) break;
                }
                long elapsedMs = System.currentTimeMillis() - start;
                double mb = read / (1024.0 * 1024.0);
                double mbps = (mb * 8) / (elapsedMs / 1000.0); // Mbps
                appendOutput(String.format("Downloaded %.2f MB in %d ms => %.2f Mbps", mb, elapsedMs, mbps));
                callLLMSummarize("Summarize this speed test: downloaded " + String.format("%.2f MB in %d ms => %.2f Mbps", mb, elapsedMs, mbps));
            } catch (Exception e) {
                appendOutput("Speed test error: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    // Generic LLM summarizer helper (replace endpoint + API key as needed)
    // WARNING: Do not send sensitive network details to remote servers without consent.
    private void callLLMSummarize(String prompt) {
        exec.submit(() -> {
            appendOutput("[LLM] Sending summary request...");
            try {
                // Replace these with your provider endpoint & key
                String endpoint = "https://YOUR-LLM-ENDPOINT.example.com/v1/generate";
                String apiKey = "YOUR_API_KEY";

                // Simple JSON body - adapt to your LLM's expected format
                String jsonBody = "{ \"prompt\": " + jsonEscape(prompt) + ", \"max_tokens\": 200 }";

                RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));
                Request req = new Request.Builder()
                        .url(endpoint)
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .post(body)
                        .build();

                try (Response resp = httpClient.newCall(req).execute()) {
                    if (!resp.isSuccessful()) {
                        appendOutput("[LLM] Error: " + resp.code() + " " + resp.message());
                        return;
                    }
                    String responseText = resp.body().string();
                    // Ideally parse the provider's JSON and extract the generated text; here we'll just show raw text
                    appendOutput("[LLM Summary]");
                    appendOutput(responseText);
                }
            } catch (Exception e) {
                appendOutput("[LLM] Exception: " + e.getMessage());
            }
        });
    }

    // small helper to JSON escape a string
    private String jsonEscape(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        exec.shutdownNow();
    }
}

/*
 * Graphics / icons used in this project:
 * - [Name of Asset] by Freepik (https://www.freepik.com)
 * - Used under Free License: https://www.freepik.com/free-license
 *
 * This attribution satisfies the Freepik Free License requirements.
 */