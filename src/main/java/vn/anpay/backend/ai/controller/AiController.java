package vn.anpay.backend.ai.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.anpay.backend.ai.dto.AiChatRequest;
import vn.anpay.backend.ai.dto.AiChatResponse;
import vn.anpay.backend.ai.service.FinancialAiService;
import vn.anpay.backend.common.api.ApiResponse;
import vn.anpay.backend.common.exception.BusinessException;
import vn.anpay.backend.common.security.CurrentUser;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final FinancialAiService aiService;

    public AiController(FinancialAiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    public ApiResponse<AiChatResponse> chat(@AuthenticationPrincipal CurrentUser currentUser, @RequestBody AiChatRequest request) {
        if (currentUser == null) {
            throw new BusinessException("UNAUTHORIZED", "Bạn chưa đăng nhập.", HttpStatus.UNAUTHORIZED);
        }
        if (request == null || request.message == null || request.message.isBlank()) {
            throw new BusinessException("BAD_REQUEST", "Vui lòng nhập tin nhắn.", HttpStatus.BAD_REQUEST);
        }
        
        if (request.message.length() > 4000) {
            throw new BusinessException("BAD_REQUEST", "Tin nhắn không được quá 4000 ký tự.", HttpStatus.BAD_REQUEST);
        }
        String reply = aiService.chat(currentUser.userId(), request.message);
        
        return ApiResponse.ok(new AiChatResponse(reply));
    }
}
