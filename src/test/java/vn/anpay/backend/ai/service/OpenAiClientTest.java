package vn.anpay.backend.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;
import vn.anpay.backend.common.exception.BusinessException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OpenAiClientTest {
    private OpenAiClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        client = new OpenAiClient(new ObjectMapper(), "test-key", "gpt-4.1-mini");
        server = MockRestServiceServer.bindTo(
                (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate")).build();
    }

    @Test
    void sendsResponsesRequestAndReadsAllTextParts() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().json("""
                        {"model":"gpt-4.1-mini","instructions":"context","input":"question",
                         "store":false,"max_output_tokens":1024}
                        """))
                .andRespond(withSuccess("""
                        {"status":"completed","output":[
                          {"type":"reasoning","summary":[]},
                          {"type":"message","content":[
                            {"type":"output_text","text":"Số dư:"},
                            {"type":"output_text","text":"100 VND"}]}]}
                        """, MediaType.APPLICATION_JSON));
        assertThat(client.generateContent("context", "question")).isEqualTo("Số dư:\n100 VND");
        server.verify();
    }

    @Test
    void missingKeyDoesNotMakeHttpRequest() {
        var unconfigured = new OpenAiClient(new ObjectMapper(), " ", "gpt-4.1-mini");
        assertThatThrownBy(() -> unconfigured.generateContent("context", "question"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo("AI_NOT_CONFIGURED");
                    assertThat(e.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500})
    void upstreamErrorsAreNotSuccessfulReplies(int status) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status))
                .body("sensitive upstream error"));
        assertThatThrownBy(() -> client.generateContent("context", "question"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo(status == 429 ? "AI_RATE_LIMITED" : "AI_UPSTREAM_ERROR");
                    assertThat(e.getMessage()).doesNotContain("sensitive");
                });
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{}", "{\"status\":\"completed\",\"output\":[]}",
            "{\"status\":\"incomplete\",\"output\":[]}",
            "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"refusal\",\"refusal\":\"No\"}]}]}"})
    void invalidIncompleteOrRefusedResponseIsAnError(String response) {
        server.expect(anything()).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.generateContent("context", "question"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo("AI_INVALID_RESPONSE"));
        server.verify();
    }

    @Test
    void connectionFailureIsAnError() {
        server.expect(anything()).andRespond(withException(new IOException("connection timed out")));
        assertThatThrownBy(() -> client.generateContent("context", "question"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo("AI_CONNECTION_ERROR"));
        server.verify();
    }
}
