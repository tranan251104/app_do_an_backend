package vn.anpay.backend.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class GeminiClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;
    private final String geminiUrl;
    private final String geminiApiKey;

    public GeminiClient(
            ObjectMapper mapper,
            @Value("${app.ai.gemini.url}") String geminiUrl,
            @Value("${app.ai.gemini.api-key}") String geminiApiKey
    ) {
        this.restTemplate = new RestTemplate();
        this.mapper = mapper;
        this.geminiUrl = geminiUrl;
        this.geminiApiKey = geminiApiKey;
    }

    public String generateContent(String systemInstruction, String userMessage) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            return "Tính năng AI chưa được cấu hình. Vui lòng thiết lập GEMINI_API_KEY.";
        }

        try {
            ObjectNode root = mapper.createObjectNode();

            // Build system_instruction
            ObjectNode sysInst = root.putObject("system_instruction");
            ArrayNode sysParts = sysInst.putArray("parts");
            sysParts.addObject().put("text", systemInstruction);

            // Build contents
            ArrayNode contents = root.putArray("contents");
            ObjectNode userContent = contents.addObject();
            userContent.put("role", "user");
            ArrayNode userParts = userContent.putArray("parts");
            userParts.addObject().put("text", userMessage);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(mapper.writeValueAsString(root), headers);

            String url = geminiUrl + "?key=" + geminiApiKey;
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode responseNode = mapper.readTree(response.getBody());
                JsonNode candidates = responseNode.path("candidates");
                if (candidates.isArray() && !candidates.isEmpty()) {
                    JsonNode parts = candidates.get(0).path("content").path("parts");
                    if (parts.isArray() && !parts.isEmpty()) {
                        return parts.get(0).path("text").asText();
                    }
                }
            }
            return "Xin lỗi, tôi không thể trả lời lúc này do lỗi hệ thống phân tích AI.";

        } catch (Exception e) {
            log.error("Error calling Gemini API: {}", e.getMessage());
            return "Xin lỗi, đã xảy ra lỗi kết nối với máy chủ AI.";
        }
    }
}
