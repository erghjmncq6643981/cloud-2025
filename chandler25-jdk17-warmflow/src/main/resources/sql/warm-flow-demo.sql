-- 先执行 Warm-Flow 官方全量脚本，再执行当前脚本。
-- 官方脚本仓库: https://github.com/dromara/warm-flow/tree/master/sql

CREATE TABLE IF NOT EXISTS `wf_demo_leave_request` (
  `id` bigint NOT NULL COMMENT '业务主键',
  `applicant_id` varchar(64) NOT NULL COMMENT '申请人ID',
  `applicant_name` varchar(64) NOT NULL COMMENT '申请人姓名',
  `days` int NOT NULL COMMENT '请假天数',
  `reason` varchar(255) NOT NULL COMMENT '请假原因',
  `workflow_instance_id` bigint DEFAULT NULL COMMENT 'Warm-Flow流程实例ID',
  `status` varchar(32) NOT NULL COMMENT '业务状态',
  `current_task_name` varchar(128) DEFAULT NULL COMMENT '当前待办节点名称',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_wf_demo_leave_instance_id` (`workflow_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Warm-Flow请假演示业务表';
