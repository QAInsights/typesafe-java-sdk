package dev.dosa.typesafe;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Fluent builder for {@link TypeSafeClient}.
 *
 * <p>Only {@link #apiKey(String)} is required:</p>
 *
 * <pre>{@code
 * TypeSafeClient client = TypeSafeClient.builder()
 *         .apiKey(System.getenv("TYPESAFE_API_KEY"))
 *         .defaultModel("jev-latest")            // optional
 *         .baseUrl("http://localhost:8080")      // optional override, e.g. tests
 *         .requestTimeout(Duration.ofSeconds(10))
 *         .maxAttempts(3)
 *         .build();
 * }</pre>
 */
public final class TypeSafeClientBuilder {

    String apiKey;
    String baseUrl;
    String defaultModel;
    Duration requestTimeout = Duration.ofSeconds(30);
    Duration connectTimeout = Duration.ofSeconds(10);
    int maxAttempts = 3;
    Duration initialRetryDelay = Duration.ofMillis(200);
    Duration maxRetryDelay = Duration.ofSeconds(10);
    HttpClient httpClient;

    TypeSafeClientBuilder() {
    }

    /**
     * Sets the API key used for the {@code Authorization: Bearer} header.
     * Required unless the {@code TYPESAFE_API_KEY} environment variable is set.
     * The key is never included in exception messages or logs.
     *
     * @param apiKey the TypeSafe API key
     * @return this builder
     */
    public TypeSafeClientBuilder apiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    /**
     * Overrides the API base URL - primarily for pointing the client at a test
     * server. Falls back to the {@code TYPESAFE_BASE_URL} environment variable
     * when unset, then to {@link TypeSafeClient#DEFAULT_BASE_URL}.
     *
     * @param baseUrl the base URL, without a trailing path (e.g.
     *        {@code "https://api.typesafe.ai"})
     * @return this builder
     */
    public TypeSafeClientBuilder baseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    /**
     * Sets the model used for requests that don't specify one. When unset (and
     * the request doesn't set one either), an empty string is sent and the
     * server picks its default.
     *
     * @param defaultModel the model name, e.g. {@code "jev-latest"}
     * @return this builder
     */
    public TypeSafeClientBuilder defaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
        return this;
    }

    /**
     * Sets the per-request timeout applied to each HTTP attempt. Defaults to
     * 30 seconds.
     *
     * @param requestTimeout the request timeout
     * @return this builder
     */
    public TypeSafeClientBuilder requestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
        return this;
    }

    /**
     * Sets the connection timeout for the underlying HTTP client. Ignored when
     * a custom {@link #httpClient(HttpClient)} is supplied. Defaults to
     * 10 seconds.
     *
     * @param connectTimeout the connect timeout
     * @return this builder
     */
    public TypeSafeClientBuilder connectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    /**
     * Sets the maximum number of HTTP attempts per request, including the
     * initial attempt. {@code 1} disables retries. Defaults to 3.
     *
     * @param maxAttempts the maximum attempt count, must be at least 1
     * @return this builder
     */
    public TypeSafeClientBuilder maxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
        return this;
    }

    /**
     * Sets the base delay for exponential backoff between retries; the actual
     * delay doubles per attempt (with jitter) up to
     * {@link #maxRetryDelay(Duration)}. Defaults to 200 ms.
     *
     * @param initialRetryDelay the initial backoff delay
     * @return this builder
     */
    public TypeSafeClientBuilder initialRetryDelay(Duration initialRetryDelay) {
        this.initialRetryDelay = initialRetryDelay;
        return this;
    }

    /**
     * Sets the ceiling for retry backoff, and the cap applied to server-provided
     * {@code Retry-After} values. Defaults to 10 seconds.
     *
     * @param maxRetryDelay the maximum backoff delay
     * @return this builder
     */
    public TypeSafeClientBuilder maxRetryDelay(Duration maxRetryDelay) {
        this.maxRetryDelay = maxRetryDelay;
        return this;
    }

    /**
     * Supplies a custom {@link HttpClient} for transport - e.g. with a custom
     * executor, proxy, or TLS configuration. When unset, a default client is
     * created with the configured {@link #connectTimeout(Duration)}.
     *
     * @param httpClient the HTTP client to use
     * @return this builder
     */
    public TypeSafeClientBuilder httpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
        return this;
    }

    /**
     * Builds the client.
     *
     * @return a {@link TypeSafeClient}
     * @throws IllegalArgumentException if no API key is available or the
     *         configuration is invalid
     */
    public TypeSafeClient build() {
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("TYPESAFE_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                    "apiKey is required (set it via apiKey(...) or the TYPESAFE_API_KEY environment variable)");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = System.getenv("TYPESAFE_BASE_URL");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = TypeSafeClient.DEFAULT_BASE_URL;
        }
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        if (initialRetryDelay == null || initialRetryDelay.isNegative()) {
            throw new IllegalArgumentException("initialRetryDelay must not be negative");
        }
        if (maxRetryDelay == null || maxRetryDelay.isNegative()) {
            throw new IllegalArgumentException("maxRetryDelay must not be negative");
        }
        return new TypeSafeClient(this);
    }
}
