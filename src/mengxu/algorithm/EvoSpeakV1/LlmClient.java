package mengxu.algorithm.EvoSpeakV1;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
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
    private final String credentialSource;
    private final HttpClient client;
    private final String proxyDescription;
    private int requests;

    public LlmClient(EvoSpeakConfig config) {
        this(config, System::getenv);
    }

    LlmClient(EvoSpeakConfig config, Function<String, String> environment) {
        this(config, environment, true);
    }

    private LlmClient(EvoSpeakConfig config, Function<String, String> environment, boolean authenticate) {
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
        String configuredKey = null;
        String source = "none (unauthenticated connection check)";
        if (authenticate) {
            configuredKey = config.configuredApiKey();
            source = "llm.api-key in the selected params";
            if (configuredKey.isEmpty() || configuredKey.equals("REPLACE_WITH_YOUR_API_KEY")) {
                configuredKey = keyName.isEmpty() ? null : environment.apply(keyName);
                source = "the environment variable selected by llm.api-key-env";
            }
        }
        apiKey = configuredKey == null ? null : configuredKey.trim();
        credentialSource = source;
        if (authenticate && !provider.equals("ollama") && (apiKey == null || apiKey.isBlank())) {
            throw new IllegalArgumentException("Missing API key. Set llm.api-key in a Git-ignored .local.params file, "
                    + "or set environment variable " + keyName + " and restart the IDE, or configure ollama.");
        }
        if (apiKey != null && (apiKey.indexOf('"') >= 0 || apiKey.indexOf('\'') >= 0
            || apiKey.regionMatches(true, 0, "Bearer ", 0, 7))) {
            throw new IllegalArgumentException("Enter only the API token in llm.api-key or its environment variable, "
                + "without quotes or a Bearer prefix. The client supplies the authentication header.");
        }
        if (apiKey != null && apiKey.chars().anyMatch(character -> character < 33 || character > 126)) {
            throw new IllegalArgumentException("Invalid API key format. Use a printable ASCII token without spaces, control characters or line breaks.");
        }
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.integer("llm.connect-timeout-seconds", 20)))
                .followRedirects(HttpClient.Redirect.NEVER);
        proxyDescription = configureProxy(builder);
        client = builder.build();
    }

    public static String checkConnection(EvoSpeakConfig config) throws IOException, InterruptedException {
        LlmClient probe = new LlmClient(config, ignored -> null, false);
        HttpRequest request = HttpRequest.newBuilder(probe.endpoint)
                .timeout(Duration.ofSeconds(config.integer("llm.timeout-seconds", 120)))
                .method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        long started = System.nanoTime();
        try {
            HttpResponse<Void> response = probe.client.send(request, HttpResponse.BodyHandlers.discarding());
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
            return "LLM connection check: received HTTP " + response.statusCode() + " from " + probe.endpoint.getHost()
                    + " via " + probe.proxyDescription + " in " + elapsedMillis + " ms. "
                    + "No API key or prompt was sent; no GP run was started. An HTTP response confirms transport "
                    + "only, not API-key validity or model availability."
                    + (response.statusCode() == 407 ? " The proxy requires authentication." : "");
        } catch (IOException error) {
            throw probe.transportFailure(error);
        }
    }

    public static String checkAuthentication(EvoSpeakConfig config) throws IOException, InterruptedException {
        LlmClient probe = new LlmClient(config);
        if (!probe.provider.equals("openai-compatible") || probe.endpoint.getPath() == null
                || !probe.endpoint.getPath().endsWith("/chat/completions") || probe.endpoint.getRawQuery() != null) {
            throw new IllegalArgumentException("--check-auth currently supports OpenAI-compatible /chat/completions endpoints "
                    + "without query parameters. It checks the same service's /models endpoint, not generation.");
        }
        URI modelsEndpoint = probe.endpoint.resolve("../models");
        HttpRequest request = probe.authenticatedRequest(modelsEndpoint).GET().build();
        HttpResponse<String> response;
        try {
            response = probe.client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw probe.transportFailure(error);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            if (response.statusCode() == 403 || response.statusCode() == 404 || response.statusCode() == 405) {
                throw new IOException("Authentication check received HTTP " + response.statusCode() + " from "
                        + modelsEndpoint.getHost() + ". The model-list endpoint may be restricted or unsupported. "
                        + "This result alone does not establish whether the token can perform inference. No generation was requested.");
            }
            throw probe.httpFailure(response.statusCode(), response.body());
        }
        if (response.body().length() > 2_000_000) {
            throw new IOException("Authentication-check response exceeds the 2 MB safety limit.");
        }
        try {
            if (new JSONObject(response.body()).optJSONArray("data") == null) {
                throw new IOException("The model-list response has an unexpected format; authentication could not be confirmed.");
            }
        } catch (org.json.JSONException error) {
            throw new IOException("The model-list response is not JSON; authentication could not be confirmed.");
        }
        return "LLM authentication check: the model-list request was accepted by " + modelsEndpoint.getHost()
                + " using " + probe.credentialSource + ". No prompt, generation or GP run was submitted. "
                + "This does not verify inference permissions, model access or billing quota.";
    }

    private HttpRequest.Builder authenticatedRequest(URI target) {
        HttpRequest.Builder request = HttpRequest.newBuilder(target)
                .timeout(Duration.ofSeconds(config.integer("llm.timeout-seconds", 120)));
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
        return request;
    }

    private String configureProxy(HttpClient.Builder builder) {
        String setting = config.text("llm.proxy", "system");
        if (setting.equalsIgnoreCase("system")) {
            ProxySelector selector = ProxySelector.getDefault();
            builder.proxy(selector == null ? ProxySelector.of(null) : selector);
            return "JVM default proxy selector (IDE/browser proxy settings may differ)";
        }
        if (setting.equalsIgnoreCase("direct")) {
            builder.proxy(ProxySelector.of(null));
            return "direct connection";
        }
        String guidance = "llm.proxy must be system, direct, or http://host:port for an HTTP CONNECT-capable proxy. "
                + "SOCKS, credentials, query strings and non-root paths are not supported.";
        URI proxy;
        try {
            proxy = URI.create(setting);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(guidance);
        }
        if (!"http".equalsIgnoreCase(proxy.getScheme()) || proxy.getHost() == null
                || proxy.getPort() < 1 || proxy.getPort() > 65535 || proxy.getUserInfo() != null
                || proxy.getQuery() != null || proxy.getFragment() != null
                || (proxy.getPath() != null && !proxy.getPath().isEmpty() && !proxy.getPath().equals("/"))) {
            throw new IllegalArgumentException(guidance);
        }
        builder.proxy(ProxySelector.of(new InetSocketAddress(proxy.getHost(), proxy.getPort())));
        return "HTTP proxy " + proxy.getHost() + ":" + proxy.getPort();
    }

    IOException transportFailure(IOException error) {
        String reason;
        if (hasCause(error, UnknownHostException.class) || hasCause(error, UnresolvedAddressException.class)) {
            reason = "DNS resolution failed. Check the endpoint/proxy hostname and your DNS settings.";
        } else if (hasCause(error, HttpConnectTimeoutException.class) || hasCause(error, ConnectException.class)) {
            reason = "TCP connection failed or timed out. Check network/firewall access and whether the proxy is running. "
                    + "If a proxy is required, set llm.proxy=http://127.0.0.1:<your-HTTP-proxy-port> in local.params; "
                    + "Java does not automatically use HTTPS_PROXY or an IDE's own HTTP proxy settings.";
        } else if (hasCause(error, javax.net.ssl.SSLException.class)) {
            reason = "TLS negotiation failed. Check the endpoint and the JVM trust store; do not disable certificate verification.";
        } else if (hasCause(error, HttpTimeoutException.class)) {
            reason = "The request timed out. Check proxy/upstream latency, reduce the generation batch, "
                    + "or adjust llm.timeout-seconds for model response time.";
        } else {
            reason = "The transport was interrupted. Check the endpoint, network and proxy configuration.";
        }
        int port = endpoint.getPort() > 0 ? endpoint.getPort() : endpoint.getScheme().equalsIgnoreCase("https") ? 443 : 80;
        return new IOException("LLM transport failed for " + provider + " at " + endpoint.getHost() + ":" + port
                + " via " + proxyDescription + ". " + reason + " Connect timeout="
                + config.integer("llm.connect-timeout-seconds", 20) + "s; request timeout="
                + config.integer("llm.timeout-seconds", 120) + "s. No usable HTTP response was received; "
                + "this is not an API-key authentication response.", error);
    }

    private boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
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
            HttpRequest.Builder request = authenticatedRequest(endpoint)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
            HttpResponse<String> response;
            try {
                requests++;
                response = client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException error) {
                lastFailure = transportFailure(error);
                continue;
            }
            int status = response.statusCode();
            if (status == 429 || status >= 500) {
                lastFailure = new IOException("LLM " + provider + " returned HTTP " + status + "; retry budget exhausted.");
                continue;
            }
            if (status < 200 || status >= 300) {
                throw httpFailure(status, response.body());
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

    IOException httpFailure(int status, String body) {
        String message = "LLM " + provider + " returned HTTP " + status + " from " + endpoint.getHost() + ". ";
        if (status == 401) {
            String code = authenticationCode(body);
            message += "Authentication was rejected using " + credentialSource + ". "
                    + (code.isEmpty() ? "" : "Provider error code: " + code + ". ");
            if (code.equals("account_deactivated") || code.equals("organization_deactivated")) {
                message += "Check the account or organization status with the API provider. ";
            } else {
                message += "Check that the token is complete, active, and issued by the service at this endpoint. ";
            }
            if ("api.openai.com".equalsIgnoreCase(endpoint.getHost())) {
                message += "This is the official OpenAI API: Azure, DeepSeek and third-party gateway keys are not "
                        + "interchangeable with official OpenAI API keys. ChatGPT login/session credentials are not API keys. ";
            }
            message += "A nonempty local llm.api-key overrides the environment key. Correct or rotate that credential "
                    + "in the private params, or configure the endpoint/provider that issued it. "
                    + "Changing the GP seed or increasing timeouts cannot resolve HTTP 401; the request is not retried. "
                    + "Response text is suppressed because providers can echo credentials.";
        } else if (status == 407) {
            message += "The HTTP proxy requires authentication. This is not an LLM API-key error; "
                    + "configure a supported local proxy route. Proxy authentication is not implemented.";
        } else {
            message += "Check credentials, endpoint, model and token settings.";
        }
        return new IOException(message);
    }

    private String authenticationCode(String body) {
        if (body == null || body.length() > 2_000_000) {
            return "";
        }
        try {
            JSONObject error = new JSONObject(body).optJSONObject("error");
            if (error == null) {
                return "";
            }
            for (String field : new String[]{"code", "type"}) {
                String value = error.optString(field, "");
                switch (value) {
                    case "invalid_api_key":
                    case "invalid_token":
                    case "authentication_error":
                    case "invalid_authentication":
                    case "account_deactivated":
                    case "organization_deactivated":
                        return value;
                    default:
                        break;
                }
            }
        } catch (org.json.JSONException ignored) {
            return "";
        }
        return "";
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