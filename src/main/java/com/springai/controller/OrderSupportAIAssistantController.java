package com.springai.controller;

import com.springai.governance.GovernedCalls;
import com.springai.service.OrderSupportAISupportAssistantService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class OrderSupportAIAssistantController {

    private final OrderSupportAISupportAssistantService aiAssistantService;
    private final GovernedCalls governed;

    public OrderSupportAIAssistantController(OrderSupportAISupportAssistantService aiAssistantService,
                                             GovernedCalls governed) {
        this.aiAssistantService = aiAssistantService;
        this.governed = governed;
    }

    /** Prompt-template order support with the advisor chain (token budget, usage audit). */
    @GetMapping("/order-support")
    public String talkToOrderAISupport(@RequestParam @NotBlank @Size(max = ApiLimits.MAX_SHORT) String customerName,
                                       @RequestParam @NotBlank @Size(max = 32) String orderId,
                                       @RequestParam @NotBlank @Size(max = ApiLimits.MAX_TEXT) String customerMessage,
                                       Authentication authentication) {
        return governed.run(authentication, "order-support", List.of(customerName, orderId, customerMessage),
                inputs -> aiAssistantService.talkToAISupport(inputs.get(0), inputs.get(1), inputs.get(2)));
    }

    /** The same prompt without advisors, for comparison. Still governed at admission. */
    @GetMapping("/order-support/baseline")
    public String orderSupportBaseline(@RequestParam @NotBlank @Size(max = ApiLimits.MAX_SHORT) String customerName,
                                       @RequestParam @NotBlank @Size(max = 32) String orderId,
                                       @RequestParam @NotBlank @Size(max = ApiLimits.MAX_TEXT) String customerMessage,
                                       Authentication authentication) {
        return governed.run(authentication, "order-support.baseline", List.of(customerName, orderId, customerMessage),
                inputs -> aiAssistantService.assistWithOrderSupport(inputs.get(0), inputs.get(1), inputs.get(2)));
    }
}
