package com.springai.controller;

import com.springai.governance.GovernedCalls;
import com.springai.service.MultiModelChatService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/multi-model/api")
public class MultiModalChatController {

    private final MultiModelChatService multiModelChatService;
    private final GovernedCalls governed;

    public MultiModalChatController(MultiModelChatService multiModelChatService, GovernedCalls governed) {
        this.multiModelChatService = multiModelChatService;
        this.governed = governed;
    }

    @GetMapping("/chat/ollama")
    public String chatWithOllama(@RequestParam("message") @NotBlank @Size(max = ApiLimits.MAX_TEXT) String message,
                                 Authentication authentication) {
        return governed.run(authentication, "multi-model.ollama", List.of(message),
                inputs -> multiModelChatService.chatWithOllama(inputs.get(0)));
    }
}
