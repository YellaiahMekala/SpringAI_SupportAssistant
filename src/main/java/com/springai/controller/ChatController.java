package com.springai.controller;

import com.springai.governance.GovernedCalls;
import com.springai.service.ChatService;
import com.springai.service.MessageRolesService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/** Chat and prompt-role demos. Every call is admitted and settled by {@link GovernedCalls}. */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final MessageRolesService messageRolesService;
    private final GovernedCalls governed;

    public ChatController(ChatService chatService, MessageRolesService messageRolesService, GovernedCalls governed) {
        this.chatService = chatService;
        this.messageRolesService = messageRolesService;
        this.governed = governed;
    }

    @GetMapping("/message")
    public String chat(@RequestParam @NotBlank @Size(max = ApiLimits.MAX_TEXT) String message,
                       Authentication authentication) {
        return governed.run(authentication, "chat.message", List.of(message),
                inputs -> chatService.chat(inputs.get(0)));
    }

    @GetMapping(value = "/message/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam @NotBlank @Size(max = ApiLimits.MAX_TEXT) String message,
                                   Authentication authentication) {
        return governed.stream(authentication, "chat.stream", List.of(message),
                inputs -> chatService.stream(inputs.get(0)));
    }

    @GetMapping("/check")
    public String checkInsurancePolicy(@RequestParam @NotBlank @Size(max = ApiLimits.MAX_TEXT) String message,
                                       Authentication authentication) {
        return governed.run(authentication, "chat.check", List.of(message),
                inputs -> messageRolesService.checkPolicy(inputs.get(0)));
    }

    @GetMapping("/check/policy")
    public String checkPolicyWithRoles(@RequestParam @NotBlank @Size(max = ApiLimits.MAX_TEXT) String message,
                                       Authentication authentication) {
        return governed.run(authentication, "chat.check.policy", List.of(message),
                inputs -> messageRolesService.checkInsurancePolicy(inputs.get(0)));
    }

    @GetMapping("/guide")
    public String guide(@RequestParam @NotBlank @Size(max = ApiLimits.MAX_SHORT) String topic,
                        @RequestParam @NotBlank @Size(max = ApiLimits.MAX_SHORT) String level,
                        @RequestParam @Min(1) @Max(20) int points,
                        Authentication authentication) {
        return governed.run(authentication, "chat.guide", List.of(topic, level),
                inputs -> messageRolesService.guideMe(inputs.get(0), inputs.get(1), points));
    }
}
