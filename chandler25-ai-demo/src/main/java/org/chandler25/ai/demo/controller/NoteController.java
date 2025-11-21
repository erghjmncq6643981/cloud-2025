package org.chandler25.ai.demo.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/21 14:37
 * @version 1.0.0
 * @since 21
 */
@RestController
@RequestMapping("/note")
@RequiredArgsConstructor
@Tag(name = "笔记管理", description = "笔记管理")
public class NoteController {
}