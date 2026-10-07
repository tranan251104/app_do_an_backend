package vn.anpay.backend.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import tools.jackson.databind.ObjectMapper;
import vn.anpay.backend.common.exception.BusinessException;

@Service
public class OpenAiClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);
    private static final String URL = "https://api.openai.com/v1/responses";
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;

    public OpenAiClient(ObjectMapper mapper,
                        @Value("${app.ai.openai.api-key}") String apiKey,
                        @Value("${app.ai.openai.model}") String model) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(15000);
        this.restTemplate = new RestTemplate(factory);
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    public String generateContent(String systemInstruction, String userMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException("AI_NOT_CONFIGURED", "Tính năng AI chưa được cấu hình.", HttpStatus.SERVICE_UNAVAILABLE);
        }
        try {
            var body = mapper.createObjectNode();
            body.put("model", model);
            body.put("instructions", systemInstruction);
            body.put("input", userMessage);
            body.put("store", false);
            body.put("max_output_tokens", 1024);
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            var response = restTemplate.postForEntity(URL,
                    new HttpEntity<>(mapper.writeValueAsString(body), headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw invalidResponse();
            }
            var root = mapper.readTree(response.getBody());
            if (!"completed".equals(root.path("status").asText())) {
                throw invalidResponse();
            }
            var reply = new StringBuilder();
            for (var item : root.path("output")) {
                if (!"message".equals(item.path("type").asText())) continue;
                for (var part : item.path("content")) {
                    if ("output_text".equals(part.path("type").asText())) {
                        if (!reply.isEmpty()) reply.append('\n');
                        reply.append(part.path("text").asText());
                    }
                }
            }
            if (reply.toString().isBlank()) throw invalidResponse();
            return reply.toString();
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientResponseException e) {
            // Do not log response bodies, prompts, financial data or authorization headers.
            log.warn("OpenAI request failed status={}", e.getStatusCode().value());
            if (e.getStatusCode().value() == 429) {
                throw new BusinessException("AI_RATE_LIMITED", "AI đang quá tải hoặc hết hạn mức. Vui lòng thử lại sau.", HttpStatus.SERVICE_UNAVAILABLE);
            }
            throw new BusinessException("AI_UPSTREAM_ERROR", "Dịch vụ AI tạm thời không khả dụng.", HttpStatus.BAD_GATEWAY);
        } catch (ResourceAccessException e) {
            throw new BusinessException("AI_CONNECTION_ERROR", "Không thể kết nối dịch vụ AI. Vui lòng thử lại sau.", HttpStatus.SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            throw invalidResponse();
        }
    }

    private BusinessException invalidResponse() {
        return new BusinessException("AI_INVALID_RESPONSE", "AI chưa trả về câu trả lời hoàn chỉnh. Vui lòng thử lại.", HttpStatus.BAD_GATEWAY);
    }
}
