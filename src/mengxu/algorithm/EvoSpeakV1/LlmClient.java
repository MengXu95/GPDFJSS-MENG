package mengxu.algorithm.EvoSpeakV1;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Function;

public final class LlmClient {
    private final EvoSpeakConfig config;
    private final String provider;
    private final String model;
    private final URI endpoint;
    private final String apiKey;
    private final HttpClient client;
    private int requests;

    public LlmClient(EvoSpeakConfig config) {
        this(config, System::getenv);
    }

    LlmClient(EvoSpeakConfig config, Function<String, String> environment) {
        this.config = config;
        provider = config.text("llm.provider", "openai-compatible").toLowerCase(Locale.ROOT);
        model = config.text("llm.model", "");
        if (model.isEmpty()) {
            throw new IllegalArgumentException("Set llm.model to the model or Azure deployment name.");
        }
        String defaultEndpoint;
        switch (provider) {
            case "openai-compatible": defaultEndpoint = "https://api.openai.com/v1/chat/completions"; break;
            case "anthropic": defaultEndpoint = "https://api.anthropic.com/v1/messages"; break;
            case "ollama": defaultEndpoint = "http://localhost:11434/api/chat"; break;
            case "azure-openai": defaultEndpoint = ""; break;
            default: throw new IllegalArgumentException("Unknown llm.provider: " + provider);
        }
        String address = config.text("llm.endpoint", defaultEndpoint);
        if (address.isEmpty()) {
            throw new IllegalArgumentException("Set llm.endpoint to the full Azure chat/completions deployment URL including api-version.");
        }
        endpoint = URI.create(address);
        if (endpoint.getQuery() != null && endpoint.getQuery().matches("(?i).*(api[-_]?key|token|secret|sig)=.*")) {
            throw new IllegalArgumentException("Do not put credentials in the endpoint query. Use llm.api-key-env.");
        }
        String host = endpoint.getHost();
        boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
        if (host == null || endpoint.getUserInfo() != null || endpoint.getFragment() != null
                || !("https".equalsIgnoreCase(endpoint.getScheme()) || (loopback && "http".equalsIgnoreCase(endpoint.getScheme())))) {
            throw new IllegalArgumentException("LLM endpoints require HTTPS, except HTTP on localhost. Embedded credentials are not allowed.");
        }
        String keyName = config.text("llm.api-key-env", provider.equals("anthropic") ? "ANTHROPIC_API_KEY" : "OPENAI_API_KEY");
        apiKey = keyName.isEmpty() ? null : environment.apply(keyName);
        if (!provider.equals("ollama") && (apiKey == null || apiKey.isBlank())) {
            throw new IllegalArgumentException("Missing API key environment variable " + keyName
                    + ". Set it outside source control and restart the IDE, or configure ollama.");
        }
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(config.integer("llm.timeout-seconds", 120)))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public int requestCount() {
        return requests;
    }

    public String complete(String system, String prompt) throws IOException, InterruptedException {
        if (system.length() + prompt.length() > 200000) {
            throw new IOException("LLM request exceeds the 200000-character budget.");
        }
        JSONObject body = requestBody(system, prompt);
        IOException lastFailure = null;
        int attempts = Math.min(10, config.integer("llm.max-attempts", 3));
        for (int attempt = 0; attempt < attempts; attempt++) {
            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(config.integer("llm.timeout-seconds", 120)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
            if (apiKey != null && !apiKey.isBlank()) {
                if (provider.equals("azure-openai")) {
                    request.header("api-key", apiKey);
                } else if (provider.equals("anthropic")) {
                    request.header("x-api-key", apiKey);
                } else {
                    request.header("Authorization", "Bearer " + apiKey);
                }
            }
            if (provider.equals("anthropic")) {
                request.header("anthropic-version", "2023-06-01");
            }
            HttpResponse<String> response;
            try {
                requests++;
                response = client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException error) {
                lastFailure = new IOException("LLM transport failed for " + provider + "; check endpoint and network.", error);
                continue;
            }
            int status = response.statusCode();
            if (status == 429 || status >= 500) {
                lastFailure = new IOException("LLM " + provider + " returned HTTP " + status + "; retry budget exhausted.");
                continue;
            }
            if (status < 200 || status >= 300) {
                throw new IOException("LLM " + provider + " returned HTTP " + status + ". Check credentials, endpoint, model and token settings.");
            }
            if (response.body().length() > 2_000_000) {
                throw new IOException("LLM response exceeds the 2 MB safety limit.");
            }
            try {
                String text = responseText(new JSONObject(response.body()));
                if (text.isBlank()) {
                    throw new IOException("LLM returned empty text or a refusal.");
                }
                return text;
            } catch (org.json.JSONException error) {
                throw new IOException("LLM returned an incompatible JSON response envelope.", error);
            }
        }
        throw lastFailure == null ? new IOException("LLM did not return a response.") : lastFailure;
    }

    private JSONObject requestBody(String system, String prompt) {
        JSONArray messages = new JSONArray();
        JSONObject body = new JSONObject().put("model", model);
        int tokens = config.integer("llm.max-output-tokens", 8000);
        if (provider.equals("anthropic")) {
            body.put("system", system).put("max_tokens", tokens);
        } else {
            messages.put(new JSONObject().put("role", "system").put("content", system));
            if (provider.equals("ollama")) {
                body.put("stream", false).put("options", new JSONObject().put("num_predict", tokens));
            } else {
                String tokenParameter = config.text("llm.token-parameter", "max_tokens");
                if (!tokenParameter.equals("max_tokens") && !tokenParameter.equals("max_completion_tokens")) {
                    throw new IllegalArgumentException("llm.token-parameter must be max_tokens or max_completion_tokens.");
                }
                body.put(tokenParameter, tokens);
            }
        }
        String temperature = config.text("llm.temperature", "");
        if (!temperature.isEmpty()) {
            double value = Double.parseDouble(temperature);
            if (!Double.isFinite(value) || value < 0.0 || value > 2.0) {
                throw new IllegalArgumentException("llm.temperature must be between 0 and 2.");
            }
            if (provider.equals("ollama")) {
                body.getJSONObject("options").put("temperature", value);
            } else {
                body.put("temperature", value);
            }
        }
        messages.put(new JSONObject().put("role", "user").put("content", prompt));
        return body.put("messages", messages);
    }

    private String responseText(JSONObject response) throws IOException {
        if (provider.equals("ollama")) {
            rejectTruncation(response.optString("done_reason"));
            return response.getJSONObject("message").getString("content");
        }
        if (provider.equals("anthropic")) {
            rejectTruncation(response.optString("stop_reason"));
            StringBuilder result = new StringBuilder();
            for (Object block : response.getJSONArray("content")) {
                JSONObject content = (JSONObject) block;
                if (content.optString("type").equals("text")) {
                    result.append(content.getString("text"));
                }
            }
            return result.toString();
        }
        JSONObject choice = response.getJSONArray("choices").getJSONObject(0);
        rejectTruncation(choice.optString("finish_reason"));
        JSONObject message = choice.getJSONObject("message");
        return message.optString("content", "");
    }

    private void rejectTruncation(String reason) throws IOException {
        if (reason.equals("length") || reason.equals("max_tokens")) {
            throw new IOException("LLM output was truncated. Reduce evospeak.batch-size or increase llm.max-output-tokens.");
        }
    }
}