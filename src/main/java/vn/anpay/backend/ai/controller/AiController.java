package vn.anpay.backend.ai.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.anpay.backend.ai.dto.AiChatRequest;
import vn.anpay.backend.ai.dto.AiChatResponse;
import vn.anpay.backend.ai.service.FinancialAiService;
import vn.anpay.backend.common.api.ApiError;
import vn.anpay.backend.common.api.ApiResponse;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final FinancialAiService aiService;

    public AiController(FinancialAiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    public ApiResponse<AiChatResponse> chat(@AuthenticationPrincipal Jwt jwt, @RequestBody AiChatRequest request) {
        if (jwt == null) {
            return new ApiResponse<>(false, null, new ApiError("UNAUTHORIZED", "Bạn chưa đăng nhập.", null, null), Instant.now());
        }
        if (request == null || request.message == null || request.message.isBlank()) {
            return new ApiResponse<>(false, null, new ApiError("BAD_REQUEST", "Vui lòng nhập tin nhắn.", null, null), Instant.now());
        }
        
        UUID userId = UUID.fromString(jwt.getSubject());
        String reply = aiService.chat(userId, request.message);
        
        return ApiResponse.ok(new AiChatResponse(reply));
    }
}
