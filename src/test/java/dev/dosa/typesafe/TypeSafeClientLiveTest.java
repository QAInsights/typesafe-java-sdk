package dev.dosa.typesafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dosa.typesafe.exception.ApiException;
import dev.dosa.typesafe.exception.AuthenticationException;
import dev.dosa.typesafe.exception.InvalidRequestException;
import dev.dosa.typesafe.model.ModelInfo;
import dev.dosa.typesafe.model.Question;
import dev.dosa.typesafe.model.SystemOneRequest;
import dev.dosa.typesafe.model.SystemOneResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Live tests against the real TypeSafe API (https://api.typesafe.ai).
 *
 * <p>Skipped entirely unless the {@code TYPESAFE_API_KEY} environment variable
 * is set - the SDK picks the key up automatically, no credentials in code.</p>
 *
 * <pre>
 *   # PowerShell
 *   $env:TYPESAFE_API_KEY = "ts-..."
 *   mvn test -Dtest=TypeSafeClientLiveTest
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
class TypeSafeClientLiveTest {

    private final TypeSafeClient client = TypeSafeClient.builder()
            .requestTimeout(Duration.ofSeconds(30))
            .build();

    private static SystemOneRequest sampleRequest() {
        return SystemOneRequest.builder()
                .state(Map.of("ticket",
                        "Your app charged me twice and support ignored me for a week. Fix this now."))
                .model("jev-latest")
                .question("urgent", Question.noul("Does this message convey urgency?")
                        .whenTrue("Demands immediate attention or mentions a deadline")
                        .whenFalse("Casual inquiry with no time pressure"))
                .question("team", Question.choice("Which team should handle this?")
                        .option("billing", "Payments, refunds, invoices, charges")
                        .option("technical", "Bugs, crashes, errors")
                        .option("support", "General help and account questions"))
                .question("frustration", Question.score("How frustrated is the customer?")
                        .level("Calm")
                        .level("Mildly annoyed")
                        .level("Frustrated")
                        .level("Very angry"))
                .build();
    }

    @Test
    void liveRoundTrip() {
        SystemOneResponse resp = client.systemOne(sampleRequest());

        assertEquals(200, resp.httpStatus());
        assertEquals(1, resp.attempts());
        assertTrue(resp.requestId().isPresent(), "x-typesafe-request-id header should be present");
        assertNotNull(resp.model());

        assertTrue(resp.noul("urgent").isPresent(), "noul answer missing");
        double urgency = resp.noul("urgent").orElseThrow();
        assertTrue(urgency >= 0.0 && urgency <= 1.0, "noul out of range: " + urgency);

        assertTrue(resp.choice("team").isPresent(), "choice answer missing");
        String team = resp.choice("team").orElseThrow().choice();
        assertTrue(team.equals("billing") || team.equals("technical") || team.equals("support"),
                "unexpected choice: " + team);

        assertTrue(resp.score("frustration").isPresent(), "score answer missing");
        assertTrue(resp.score("frustration").orElseThrow().score() >= 0.0);

        System.out.println("model=" + resp.model()
                + " requestId=" + resp.requestId().orElse("-")
                + " urgency=" + urgency
                + " team=" + team
                + " frustration=" + resp.score("frustration").orElseThrow().score()
                + " usage=" + resp.usage().map(u -> u.inputTokens() + "/" + u.outputTokens()).orElse("-"));
    }

    @Test
    void liveAsyncRoundTrip() throws Exception {
        SystemOneResponse resp = client.systemOneAsync(sampleRequest())
                .get(60, TimeUnit.SECONDS);
        assertEquals(200, resp.httpStatus());
        assertTrue(resp.noul("urgent").isPresent());
    }

    @Test
    void badKeyRejectedWithAuthenticationException() {
        TypeSafeClient badClient = TypeSafeClient.builder()
                .apiKey("definitely-not-a-real-key")
                .build();
        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> badClient.systemOne(sampleRequest()));
        assertTrue(ex.status() == 401 || ex.status() == 403);
    }

    @Test
    void missingModelRejectedClientSide() {
        TypeSafeClient noDefault = TypeSafeClient.builder().build();
        SystemOneRequest request = SystemOneRequest.builder()
                .state("x")
                .question("q", Question.noul("Is this urgent?"))
                .build();
        assertThrows(InvalidRequestException.class, () -> noDefault.systemOne(request));
    }

    @Test
    void unknownModelRejectedByApi() {
        ApiException ex = assertThrows(ApiException.class, () ->
                client.systemOne(SystemOneRequest.builder()
                        .state("x")
                        .model("gpt-4")
                        .question("q", Question.noul("Is this urgent?"))
                        .build()));
        assertEquals(400, ex.status());
    }

    @Test
    void listsAvailableModels() {
        List<ModelInfo> models = client.models();
        assertFalse(models.isEmpty());
        assertTrue(models.stream().anyMatch(m -> "jev-latest".equals(m.name())),
                "jev-latest not in " + models);
        models.forEach(m -> assertNotNull(m.name()));
        System.out.println("models=" + models);
    }

    @Test
    void unicodeQuestionNameRoundTrips() {
        SystemOneResponse resp = client.systemOne(SystemOneRequest.builder()
                .state("I want a refund, please.")
                .model("jev-latest")
                .question("café-日本語", Question.noul("Is this a complaint?"))
                .build());
        assertTrue(resp.answer("café-日本語").isPresent(),
                "unicode question name missing from answers: " + resp.answers().keySet());
    }
}
