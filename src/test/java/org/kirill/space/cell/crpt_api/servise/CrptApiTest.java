package org.kirill.space.cell.crpt_api.servise;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrptApiTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Semaphore semaphore;

    private CrptApi crptApi;

    @BeforeEach
    void setUp() throws Exception {
        crptApi = new CrptApi(TimeUnit.SECONDS, 1);

        setPrivateField(crptApi, "httpClient", httpClient);
        setPrivateField(crptApi, "objectMapper", objectMapper);
        setPrivateField(crptApi, "semaphore", semaphore);
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("create document return success")
    void createDocument_ReturnDocumentSuccess() throws Exception {
        // given
        var document = new CrptApi.Document(
                "123", "doc1", "status", "type",
                true, "12345", "00111", "2020-01-23",
                "prodType",
                new CrptApi.Product[]{}, "2020-01-23", "001");
        var signature = "signature";

        var responseMock = mock(HttpResponse.class);
        when(responseMock.body()).thenReturn("response body");

        when(semaphore.tryAcquire(1, TimeUnit.SECONDS)).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("request body");
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString()))).thenReturn(responseMock);

        // when
        var result = crptApi.createDocument(document, signature);

        // then
        assertTrue(result.isSuccess());
        assertNotNull(result.getValue());
        assertEquals("response body", result.getValue().body());
        verify(semaphore).release();
    }

    @Test
    @DisplayName("create document return failure")
    void createDocument_ReturnFailure() throws Exception {
        // given
        CrptApi.Document document = null;
        String signature = null;

        // when
        var result = crptApi.createDocument(document, signature);

        // then
        assertFalse(result.isSuccess());
        assertNull(result.getValue());
        assertEquals("Документ и подпись не могут быть пустыми", result.getError());
    }

    @Test
    @DisplayName("createDocument return failure when semaphore time out")
    void createDocument_ReturnFailureWhenSemaphoreTimeOut() throws Exception {
        // given
        var document = new CrptApi.Document(
                "123", "doc1", "status", "type",
                true, "12345", "00111", "2020-01-23",
                "prodType",
                new CrptApi.Product[]{}, "2020-01-23", "001");
        var signature = "signature";

        when(semaphore.tryAcquire(1, TimeUnit.SECONDS)).thenReturn(false);

        // when
        var result = crptApi.createDocument(document, signature);

        // then
        assertFalse(result.isSuccess());
        assertNull(result.getValue());
        assertEquals("Тайм-аут получения семафора", result.getError());
    }
}
