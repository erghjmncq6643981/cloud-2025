# Warm-Flow Demo

这个仓库现在带了一套最小可学习的 `Warm-Flow` 请假审批示例，目的是让你按下面顺序快速串起来：

1. 导入工作流表和 demo 业务表。
2. 调用接口初始化流程定义。
3. 发起请假流程。
4. 用返回的 `taskId` 继续做通过或驳回。
5. 通过查询接口观察业务状态和当前待办的变化。

另外已经接入了官方页面设计器插件 `warm-flow-plugin-ui-sb-web`，你可以直接在浏览器里设计流程。

## 需要准备的表

先导入 Warm-Flow 官方全量脚本。

- 官方仓库: `https://github.com/dromara/warm-flow/tree/master/sql`
- 常用脚本名: `warm-flow-all.sql`

再执行本项目里的 demo 业务表脚本：

- [`src/main/resources/sql/warm-flow-demo.sql`](/Users/chandler/Documents/repository/github/cloud-2025/chandler25-jdk17-warmflow/src/main/resources/sql/warm-flow-demo.sql)

## 关键文件

- 流程定义 JSON: [`src/main/resources/flow/leave-approval-demo.json`](/Users/chandler/Documents/repository/github/cloud-2025/chandler25-jdk17-warmflow/src/main/resources/flow/leave-approval-demo.json)
- 示例接口: [`src/main/java/com/chandler/warm/flow/example/controller/WarmFlowDemoController.java`](/Users/chandler/Documents/repository/github/cloud-2025/chandler25-jdk17-warmflow/src/main/java/com/chandler/warm/flow/example/controller/WarmFlowDemoController.java)
- 核心服务: [`src/main/java/com/chandler/warm/flow/example/service/WarmFlowDemoService.java`](/Users/chandler/Documents/repository/github/cloud-2025/chandler25-jdk17-warmflow/src/main/java/com/chandler/warm/flow/example/service/WarmFlowDemoService.java)
- 业务表实体: [`src/main/java/com/chandler/warm/flow/example/domain/dataobject/LeaveRequest.java`](/Users/chandler/Documents/repository/github/cloud-2025/chandler25-jdk17-warmflow/src/main/java/com/chandler/warm/flow/example/domain/dataobject/LeaveRequest.java)
- 调用样例: [`warm-flow-demo.http`](/Users/chandler/Documents/repository/github/cloud-2025/chandler25-jdk17-warmflow/warm-flow-demo.http)

## Demo 流程

内置的是一个串行审批：

`发起请假 -> 直属主管审批 -> HR审批 -> 结束`

主管和 HR 节点都支持：

- `PASS` 进入下一步
- `REJECT` 直接结束流程

## 访问入口

- Swagger: `http://localhost:17680/swagger-ui.html`
- Warm-Flow UI: `http://localhost:17680/warm-flow-ui/index.html`
- 新建流程设计: `http://localhost:17680/designer/warm-flow/new`
- 仅显示设计画布: `http://localhost:17680/designer/warm-flow/design-only`
- 打开内置 demo 流程: `http://localhost:17680/designer/warm-flow/demo`

## 页面设计器说明

这个项目已经按官方文档引入了 `warm-flow-plugin-ui-sb-web`，对应依赖在 [pom.xml](/Users/chandler/Documents/repository/github/cloud-2025/chandler25-jdk17-warmflow/pom.xml)。

当前项目没有接 Spring Security、Sa-Token 或 Shiro，所以不需要额外放行 `/warm-flow-ui/**` 和 `/warm-flow/**`。

如果你后面再把认证框架加回来，记得把这两个路径加入白名单。官方文档见：

- [设计器集成](https://www.warm-flow.com/master/primary/designerIntroduced.html)
- [配置 yml 和定义 json](https://www.warm-flow.com/master/primary/config.html)

使用建议：

- 想从零开始画流程，访问 `/designer/warm-flow/new`
- 想直接看当前项目内置的请假流程长什么样，访问 `/designer/warm-flow/demo`
- 想只看设计画布，不看基础信息 tab，访问 `/designer/warm-flow/design-only`

## 学习建议

建议先看 [`src/main/resources/flow/leave-approval-demo.json`](/Users/chandler/Documents/repository/github/cloud-2025/chandler25-jdk17-warmflow/src/main/resources/flow/leave-approval-demo.json) 里的 `nodeList` 和 `skipList`，再看 [`src/main/java/com/chandler/warm/flow/example/service/WarmFlowDemoService.java`](/Users/chandler/Documents/repository/github/cloud-2025/chandler25-jdk17-warmflow/src/main/java/com/chandler/warm/flow/example/service/WarmFlowDemoService.java) 里这三段逻辑：

- `initializeDefinition`: 流程定义导入和发布
- `startLeaveProcess`: 业务单据落库并启动流程
- `handleTask`: 用 `taskService.skip` 执行通过或驳回
