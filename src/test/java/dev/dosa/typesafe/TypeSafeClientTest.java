package dev.dosa.typesafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dosa.typesafe.exception.ApiException;
import dev.dosa.typesafe.exception.AuthenticationException;
import dev.dosa.typesafe.exception.InvalidRequestException;
import dev.dosa.typesafe.exception.RateLimitException;
import dev.dosa.typesafe.model.ChoiceAnswer;
import dev.dosa.typesafe.model.Question;
import dev.dosa.typesafe.model.ScoreAnswer;
import dev.dosa.typesafe.model.SystemOneRequest;
import dev.dosa.typesafe.model.SystemOneResponse;
import dev.dosa.typesafe.model.UnknownAnswer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TypeSafeClientTest {

    private static final String TEST_KEY = "test-api-key";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StubServer server;
    private TypeSafeClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new StubServer();
        client = TypeSafeClient.builder()
                .apiKey(TEST_KEY)
                .baseUrl(server.baseUrl())
                .requestTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private static SystemOneRequest threeQuestionRequest() {
        return SystemOneRequest.builder()
                .state(Map.of("ticket", "I want a refund now!"))
                .model("jev-latest")
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
    }

    private static String threeAnswerBody() {
        return """
                {
                  "model": "jev-latest",
                  "answers": {
                    "urgent": { "type": "noul", "noul": 0.93 },
                    "dept": {
                      "type": "choice",
                      "choice": "billing",
                      "probabilities": { "billing": 0.81, "technical": 0.19 },
                      "confidence": 0.81
                    },
                    "frustration": {
                      "type": "score",
                      "score": 2.0,
                      "legend": { "0": "Calm", "1": "Frustrated", "2": "Very angry" },
                      "probabilities": { "0": 0.05, "1": 0.15, "2": 0.80 },
                      "confidence": 0.80
                    }
                  },
                  "usage": { "input_tokens": 210, "output_tokens": 74 }
                }
                """;
    }

    @Test
    void roundTripsAllThreeQuestionTypes() {
        server.enqueue(200, Map.of("x-typesafe-request-id", "req-abc-123"), threeAnswerBody());

        SystemOneResponse resp = client.systemOne(threeQuestionRequest());

        assertEquals("jev-latest", resp.model());
        assertEquals(200, resp.httpStatus());
        assertEquals(1, resp.attempts());
        assertEquals("req-abc-123", resp.requestId().orElseThrow());

        assertEquals(0.93, resp.noul("urgent").orElseThrow(), 1e-9);

        ChoiceAnswer dept = resp.choice("dept").orElseThrow();
        assertEquals("billing", dept.choice());
        assertEquals(0.81, dept.probabilities().get("billing"), 1e-9);
        assertEquals(0.81, dept.confidence(), 1e-9);

        ScoreAnswer frustration = resp.score("frustration").orElseThrow();
        assertEquals(2.0, frustration.score(), 1e-9);
        assertEquals("Very angry", frustration.legend().get("2"));
        assertEquals(0.80, frustration.probabilities().orElseThrow().get("2"), 1e-9);
        assertEquals(0.80, frustration.confidence(), 1e-9);

        assertEquals(210, resp.usage().orElseThrow().inputTokens());
        assertEquals(74, resp.usage().orElseThrow().outputTokens());

        assertEquals(1, resp.nouls().count());
        assertEquals(1, resp.choices().count());
        assertEquals(1, resp.scores().count());
    }

    @Test
    void sendsExpectedWireFormat() throws Exception {
        server.enqueue(200, threeAnswerBody());

        client.systemOne(threeQuestionRequest());

        assertEquals(1, server.requests().size());
        StubServer.RecordedRequest recorded = server.requests().get(0);
        assertEquals("POST", recorded.method());
        assertEquals("/v1/systemone", recorded.path());
        assertEquals("Bearer " + TEST_KEY, recorded.header("Authorization"));

        JsonNode body = MAPPER.readTree(recorded.body());
        assertEquals("I want a refund now!", body.path("state").path("ticket").asText());
        assertEquals("jev-latest", body.path("model").asText());

        JsonNode questions = body.path("questions");
        assertEquals("noul", questions.path("urgent").path("type").asText());
        assertEquals("Explicitly time-sensitive",
                questions.path("urgent").path("criteria").path("true").asText());
        assertEquals("choice", questions.path("dept").path("type").asText());
        assertEquals("score", questions.path("frustration").path("type").asText());
        assertEquals(3, questions.path("frustration").path("criteria").size());

        // Choice criteria must preserve insertion order on the wire.
        String raw = recorded.body();
        assertTrue(raw.indexOf("\"billing\"") < raw.indexOf("\"technical\""));
    }

    @Test
    void noulOmitsCriteriaWhenNotSet() throws Exception {
        server.enqueue(200, threeAnswerBody());

        client.systemOne(SystemOneRequest.builder()
                .state("hello")
                .question("q", Question.noul("Just a question"))
                .build());

        JsonNode body = MAPPER.readTree(server.requests().get(0).body());
        assertFalse(body.path("questions").path("q").has("criteria"));
    }

    @Test
    void clientDefaultModelAppliedWhenRequestHasNone() throws Exception {
        TypeSafeClient withDefault = TypeSafeClient.builder()
                .apiKey(TEST_KEY)
                .baseUrl(server.baseUrl())
                .defaultModel("jev-latest")
                .build();
        server.enqueue(200, threeAnswerBody());

        withDefault.systemOne(SystemOneRequest.builder()
                .state("hi")
                .question("q", Question.noul("?"))
                .build());

        JsonNode body = MAPPER.readTree(server.requests().get(0).body());
        assertEquals("jev-latest", body.path("model").asText());
    }

    @Test
    void choiceRequiresAtLeastTwoOptions() {
        InvalidRequestException ex = assertThrows(InvalidRequestException.class, () ->
                SystemOneRequest.builder()
                        .state("x")
                        .question("c", Question.choice("Pick one").option("only", "Sole option"))
                        .build());
        assertTrue(ex.getMessage().contains("between"));
        assertEquals(0, server.requests().size());
    }

    @Test
    void choiceRejectsMoreThan255Options() {
        var choice = Question.choice("Pick one");
        for (int i = 0; i < 256; i++) {
            choice.option("opt-" + i, "Option " + i);
        }
        assertThrows(InvalidRequestException.class, () ->
                SystemOneRequest.builder().state("x").question("c", choice).build());
    }

    @Test
    void scoreRequiresAtLeastTwoLevels() {
        assertThrows(InvalidRequestException.class, () ->
                SystemOneRequest.builder()
                        .state("x")
                        .question("s", Question.score("Rate it").level("Only level"))
                        .build());
        assertEquals(0, server.requests().size());
    }

    @Test
    void emptyQuestionsMapRejected() {
        assertThrows(InvalidRequestException.class, () ->
                SystemOneRequest.builder().state("x").build());
    }

    @Test
    void blankQuestionNameRejected() {
        assertThrows(InvalidRequestException.class, () ->
                SystemOneRequest.builder()
                        .state("x")
                        .question("  ", Question.noul("?"))
                        .build());
    }

    @Test
    void missingStateRejected() {
        assertThrows(InvalidRequestException.class, () ->
                SystemOneRequest.builder()
                        .question("q", Question.noul("?"))
                        .build());
    }

    @Test
    void unknownAnswerTypeDoesNotThrow() {
        server.enqueue(200, """
                {
                  "model": "jev-latest",
                  "answers": {
                    "future": { "type": "quantum", "entangled": true, "value": "yes" }
                  }
                }
                """);

        SystemOneResponse resp = client.systemOne(SystemOneRequest.builder()
                .state("x")
                .question("future", Question.noul("?"))
                .build());

        UnknownAnswer unknown = assertInstanceOf(UnknownAnswer.class,
                resp.answer("future").orElseThrow());
        assertEquals("quantum", unknown.type());
        assertTrue(unknown.raw().path("entangled").asBoolean());
        assertTrue(resp.noul("future").isEmpty());
    }

    @Test
    void scoreAnswerWithoutProbabilitiesDeserializes() {
        server.enqueue(200, """
                {
                  "model": "jev-latest",
                  "answers": {
                    "s": {
                      "type": "score",
                      "score": 1.0,
                      "legend": { "0": "Low", "1": "High" },
                      "confidence": 0.7
                    }
                  }
                }
                """);

        ScoreAnswer answer = client.systemOne(SystemOneRequest.builder()
                        .state("x")
                        .question("s", Question.score("?").level("Low").level("High"))
                        .build())
                .score("s")
                .orElseThrow();

        assertEquals(1.0, answer.score(), 1e-9);
        assertTrue(answer.probabilities().isEmpty());
    }

    @Test
    void retriesOn429ThenSucceeds() {
        server.enqueue(429, "{\"error\":\"slow down\"}");
        server.enqueue(200, Map.of("x-typesafe-request-id", "req-after-retry"), threeAnswerBody());

        SystemOneResponse resp = client.systemOne(threeQuestionRequest());

        assertEquals(2, server.requests().size());
        assertEquals(2, resp.attempts());
        assertEquals("req-after-retry", resp.requestId().orElseThrow());
        assertEquals(0.93, resp.noul("urgent").orElseThrow(), 1e-9);
    }

    @Test
    void retriesOn5xxThenSucceeds() {
        server.enqueue(503, "{\"error\":\"unavailable\"}");
        server.enqueue(200, threeAnswerBody());

        SystemOneResponse resp = client.systemOne(threeQuestionRequest());

        assertEquals(2, resp.attempts());
    }

    @Test
    void rateLimitExhaustionThrows() {
        server.enqueue(429, "{}");
        server.enqueue(429, "{}");
        server.enqueue(429, "{}");

        RateLimitException ex = assertThrows(RateLimitException.class,
                () -> client.systemOne(threeQuestionRequest()));

        assertEquals(3, ex.attempts());
        assertEquals(3, server.requests().size());
    }

    @Test
    void authenticationFailureDoesNotRetry() {
        server.enqueue(401, Map.of("x-typesafe-request-id", "req-denied"),
                "{\"error\":\"bad key\"}");

        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> client.systemOne(threeQuestionRequest()));

        assertEquals(401, ex.status());
        assertEquals("req-denied", ex.requestId().orElseThrow());
        assertEquals(1, server.requests().size());
    }

    @Test
    void other4xxThrowsApiExceptionWithBody() {
        server.enqueue(422, "{\"error\":\"invalid questions\"}");

        ApiException ex = assertThrows(ApiException.class,
                () -> client.systemOne(threeQuestionRequest()));

        assertEquals(422, ex.status());
        assertTrue(ex.body().orElseThrow().contains("invalid questions"));
        assertEquals(1, server.requests().size());
    }

    @Test
    void asyncRoundTrip() throws Exception {
        server.enqueue(200, threeAnswerBody());

        SystemOneResponse resp = client.systemOneAsync(threeQuestionRequest())
                .get(10, TimeUnit.SECONDS);

        assertEquals("jev-latest", resp.model());
        assertEquals(1, resp.attempts());
    }

    @Test
    void responseWithoutUsageDeserializes() {
        server.enqueue(200, """
                { "model": "m", "answers": { "u": { "type": "noul", "noul": 0.5 } } }
                """);

        SystemOneResponse resp = client.systemOne(SystemOneRequest.builder()
                .state("x")
                .question("u", Question.noul("?"))
                .build());

        assertTrue(resp.usage().isEmpty());
        assertEquals(0.5, resp.noul("u").orElseThrow(), 1e-9);
    }

    @Test
    void builderRequiresApiKey() {
        assertThrows(IllegalArgumentException.class, () ->
                TypeSafeClient.builder().baseUrl(server.baseUrl()).build());
    }
}
