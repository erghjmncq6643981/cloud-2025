package com.chandler.warm.flow.example.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Warm-Flow 请假单演示业务对象。
 */
@Data
@TableName("wf_demo_leave_request")
@Schema(name = "请假单", description = "Warm-Flow 学习示例里的业务单据")
public class LeaveRequest {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "业务主键")
    private Long id;

    @TableField("applicant_id")
    @Schema(description = "申请人ID")
    private String applicantId;

    @TableField("applicant_name")
    @Schema(description = "申请人姓名")
    private String applicantName;

    @TableField("days")
    @Schema(description = "请假天数")
    private Integer days;

    @TableField("reason")
    @Schema(description = "请假原因")
    private String reason;

    @TableField("workflow_instance_id")
    @Schema(description = "Warm-Flow 流程实例ID")
    private Long workflowInstanceId;

    @TableField("status")
    @Schema(description = "业务状态")
    private String status;

    @TableField("current_task_name")
    @Schema(description = "当前待办节点名称")
    private String currentTaskName;

    @TableField("created_at")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
