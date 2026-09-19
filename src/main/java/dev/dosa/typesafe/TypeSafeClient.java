package dev.dosa.typesafe;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dosa.typesafe.exception.ApiException;
import dev.dosa.typesafe.exception.AuthenticationException;
import dev.dosa.typesafe.exception.InvalidRequestException;
import dev.dosa.typesafe.exception.NetworkException;
import dev.dosa.typesafe.exception.RateLimitException;
import dev.dosa.typesafe.exception.TypeSafeException;
import dev.dosa.typesafe.model.ModelInfo;
import dev.dosa.typesafe.model.SystemOneRequest;
import dev.dosa.typesafe.model.SystemOneResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Client for TypeSafe AI's System One API.
 *
 * <p>Constructed via {@link #builder()}:</p>
 *
 * <pre>{@code
 * TypeSafeClient client = TypeSafeClient.builder()
 *         .apiKey(System.getenv("TYPESAFE_API_KEY"))
 *         .defaultModel("jev-latest")
 *         .requestTimeout(Duration.ofSeconds(10))
 *         .build();
 *
 * SystemOneResponse resp = client.systemOne(request);        // sync
 * CompletableFuture<SystemOneResponse> f = client.systemOneAsync(request); // async
 * }</pre>
 *
 * <p>The client retries automatically on HTTP 429 and 5xx responses with
 * exponential backoff and jitter (honouring a {@code Retry-After} header when
 * present), up to the configured {@code maxAttempts}. The API key is sent only
 * in the {@code Authorization} header and never appears in exception messages
 * or logs.</p>
 *
 * <p>Instances are thread-safe.</p>
 */
public final class TypeSafeClient {

    /** Default API base URL. */
    public static final String DEFAULT_BASE_URL = "https://api.typesafe.ai";

    static final String SYSTEM_ONE_PATH = "/v1/systemone";
    static final String MODELS_PATH = "/v1/models";
    static final String REQUEST_ID_HEADER = "x-typesafe-request-id";
    static final String SDK_VERSION = loadVersion();
    static final String USER_AGENT = "typesafe-java-sdk/" + SDK_VERSION;

    private final HttpClient http;
    private final String apiKey;
    private final String baseUrl;
    private final String defaultModel;
    private final Duration requestTimeout;
    private final int maxAttempts;
    private final Duration initialRetryDelay;
    private final Duration maxRetryDelay;

    TypeSafeClient(TypeSafeClientBuilder b) {
        this.apiKey = b.apiKey;
        this.baseUrl = b.baseUrl;
        this.defaultModel = b.defaultModel;
        this.requestTimeout = b.requestTimeout;
        this.maxAttempts = b.maxAttempts;
        this.initialRetryDelay = b.initialRetryDelay;
        this.maxRetryDelay = b.maxRetryDelay;
        this.http = b.httpClient != null
                ? b.httpClient
                : HttpClient.newBuilder().connectTimeout(b.connectTimeout).build();
    }

    /**
     * Creates a new client builder.
     *
     * @return a {@link TypeSafeClientBuilder}
     */
    public static TypeSafeClientBuilder builder() {
        return new TypeSafeClientBuilder();
    }

    /**
     * Sends a System One request synchronously.
     *
     * @param request the request to send
     * @return the parsed response, including transport metadata
     * @throws AuthenticationException on HTTP 401/403 (never retried)
     * @throws RateLimitException on HTTP 429 after all retries are exhausted
     * @throws ApiException on other error statuses or an unparseable body
     * @throws NetworkException on transport-level failures
     * @throws TypeSafeException if the calling thread is interrupted
     */
    public SystemOneResponse systemOne(SystemOneRequest request) {
        Objects.requireNonNull(request, "request");
        AttemptedResponse result = sendWithRetries(newSystemOneRequest(request));
        HttpResponse<String> resp = result.response();
        return SystemOneResponse.parse(resp.body(), resp.statusCode(),
                requestId(resp), result.attempts());
    }

    /**
     * Sends a System One request asynchronously. Retry backoff does not block a
     * thread - it is scheduled on a delayed executor.
     *
     * @param request the request to send
     * @return a future completing with the parsed response, or exceptionally
     *         with the same exception types as {@link #systemOne(SystemOneRequest)}
     */
    public CompletableFuture<SystemOneResponse> systemOneAsync(SystemOneRequest request) {
        Objects.requireNonNull(request, "request");
        return sendWithRetriesAsync(newSystemOneRequest(request), 1)
                .thenApply(result -> {
                    HttpResponse<String> resp = result.response();
                    return SystemOneResponse.parse(resp.body(), resp.statusCode(),
                            requestId(resp), result.attempts());
                });
    }

    /**
     * Lists the models available on the API ({@code GET /v1/models}).
     *
     * @return the available models, in server order
     * @throws AuthenticationException on HTTP 401/403 (never retried)
     * @throws RateLimitException on HTTP 429 after all retries are exhausted
     * @throws ApiException on other error statuses or an unparseable body
     * @throws NetworkException on transport-level failures
     */
    public List<ModelInfo> models() {
        AttemptedResponse result = sendWithRetries(newModelsRequest());
        return ModelInfo.parseList(result.response().body());
    }

    /**
     * Lists the models available on the API asynchronously.
     *
     * @return a future completing with the model list, or exceptionally with
     *         the same exception types as {@link #models()}
     */
    public CompletableFuture<List<ModelInfo>> modelsAsync() {
        return sendWithRetriesAsync(newModelsRequest(), 1)
                .thenApply(result -> ModelInfo.parseList(result.response().body()));
    }

    private record AttemptedResponse(HttpResponse<String> response, int attempts) {
    }

    private AttemptedResponse sendWithRetries(HttpRequest httpRequest) {
        for (int attempt = 1; ; attempt++) {
            HttpResponse<String> resp = send(httpRequest);
            int status = resp.statusCode();
            if (isSuccess(status)) {
                return new AttemptedResponse(resp, attempt);
            }
            if (!isRetryable(status) || attempt >= maxAttempts) {
                throw errorFor(status, resp.body(), requestId(resp), attempt);
            }
            sleep(retryDelay(resp, attempt));
        }
    }

    private CompletableFuture<AttemptedResponse> sendWithRetriesAsync(HttpRequest httpRequest, int attempt) {
        CompletableFuture<AttemptedResponse> result = new CompletableFuture<>();
        http.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .whenComplete((resp, err) -> {
                    if (err != null) {
                        result.completeExceptionally(unwrap(err));
                        return;
                    }
                    int status = resp.statusCode();
                    if (isSuccess(status)) {
                        result.complete(new AttemptedResponse(resp, attempt));
                        return;
                    }
                    if (isRetryable(status) && attempt < maxAttempts) {
                        scheduleRetry(httpRequest, attempt + 1, retryDelay(resp, attempt), result);
                    } else {
                        result.completeExceptionally(
                                errorFor(status, resp.body(), requestId(resp), attempt));
                    }
                });
        return result;
    }

    private void scheduleRetry(HttpRequest httpRequest, int attempt, Duration delay,
            CompletableFuture<AttemptedResponse> result) {
        CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS)
                .execute(() -> sendWithRetriesAsync(httpRequest, attempt)
                        .whenComplete((r, e) -> {
                            if (e != null) {
                                result.completeExceptionally(e);
                            } else {
                                result.complete(r);
                            }
                        }));
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new NetworkException("Request to TypeSafe API failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TypeSafeException("Interrupted while waiting for TypeSafe API response", e);
        }
    }

    private HttpRequest newSystemOneRequest(SystemOneRequest request) {
        ObjectNode body = request.toJson();
        body.put("model", resolveModel(request));
        return baseRequest(SYSTEM_ONE_PATH)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
    }

    private HttpRequest newModelsRequest() {
        return baseRequest(MODELS_PATH).GET().build();
    }

    private HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(requestTimeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT);
    }

    /**
     * Resolves the effective model: the request's model wins, then the client's
     * default model. The API requires a non-empty model - there is no
     * server-side default - so a missing model fails client-side.
     */
    private String resolveModel(SystemOneRequest request) {
        String model = request.model();
        if (model == null || model.isBlank()) {
            model = defaultModel;
        }
        if (model == null || model.isBlank()) {
            throw new InvalidRequestException(
                    "A model is required: set one via SystemOneRequest.builder().model(...)"
                            + " or TypeSafeClient.builder().defaultModel(...)"
                            + " (see client.models() for available models)");
        }
        return model;
    }

    private static boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }

    private static boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }

    private static String requestId(HttpResponse<?> resp) {
        return resp.headers().firstValue(REQUEST_ID_HEADER).orElse(null);
    }

    private static TypeSafeException errorFor(int status, String body, String requestId, int attempts) {
        if (status == 401 || status == 403) {
            return new AuthenticationException(status, requestId);
        }
        if (status == 429) {
            return new RateLimitException(requestId, attempts);
        }
        return new ApiException(status, body, requestId);
    }

    private Duration retryDelay(HttpResponse<?> resp, int attempt) {
        Duration retryAfter = resp.headers().firstValue("Retry-After")
                .map(TypeSafeClient::parseRetryAfter)
                .orElse(null);
        if (retryAfter != null) {
            return retryAfter.compareTo(maxRetryDelay) > 0 ? maxRetryDelay : retryAfter;
        }
        long base = initialRetryDelay.toMillis() << (attempt - 1);
        long capped = Math.min(base, maxRetryDelay.toMillis());
        long jittered = ThreadLocalRandom.current().nextLong(capped / 2, capped + 1);
        return Duration.ofMillis(jittered);
    }

    private static String loadVersion() {
        try (InputStream in = TypeSafeClient.class.getResourceAsStream("/typesafe-java-sdk.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String version = props.getProperty("version");
                if (version != null && !version.isBlank() && !version.contains("${")) {
                    return version;
                }
            }
        } catch (IOException ignored) {
            // fall through to the fallback version below
        }
        return "unknown";
    }

    private static Duration parseRetryAfter(String value) {
        value = value.trim();
        try {
            return Duration.ofSeconds(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            // fall through to HTTP-date
        }
        try {
            Duration d = Duration.between(ZonedDateTime.now(),
                    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME));
            return d.isNegative() ? Duration.ZERO : d;
        } catch (Exception ignored) {
            return Duration.ZERO;
        }
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TypeSafeException("Interrupted during retry backoff", e);
        }
    }

    private static TypeSafeException unwrap(Throwable err) {
        Throwable cause = err;
        while (cause instanceof CompletionException || cause instanceof ExecutionException) {
            if (cause.getCause() == null) {
                break;
            }
            cause = cause.getCause();
        }
        if (cause instanceof TypeSafeException tse) {
            return tse;
        }
        if (cause instanceof IOException ioe) {
            return new NetworkException("Request to TypeSafe API failed: " + ioe.getMessage(), ioe);
        }
        if (cause instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return new TypeSafeException("Interrupted while waiting for TypeSafe API response", cause);
        }
        return new TypeSafeException("Request to TypeSafe API failed: " + cause.getMessage(), cause);
    }
}
