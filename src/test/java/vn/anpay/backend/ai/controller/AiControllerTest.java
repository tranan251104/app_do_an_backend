package vn.anpay.backend.ai.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.anpay.backend.ai.service.FinancialAiService;
import vn.anpay.backend.common.exception.BusinessException;
import vn.anpay.backend.common.exception.GlobalExceptionHandler;
import vn.anpay.backend.common.security.CurrentUser;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiControllerTest {
    private final FinancialAiService service = mock(FinancialAiService.class);
    private final UUID userId = UUID.randomUUID();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AiController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CurrentUser(userId), null, List.of()));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesActualCurrentUserPrincipal() throws Exception {
        when(service.chat(userId, "Số dư?")).thenReturn("100 VND");
        mvc.perform(post("/api/v1/ai/chat").contentType("application/json")
                        .content("{\"message\":\"Số dư?\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reply").value("100 VND"));
        verify(service).chat(userId, "Số dư?");
    }

    @Test
    void missingPrincipalIsUnauthorized() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(post("/api/v1/ai/chat").contentType("application/json").content("{\"message\":\"Hi\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsBlankAndOversizedMessages() throws Exception {
        for (String message : List.of(" ", "a".repeat(4001))) {
            mvc.perform(post("/api/v1/ai/chat").contentType("application/json")
                            .content("{\"message\":\"" + message + "\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        }
        verifyNoInteractions(service);
    }

    @Test
    void providerFailureUsesErrorEnvelope() throws Exception {
        when(service.chat(userId, "Hi")).thenThrow(new BusinessException(
                "AI_NOT_CONFIGURED", "Chưa cấu hình", HttpStatus.SERVICE_UNAVAILABLE));
        mvc.perform(post("/api/v1/ai/chat").contentType("application/json").content("{\"message\":\"Hi\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AI_NOT_CONFIGURED"));
    }
}
