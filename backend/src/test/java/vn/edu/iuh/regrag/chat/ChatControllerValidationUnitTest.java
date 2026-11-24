package vn.edu.iuh.regrag.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import vn.edu.iuh.regrag.common.GlobalExceptionHandler;

class ChatControllerValidationUnitTest {

    private final ObjectMapper json = new ObjectMapper();
    private ChatService chatService;
    private MockMvc mvc;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        currentUserId = UUID.randomUUID();

        Jwt testJwt = Jwt.withTokenValue("mock.access.token")
                .header("alg", "none")
                .subject(currentUserId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("type", "access")
                .build();

        HandlerMethodArgumentResolver jwtArgumentResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().isAssignableFrom(Jwt.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return testJwt;
            }
        };

        ChatController controller = new ChatController(chatService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(jwtArgumentResolver)
                .build();
    }

    @Test
    void rejectsEmptyQuestionWithBadRequestAndFieldErrors() throws Exception {
        ChatController.ChatRequest request = new ChatController.ChatRequest("", null);

        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.errors[0].field").value("question"));
    }

    @Test
    void rejectsWhitespaceQuestionWithBadRequest() throws Exception {
        ChatController.ChatRequest request = new ChatController.ChatRequest("    \t\n   ", null);

        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("question"));
    }

    @Test
    void rejectsQuestionLongerThan2000Characters() throws Exception {
        String longQuestion = "Q".repeat(2001);
        ChatController.ChatRequest request = new ChatController.ChatRequest(longQuestion, null);

        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("question"));
    }

    @Test
    void processesValidRequestAndReturnsAnswer() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID userMsgId = UUID.randomUUID();
        UUID assistantMsgId = UUID.randomUUID();

        ChatResponse mockResponse = new ChatResponse(
                sessionId,
                userMsgId,
                assistantMsgId,
                "Sinh viên học theo tín chỉ được đăng ký tối đa 25 tín chỉ mỗi kỳ.",
                "answered",
                List.of()
        );

        when(chatService.answer(eq(currentUserId), eq(sessionId), eq("Số tín chỉ tối đa?")))
                .thenReturn(mockResponse);

        ChatController.ChatRequest request = new ChatController.ChatRequest("Số tín chỉ tối đa?", sessionId);

        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.userMessageId").value(userMsgId.toString()))
                .andExpect(jsonPath("$.assistantMessageId").value(assistantMsgId.toString()))
                .andExpect(jsonPath("$.answer").value("Sinh viên học theo tín chỉ được đăng ký tối đa 25 tín chỉ mỗi kỳ."))
                .andExpect(jsonPath("$.status").value("answered"));

        verify(chatService).answer(currentUserId, sessionId, "Số tín chỉ tối đa?");
    }
}
