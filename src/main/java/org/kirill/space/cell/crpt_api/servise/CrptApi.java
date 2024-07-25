package org.kirill.space.cell.crpt_api.servise;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.*;

@Slf4j
public class CrptApi {
    private static final URI API_URI = URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.semaphore = new Semaphore(requestLimit);
        this.scheduler = Executors.newScheduledThreadPool(1);

        scheduler.scheduleAtFixedRate(semaphore::release, 0, 1, timeUnit);
    }

    public HttpResponse<String> createDocument(Document document, String signature) throws Exception {
        if (document == null || signature == null) {
            throw new IllegalArgumentException("Документ и подпись не могут быть пустыми");
        }

        semaphore.acquire();

        try {
            DocumentRequest documentRequest = new DocumentRequest(document, signature);
            String requestBody = objectMapper.writeValueAsString(documentRequest);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(API_URI)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        } catch (IOException | InterruptedException e) {
            log.error("Ошибка при создании документа", e);
            throw e;
        } finally {
            semaphore.release();
        }
    }

    public record DocumentRequest(Document document, String signature) {
    }

    public record Document(String participantInn, String docId, String docStatus, String docType,
                           boolean importRequest, String ownerInn, String producerInn,
                           String productionDate, String productionType,
                           Product[] products, String regDate, String regNumber) {
    }

    public record Product(String certificateDocument, String certificateDocumentDate,
                          String certificateDocumentNumber, String ownerInn,
                          String producerInn, String productionDate,
                          String tnvedCode, String uitCode, String uituCode) {
    }
}
