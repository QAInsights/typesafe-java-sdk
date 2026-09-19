# typesafe-java-sdk

> **Unofficial community SDK.** This project is not affiliated with, endorsed
> by, or sponsored by TypeSafe AI. "TypeSafe" and all related names, marks, and
> logos are trademarks of TypeSafe AI — used here solely to identify the API
> this client targets. Use at your own risk; the API may change without notice.

Java client for [TypeSafe AI](https://api.typesafe.ai)'s **System One** API
(`POST /v1/systemone`).

- Java 21+, Maven build
- `java.net.http.HttpClient` transport — no extra HTTP dependency
- Jackson for JSON (only runtime dependency)
- Sync + async (`CompletableFuture`) APIs
- Automatic retries with exponential backoff + jitter on 429/5xx, honoring
  `Retry-After`
- Sealed `Question`/`Answer` hierarchies; unknown answer types degrade to a
  forward-compatible `UnknownAnswer`

## Quickstart

```java
import dev.dosa.typesafe.TypeSafeClient;
import dev.dosa.typesafe.model.*;

TypeSafeClient client = TypeSafeClient.builder()
        .apiKey(System.getenv("TYPESAFE_API_KEY"))
        .defaultModel("jev-latest")        // optional
        .requestTimeout(Duration.ofSeconds(10))
        .build();

SystemOneRequest request = SystemOneRequest.builder()
        .state(Map.of("ticket", "I want a refund now!"))
        .question("urgent", Question.noul("Does this convey urgency?")
                .whenTrue("Explicitly time-sensitive")
                .whenFalse("No time pressure"))
        .question("dept", Question.choice("Which team?")
                .option("billing", "Payment issues")
                .option("technical", "Bugs"))
        .question("frustration", Question.score("How frustrated?")
                .level("Calm")
                .level("Frustrated")
                .level("Very angry"))
        .build();

SystemOneResponse resp = client.systemOne(request);                    // sync
// CompletableFuture<SystemOneResponse> f = client.systemOneAsync(request); // async

resp.noul("urgent").ifPresent(p -> System.out.println("urgency: " + p));
resp.choice("dept").ifPresent(c -> System.out.println("team: " + c.choice()));
resp.score("frustration").ifPresent(s -> System.out.println("score: " + s.score()));
```

## Building questions

One static factory + fluent builder per question type:

```java
Question.noul("Does this convey urgency?")
        .whenTrue("Explicitly time-sensitive")
        .whenFalse("No time pressure");

Question.choice("Which team?")
        .option("billing", "Payment issues")
        .option("technical", "Bugs");

Question.score("How frustrated?")
        .level("Calm")
        .level("Frustrated")
        .level("Very angry");
```

Validation runs client-side in `SystemOneRequest.builder().build()`, before any
network call, and throws `InvalidRequestException`: empty questions map, blank
question names, choice questions with fewer than 2 or more than 255 options,
score questions with fewer than 2 levels.

## Reading answers

```java
Optional<Double>        n = resp.noul("urgent");        // NoulAnswer probability
Optional<ChoiceAnswer>  c = resp.choice("dept");
Optional<ScoreAnswer>   s = resp.score("frustration");
Optional<Answer>        a = resp.answer("anything");    // untyped lookup

Stream<Entry<String, NoulAnswer>>   allNouls   = resp.nouls();
Stream<Entry<String, ChoiceAnswer>> allChoices = resp.choices();
Stream<Entry<String, ScoreAnswer>>  allScores  = resp.scores();
```

Response metadata (kept separate from answer data):

```java
resp.requestId();   // Optional<String> — x-typesafe-request-id header
resp.httpStatus();  // final HTTP status after retries
resp.attempts();    // number of HTTP attempts made
resp.usage();       // Optional<Usage> — input/output token counts
```

## Errors

All exceptions extend the unchecked `dev.dosa.typesafe.exception.TypeSafeException`:

| Exception                  | When                                                        |
|----------------------------|-------------------------------------------------------------|
| `InvalidRequestException`  | Client-side validation failure (thrown before any network)  |
| `AuthenticationException`  | HTTP 401/403 — never retried                                |
| `RateLimitException`       | HTTP 429 after all retry attempts exhausted                 |
| `ApiException`             | Other error statuses / unparseable body (status + body + request id) |
| `NetworkException`         | Transport-level `IOException` (timeouts, DNS, refused)      |

The API key is only ever sent in the `Authorization` header and never appears
in exception messages.

## Configuration

```java
TypeSafeClient.builder()
        .apiKey(...)                     // required
        .baseUrl(...)                    // default https://api.typesafe.ai
        .defaultModel(...)               // model used when request has none
        .requestTimeout(Duration)        // per attempt, default 30s
        .connectTimeout(Duration)        // default 10s
        .maxAttempts(3)                  // retries incl. initial attempt
        .initialRetryDelay(Duration)     // backoff base, default 200ms
        .maxRetryDelay(Duration)         // backoff cap, default 10s
        .httpClient(HttpClient)          // custom transport
        .build();
```

## Build & test

```bash
mvn test      # compiles and runs the test suite (JDK-local stub HTTP server)
mvn package   # builds the jar
```
