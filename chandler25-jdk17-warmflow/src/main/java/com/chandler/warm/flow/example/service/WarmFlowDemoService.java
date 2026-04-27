package com.chandler.warm.flow.example.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.warm.flow.example.domain.dataobject.LeaveRequest;
import com.chandler.warm.flow.example.domain.mapper.LeaveRequestMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.warm.flow.core.dto.FlowParams;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.service.DefService;
import org.dromara.warm.flow.core.service.InsService;
import org.dromara.warm.flow.core.service.TaskService;
import org.dromara.warm.flow.orm.entity.FlowTask;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Warm-Flow 学习示例服务。
 */
@Service
@RequiredArgsConstructor
public class WarmFlowDemoService {

    private static final String FLOW_CODE = "leave-demo";
    private static final String FLOW_DEFINITION_PATH = "flow/leave-approval-demo.json";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_IN_APPROVAL = "IN_APPROVAL";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_FINISHED = "FINISHED";

    private final DefService defService;
    private final InsService insService;
    private final TaskService taskService;
    private final LeaveRequestMapper leaveRequestMapper;

    @Transactional(rollbackFor = Exception.class)
    public DefinitionView initializeDefinition() {
        Definition publishedDefinition = defService.getPublishByFlowCode(FLOW_CODE);
        if (publishedDefinition != null) {
            return new DefinitionView(
                    publishedDefinition.getId(),
                    FLOW_CODE,
                    true,
                    "请假审批演示流程已存在，直接复用已发布版本");
        }

        String definitionJson = readDefinitionJson();
        Definition definition = defService.importJson(definitionJson);
        defService.publish(definition.getId());
        return new DefinitionView(
                definition.getId(),
                FLOW_CODE,
                true,
                "请假审批演示流程已导入并发布");
    }

    @Transactional(rollbackFor = Exception.class)
    public LeaveProcessView startLeaveProcess(StartLeaveCommand command) {
        validateStartCommand(command);
        ensurePublishedDefinition();

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setApplicantId(command.applicantId());
        leaveRequest.setApplicantName(command.applicantName());
        leaveRequest.setDays(command.days());
        leaveRequest.setReason(command.reason());
        leaveRequest.setStatus(STATUS_DRAFT);
        leaveRequest.setCreatedAt(LocalDateTime.now());
        leaveRequest.setUpdatedAt(LocalDateTime.now());
        leaveRequestMapper.insert(leaveRequest);

        FlowParams flowParams = FlowParams.build()
                .flowCode(FLOW_CODE)
                .handler(command.applicantId())
                .variable(buildStartVariables(command));
        Instance instance = insService.start(String.valueOf(leaveRequest.getId()), flowParams);

        leaveRequest.setWorkflowInstanceId(instance.getId());
        syncBusinessState(leaveRequest, null);
        leaveRequestMapper.updateById(leaveRequest);
        return buildLeaveProcessView(leaveRequest);
    }

    public LeaveProcessView getLeaveProcess(Long leaveRequestId) {
        LeaveRequest leaveRequest = getLeaveRequestById(leaveRequestId);
        return buildLeaveProcessView(leaveRequest);
    }

    public Long getOrInitDemoDefinitionId() {
        return ensurePublishedDefinition().getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public LeaveProcessView passTask(TaskActionCommand command) {
        return handleTask(command, SkipType.PASS, STATUS_APPROVED);
    }

    @Transactional(rollbackFor = Exception.class)
    public LeaveProcessView rejectTask(TaskActionCommand command) {
        return handleTask(command, SkipType.REJECT, STATUS_REJECTED);
    }

    private LeaveProcessView handleTask(TaskActionCommand command, SkipType skipType, String finalStatus) {
        validateTaskActionCommand(command);
        Task task = taskService.getById(command.taskId());
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "待办任务不存在，可能已经被处理");
        }

        LeaveRequest leaveRequest = getLeaveRequestByInstanceId(task.getInstanceId());
        FlowParams flowParams = FlowParams.build()
                .skipType(skipType.getKey())
                .handler(command.handler())
                .message(command.comment());
        if (!CollectionUtils.isEmpty(command.variables())) {
            flowParams.variable(command.variables());
        }

        taskService.skip(command.taskId(), flowParams);
        syncBusinessState(leaveRequest, finalStatus);
        leaveRequestMapper.updateById(leaveRequest);
        return buildLeaveProcessView(leaveRequest);
    }

    private void syncBusinessState(LeaveRequest leaveRequest, String finalStatusWhenFinished) {
        List<ActiveTaskView> activeTasks = listActiveTasks(leaveRequest.getWorkflowInstanceId());
        leaveRequest.setUpdatedAt(LocalDateTime.now());

        if (activeTasks.isEmpty()) {
            leaveRequest.setCurrentTaskName(null);
            leaveRequest.setStatus(StringUtils.hasText(finalStatusWhenFinished) ? finalStatusWhenFinished : STATUS_FINISHED);
            return;
        }

        leaveRequest.setStatus(STATUS_IN_APPROVAL);
        leaveRequest.setCurrentTaskName(activeTasks.stream()
                .map(ActiveTaskView::nodeName)
                .collect(Collectors.joining(", ")));
    }

    private LeaveProcessView buildLeaveProcessView(LeaveRequest leaveRequest) {
        List<ActiveTaskView> activeTasks = listActiveTasks(leaveRequest.getWorkflowInstanceId());
        return new LeaveProcessView(leaveRequest, FLOW_CODE, activeTasks);
    }

    private List<ActiveTaskView> listActiveTasks(Long instanceId) {
        if (instanceId == null) {
            return Collections.emptyList();
        }

        List<Task> tasks = taskService.list(new FlowTask().setInstanceId(instanceId));
        return tasks.stream()
                .map(task -> new ActiveTaskView(
                        task.getId(),
                        task.getNodeCode(),
                        task.getNodeName(),
                        task.getInstanceId()))
                .toList();
    }

    private Map<String, Object> buildStartVariables(StartLeaveCommand command) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("applicantId", command.applicantId());
        variables.put("applicantName", command.applicantName());
        variables.put("days", command.days());
        variables.put("reason", command.reason());
        if (!CollectionUtils.isEmpty(command.variables())) {
            variables.putAll(command.variables());
        }
        return variables;
    }

    private Definition ensurePublishedDefinition() {
        Definition definition = defService.getPublishByFlowCode(FLOW_CODE);
        if (definition != null) {
            return definition;
        }

        initializeDefinition();
        return defService.getPublishByFlowCode(FLOW_CODE);
    }

    private LeaveRequest getLeaveRequestById(Long leaveRequestId) {
        LeaveRequest leaveRequest = leaveRequestMapper.selectById(leaveRequestId);
        if (leaveRequest == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "请假单不存在");
        }
        return leaveRequest;
    }

    private LeaveRequest getLeaveRequestByInstanceId(Long instanceId) {
        LeaveRequest leaveRequest = leaveRequestMapper.selectOne(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getWorkflowInstanceId, instanceId));
        if (leaveRequest == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到与流程实例关联的请假单");
        }
        return leaveRequest;
    }

    private void validateStartCommand(StartLeaveCommand command) {
        requireText(command.applicantId(), "申请人ID不能为空");
        requireText(command.applicantName(), "申请人姓名不能为空");
        requireText(command.reason(), "请假原因不能为空");
        if (command.days() == null || command.days() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请假天数必须大于0");
        }
    }

    private void validateTaskActionCommand(TaskActionCommand command) {
        if (command.taskId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taskId 不能为空");
        }
        requireText(command.handler(), "办理人handler不能为空");
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private String readDefinitionJson() {
        ClassPathResource resource = new ClassPathResource(FLOW_DEFINITION_PATH);
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("读取 Warm-Flow 示例流程定义失败: " + FLOW_DEFINITION_PATH, exception);
        }
    }

    public record DefinitionView(Long definitionId, String flowCode, boolean published, String message) {
    }

    public record StartLeaveCommand(
            String applicantId,
            String applicantName,
            Integer days,
            String reason,
            Map<String, Object> variables) {
    }

    public record TaskActionCommand(
            Long taskId,
            String handler,
            String comment,
            Map<String, Object> variables) {
    }

    public record ActiveTaskView(
            Long taskId,
            String nodeCode,
            String nodeName,
            Long instanceId) {
    }

    public record LeaveProcessView(
            LeaveRequest leaveRequest,
            String flowCode,
            List<ActiveTaskView> activeTasks) {
    }
}
