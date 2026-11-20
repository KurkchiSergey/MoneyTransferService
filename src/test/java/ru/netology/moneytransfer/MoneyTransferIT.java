package ru.netology.moneytransfer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Интеграционный тест: запускает готовый Docker-образ сервиса и обращается к нему по HTTP.
 * Перед запуском нужно собрать образ:  docker build -t money-transfer-service .
 * Запуск:  mvn verify
 */
@Testcontainers
class MoneyTransferIT {

    private static final String CARD_FROM = "1111111111111111";
    private static final String CARD_TO = "2222222222222222";

    @Container
    private static final GenericContainer<?> SERVICE = new GenericContainer<>("money-transfer-service")
            .withExposedPorts(8080)
            .waitingFor(Wait.forLogMessage(".*Started MoneyTransferApplication.*", 1));

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private HttpResponse<String> post(String path, String json) throws Exception {
        URI uri = URI.create("http://" + SERVICE.getHost() + ":" + SERVICE.getMappedPort(8080) + path);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String transferJson(String cardFrom, String cvv, long amount) {
        return """
                {"cardFromNumber":"%s","cardFromValidTill":"12/30","cardFromCVV":"%s",
                 "cardToNumber":"%s","amount":{"value":%d,"currency":"RUR"}}
                """.formatted(cardFrom, cvv, CARD_TO, amount);
    }

    @Test
    void transferAndConfirmSucceed() throws Exception {
        HttpResponse<String> transfer = post("/transfer", transferJson(CARD_FROM, "111", 10_000));
        assertEquals(200, transfer.statusCode());
        String operationId = mapper.readTree(transfer.body()).get("operationId").asText();
        assertFalse(operationId.isBlank());

        HttpResponse<String> confirm = post("/confirmOperation",
                "{\"operationId\":\"" + operationId + "\",\"code\":\"0000\"}");

        assertEquals(200, confirm.statusCode());
        assertEquals(operationId, mapper.readTree(confirm.body()).get("operationId").asText());
    }

    @Test
    void transferWithWrongCvvReturns400() throws Exception {
        HttpResponse<String> response = post("/transfer", transferJson(CARD_FROM, "999", 10_000));

        assertEquals(400, response.statusCode());
        JsonNode error = mapper.readTree(response.body());
        assertEquals("Неверные данные карты отправителя", error.get("message").asText());
    }

    @Test
    void confirmWithWrongCodeReturns400() throws Exception {
        HttpResponse<String> transfer = post("/transfer", transferJson(CARD_FROM, "111", 10_000));
        String operationId = mapper.readTree(transfer.body()).get("operationId").asText();

        HttpResponse<String> confirm = post("/confirmOperation",
                "{\"operationId\":\"" + operationId + "\",\"code\":\"1234\"}");

        assertEquals(400, confirm.statusCode());
    }

    @Test
    void confirmUnknownOperationReturns400() throws Exception {
        HttpResponse<String> confirm = post("/confirmOperation", "{\"operationId\":\"unknown\",\"code\":\"0000\"}");

        assertEquals(400, confirm.statusCode());
    }
}
