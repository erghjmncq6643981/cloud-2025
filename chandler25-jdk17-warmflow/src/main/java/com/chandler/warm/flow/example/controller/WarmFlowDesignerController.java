package com.chandler.warm.flow.example.controller;

import com.chandler.warm.flow.example.service.WarmFlowDemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Warm-Flow 设计器便捷入口。
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/designer/warm-flow")
public class WarmFlowDesignerController {

    private final WarmFlowDemoService warmFlowDemoService;

    @GetMapping
    public String designerHome() {
        return "redirect:/warm-flow-ui/index.html?onlyDesignShow=false";
    }

    @GetMapping("/new")
    public String createNewDefinition() {
        return "redirect:/warm-flow-ui/index.html?onlyDesignShow=false";
    }

    @GetMapping("/design-only")
    public String designOnly() {
        return "redirect:/warm-flow-ui/index.html?onlyDesignShow=true";
    }

    @GetMapping("/demo")
    public String demoDefinition() {
        Long definitionId = warmFlowDemoService.getOrInitDemoDefinitionId();
        return "redirect:/warm-flow-ui/index.html?id=" + definitionId + "&onlyDesignShow=false";
    }
}
