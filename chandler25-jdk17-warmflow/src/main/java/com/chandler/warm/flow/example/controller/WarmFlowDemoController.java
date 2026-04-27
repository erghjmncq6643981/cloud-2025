package com.chandler.warm.flow.example.controller;

import com.chandler.warm.flow.example.service.WarmFlowDemoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Warm-Flow 学习演示接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/warm-flow/demo")
@Tag(name = "Warm-Flow学习Demo", description = "通过请假审批场景演示 Warm-Flow 的基础接入与流转")
public class WarmFlowDemoController {

    private final WarmFlowDemoService warmFlowDemoService;

    @Operation(summary = "初始化示例流程定义", description = "导入并发布内置的请假审批示例流程")
    @PostMapping("/definition/init")
    public WarmFlowDemoService.DefinitionView initDefinition() {
        return warmFlowDemoService.initializeDefinition();
    }

    @Operation(summary = "发起请假流程", description = "保存业务单据并启动 Warm-Flow 流程实例")
    @PostMapping("/leave/start")
    public WarmFlowDemoService.LeaveProcessView startLeaveProcess(
            @RequestBody WarmFlowDemoService.StartLeaveCommand command) {
        return warmFlowDemoService.startLeaveProcess(command);
    }

    @Operation(summary = "查询请假单与当前待办", description = "根据业务ID查看请假单状态和当前任务")
    @GetMapping("/leave/{leaveRequestId}")
    public WarmFlowDemoService.LeaveProcessView getLeaveProcess(@PathVariable Long leaveRequestId) {
        return warmFlowDemoService.getLeaveProcess(leaveRequestId);
    }

    @Operation(summary = "审批通过", description = "按 taskId 执行 PASS 流转")
    @PostMapping("/task/pass")
    public WarmFlowDemoService.LeaveProcessView passTask(
            @RequestBody WarmFlowDemoService.TaskActionCommand command) {
        return warmFlowDemoService.passTask(command);
    }

    @Operation(summary = "审批驳回", description = "按 taskId 执行 REJECT 流转")
    @PostMapping("/task/reject")
    public WarmFlowDemoService.LeaveProcessView rejectTask(
            @RequestBody WarmFlowDemoService.TaskActionCommand command) {
        return warmFlowDemoService.rejectTask(command);
    }
}
