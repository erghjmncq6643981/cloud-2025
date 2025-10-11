package org.chandler25.elasticsearch.dto.req;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

@Data
@ApiModel("订单搜索")
public class OrderQueryReq {

	/**=======orderDocument  start */
    @ApiModelProperty(value = "下单人",example = "埃贝赫")
    private String orderDocumentCreatedByName;
	@ApiModelProperty(value = "订单付款类型",example = "TYPE_COMMON_ORDER")
	private String orderPayTypeId;
    /**=======orderDocument  end */
	
	/**=======RELEASE_ORDER  start*/
    @ApiModelProperty(value = "放箱状态",example = "")
    private String releaseOrderStatusId;
    
    @ApiModelProperty(value = "放箱公司",example = "")
    private Long releaseCompanyId;
    
    
	/**=======RELEASE_ORDER  end */
	
	/**=======truckOrder  start */
    @ApiModelProperty("录单人")
   	private String orderCreateBy;
    
    @ApiModelProperty(value = "订单id",example = "7689736")
    private String orderId;
    
    @ApiModelProperty(value = "提单号",example = "OOLU2635775900")
    private String blNo;
    @ApiModelProperty(value = "进出口",allowableValues = "IB,OB,EP",example = "OB")
    private String orderTypeCode;
    @ApiModelProperty(value = "是否有开港时间",example = "true")
    private Boolean isOpeningTime;
    @ApiModelProperty(value = "是否有截港时间",example = "true")
    private Boolean isClosingTime;
    @ApiModelProperty(value = "客服",example = "")
    private String customerService;
    @ApiModelProperty(value = "委托时间from",example = "2020-02-25")
    private String commissionTimeFrom;
    @ApiModelProperty(value = "委托时间to",example = "2020-02-26")
    private String commissionTimeTo;
    
    @ApiModelProperty(value = "船期时间from",example = "2020-02-25")
    private String etdFrom;
    @ApiModelProperty(value = "船期时间to",example = "2020-02-26")
    private String etdTo;
    @ApiModelProperty(value = "客户",example = "44328")
    private Long buyerId;
    @ApiModelProperty(value = "旺季客户标签",example = "HIGH_PROFIT_CUSTOMERS")
    private String customerQualityType;
    
    @ApiModelProperty(value = "港口",example = "SHANGHAI")
    private String portId;
    @ApiModelProperty(value = "等通知放设备单",example = "false")
    private Boolean isCustomerNotifyApplyEir;
    @ApiModelProperty(value = "开港时间from",example = "2020-02-29")
    private String openingTimeFrom;
    @ApiModelProperty(value = "开港时间to",example = "2020-03-02")
    private String openingTimeTo;
    @ApiModelProperty(value = "截港时间from",example = "2020-02-29")
    private String closingTimeFrom;
    @ApiModelProperty(value = "截港时间to",example = "2020-03-02")
    private String closingTimeTo;
    @ApiModelProperty(value = "新增预警时间from")
    private String cutoffCustomsTimeWarningFrom;
    @ApiModelProperty(value = "新增预警时间to")
    private String cutoffCustomsTimeWarningTo;

    @ApiModelProperty(value = "港区",example = "外高桥二期")
    private String portDistrict;
    @ApiModelProperty(value = "订单号",example = "200204531-1")
    private String orderNo;
    @ApiModelProperty(value = "业务编号",example = "ABHZX2000092")
    private String businessNo;
    @ApiModelProperty(value = "重点跟单",example = "0")
    private Integer isImportantContainer;
    @ApiModelProperty(value = "销售",example = "高月")
    private String salesmanfirst;
    @ApiModelProperty(value = "销售id")
    private String salesId;
    @ApiModelProperty(value = "要求落箱",example = "false")
    private Boolean isDropContainer;
    @ApiModelProperty(value = "预订箱号",example = "false")
    private Boolean isContainerNoBooked;
    @ApiModelProperty(value = "要求回单",example = "false")
    private Boolean requireReceipt;
    @ApiModelProperty(value="客服组  （多个请半角英文逗号分隔）",example = "1,2,3")
    private String customerServiceGroupId;

    @ApiModelProperty("运输方式")
    private String transportType;
    @ApiModelProperty("中转点")
    private String transitPoint;
    @ApiModelProperty(value = "船公司",example = "东方海外")
    private String shippingCompany;
    
    @ApiModelProperty(value = "是否有船公司",example = "true")
    private Boolean hasShippingCompany;

    @ApiModelProperty("货代订单ID")
    private String forwarderOrderId;
    @ApiModelProperty("货代箱编号")
    private String forwarderContainerId;
    @ApiModelProperty("船名")
    private String vessel;
    @ApiModelProperty("允许打单")
    private String allowPrint;

    @ApiModelProperty(value = "是否是包车业务",example = "true")
    private Boolean isChartered;
    
    /**=======truckOrder  end */
    @ApiModelProperty(value = "设备单类型",example = "PAPERLESS")
    private String deviceSingleType;

    @ApiModelProperty(value = "是否无纸化",example = "true")
    private Boolean isDeviceTypePaperless;
   
    /**========truckOrderContainer  start*/
    @ApiModelProperty(value = "箱号",example = "PSXU2015255")
    private String containerNo;

    @ApiModelProperty(value = "箱型",example = "20GP")
    private String containerSizeType;
    @ApiModelProperty(value = "订单状态",example = "[CONTAINER_DISPATCHED]")
    private List<String> statuses;
    @ApiModelProperty(value = "预进港",example = "false")
    private String isEarlierGateIn;
    @ApiModelProperty(value = "暂落箱堆场",example = "")
    private String dropDepotName;
    @ApiModelProperty("暂落箱堆场")
    private Long dropDepotId;
    @ApiModelProperty(value = "堆场区域",example = "奥吉三堆场")
    private String depotArea;
    @ApiModelProperty(value = "装拆箱点",example = "江苏省泰州市靖江市西北环路91号（西效公园旁边）")
    private String doorAddress;
    @ApiModelProperty(value = "做箱时间from",example = "2020-02-29")
	private String planArriveTimeFrom;
    @ApiModelProperty(value = "做箱时间to",example = "2020-03-01")
	private String planArriveTimeTo;
    @ApiModelProperty(value = "计划到厂时间from",example = "2020-02-29")
    private String driverPlanArrivalTimeFrom;
    @ApiModelProperty(value = "计划到厂时间to",example = "2020-03-01")
    private String driverPlanArrivalTimeTo;
    @ApiModelProperty(value = "车牌号",example = "沪EA8333")
    private String truckPlateNumber;
    @ApiModelProperty(value = "车牌号集合",example = "沪EA8333,沪EA8333")
    private String truckPlateNumbers;
    @ApiModelProperty(value = "司机名",example = "任兰宝")
    private String driverName;
    @ApiModelProperty(value = "接单人",example = "任兰宝")
    private String orderTaker;
//    @ApiModelProperty("司机手机号")
//    private String driverPhone;
    
    
    @ApiModelProperty(value = "客服确认做箱",example = "true")
    private Boolean hasCustomerConfirmedPlan;
    

    @ApiModelProperty("是否车辆备案")
    private Boolean driverRecord;
    /**========truckOrderContainer  end*/
	

    /**======== cso  start */
    @ApiModelProperty(value = "客户提供箱封号",example = "false")
    private Boolean isCustomerContainerNumber;
    @ApiModelProperty(value = "进港状态",example = "false")
    private String gateIn;
    
    @ApiModelProperty(value = "是否压夜",example = "false")
    private Boolean isPressNight;
    /**======== cso  end */

    /**======== user  driver start */

    @ApiModelProperty(value = "司机类型vip",example = "true")
    private Boolean isVip;
   
    @ApiModelProperty(value = "司机属性",example = "0")
    private Integer driverVariety;
    
    /**======== user  driver end  */

    /** container_requirement start*/
    
    @ApiModelProperty(value = "是否冷箱",example = "true")
    private Boolean isFreeze;
    @ApiModelProperty(value = "是否需要核酸检测报告",example = "true")
    private Boolean needNucleicTest;

    @ApiModelProperty(value = "是否白卡",example = "true")
    private Boolean isCustomsRecord;

    @ApiModelProperty(value = "是否需要带货",example = "true")
    private Boolean isIncidentally;

    @ApiModelProperty(value = "是否新门点做箱")
    private Boolean newDoorPoint;

    @ApiModelProperty(value = "是否带货价")
    private Integer incidentallyRequire;

    @ApiModelProperty(value = "拼箱要求 双拼 0 null 单放 1 双拼")
    private Integer lclRequire;

    /** container_requirement end */

    @ApiModelProperty(value = "排序字段",example = "planArrivalTime")
    private String sortItem;
    @ApiModelProperty(value = "排序方式",allowableValues = "ASC,DESC",example = "DESC")
    private String sort;
    @ApiModelProperty(value = "当前页数",example = "1")
    @Min(1) @Max(1000)
    private int page = 1;
    @ApiModelProperty(value = "每页的行数",example = "25")
    @Min(1) @Max(2000)
    private int pageSize = 25;
    @ApiModelProperty(value = "每页的行数",example = "25")
    @Min(1) @Max(2000)
    private int rows = 25;

    @ApiModelProperty(value = "eir过期时间from",example = "2020-02-25 00:00:00")
    private String validEndTimeFrom;
    @ApiModelProperty(value = "eir过期时间to",example = "2020-02-25 14:00:00")
    private String validEndTimeTo;
    
    @ApiModelProperty(value = "司机id",example = "1")
    private String driverId;
    
    @ApiModelProperty(value = "预约进港状态1成功2失败3无法预约",example = "1")
    private String apptGateInStatus; 
    
    @ApiModelProperty("派车类型")
    private String dispatchType;
    
    @ApiModelProperty(value = "车辆类型  桥数")
    private String truckType;
    
    @ApiModelProperty(value = "退关需还箱",example = "true")
    private Boolean  terminationNeedReturnBack;
    
    @ApiModelProperty(value = "退关已还箱",example = "true")
    private Boolean  isTerminationReturnBack;
    
    @ApiModelProperty(value = "箱号审核状态: 已审核（true）/ 未审核（false）",example = "true")
    private Boolean containerNumberConfirmed; 
    
    @ApiModelProperty(value = "到厂时间from",example = "2020-02-29")
  	private String doorArrivalTimeFrom;
      @ApiModelProperty(value = "到厂时间to",example = "2020-03-01")
  	private String doorArrivalTimeTo;
    
    @ApiModelProperty(value = "截单时间from",example = "2020-02-25")
    private String documentaryOffTimeFrom;
    @ApiModelProperty(value = "截单时间to",example = "2020-02-26")
    private String documentaryOffTimeTo;
    
    @ApiModelProperty(value = "最晚进港时间from",example = "2020-02-25")
    private String requiredGateInTimeFrom;
    @ApiModelProperty(value = "最晚进港时间to",example = "2020-02-25")
    private String requiredGateInTimeTo;
    
    @ApiModelProperty(value = "是否已落箱",example = "true")
    private Boolean hasDropContainer;
    
    @ApiModelProperty(value = "是否已提箱",example = "true")
    private Boolean hasPickup;
    
    @ApiModelProperty(value = "是否已到厂",example = "true")
    private Boolean hasDoorArrived;
    
    @ApiModelProperty(value = "是否已离厂",example = "true")
    private Boolean hasDoorLeft;
    
    @ApiModelProperty(value = "是否需要过磅",example = "false")
    private Boolean needContainerWeighing;
    
    @ApiModelProperty(value = "是否反馈",example = "true")
    private Boolean feedback;
    
    @ApiModelProperty(value = "是否已过磅反馈",example = "true")
    private Boolean weighingFeedback;
    
    @ApiModelProperty(value = "落箱确认",example = "true")
    private Boolean dropContainerConfirm;
    
    @ApiModelProperty(value = "司机反馈落箱点ID",example = "0")
    private Long dropContainerPointId;
    
    @ApiModelProperty(value = "是否有司机反馈落箱点ID",example = "0")
    private Boolean hasDropContainerPointId;
    
    
    @ApiModelProperty(value = "司机反馈落箱点ID是否与要求一致",example = "0")
    private Boolean isDropContainerPointIdentical;
    
    
    @ApiModelProperty(value = "重点跟单原因",example = "[CONTAINER_DISPATCHED]")
    private List<Long> importantReasonIds; 
    
    @ApiModelProperty(value = "提箱堆场",example = "奥吉三堆场")
    private String depot;

    @ApiModelProperty(value = "提箱堆场Id",example = "97")
    private String depotId;
    
    @ApiModelProperty(value = "是否有特殊要求")
    private Boolean hasSpecialRequirement;
    
    @ApiModelProperty(value = "是否有跟踪")
    private Boolean hasTrack;
    
    @ApiModelProperty(value = "是否出数据截单",example = "1")
    private Boolean isVGMCutoff;
    
    @ApiModelProperty("航次")
    private String voyage;
    
    @ApiModelProperty(value = "门点名称")
    private String doorName;
    
    @ApiModelProperty(value = "门点区域")
    private String doorArea;
    
    @ApiModelProperty(value = "是否新手司机",example = "1")
    private Boolean noviceDriver;
    
    @ApiModelProperty(value = "是否多地装",example = "1")
    private Boolean isMultiPlace;
    
    @ApiModelProperty("是否需要报关")
    private Boolean needDeclared;
    
    @ApiModelProperty("是否监装")
    private Boolean hasInspection;
    
    
    @ApiModelProperty(value = "司机准点率起始",example = "1")
    private Integer onTimeRateFrom;
    
    @ApiModelProperty(value = "司机准点率结束",example = "1")
    private Integer onTimeRateTo;

    @ApiModelProperty(value = "车队长ID",example = "yanqianting,yanggaofeng")
    private String captainId;
    
    @ApiModelProperty(value = "呼叫状态",example = "1")
    private String operationVoiceCallStatus;
    /** desc    doorPointNumber  */

    /** company  */
    @ApiModelProperty(value = "客户级别",example = "true")
    private String customerLevel;

    @ApiModelProperty(value = "客户类型",example = "1")
    private String businessType;
    /** company end */
    
    /**====== task   start */
    @ApiModelProperty(value = "任务类型",example = "1")
    private String taskTypeId;
    
    @ApiModelProperty(value = "绑定类型 0套班，1 带货， 2 套箱",example = "1")
    private Integer taskBandType;
    
    @ApiModelProperty(value = "是否有绑定类型( 0套班，1 带货， 2 套箱)",example = "true")
    private Boolean hasTaskBandType;
    
    @ApiModelProperty(value = "排除的任务类型",example = "1")
    private String excludeTaskTypeId;
    
    @ApiModelProperty(value = "所属区域",example = "1")
    private String belongsArea;
    /**======= task   end */

    @ApiModelProperty(value = "线路id")
    private Long routeId;

    @ApiModelProperty(value = "是否异常",example = "true")
    private Boolean isException;

    @ApiModelProperty(value = "二班已联系",example = "true")
    private Boolean returnHasVisit;

    @ApiModelProperty(value = "箱编号",example = "1")
    private String businessContainerId;

    @ApiModelProperty(value = "多式联运来源",example = "1")
    private String waterageOrigin;

    @ApiModelProperty(value = "是否委托报关",example = "1")
    private Boolean contractDeclarationStatus;

    @ApiModelProperty(value = "是否完成报关",example = "1")
    private Boolean completeCustomsDeclaration;

    @ApiModelProperty(value = "报关核实状态",example = "NONE：无需核实；WAIT_CONFIRM：待确认；CONFIRMED：已确认；")
    private String customsDeclareConfirm;

    @ApiModelProperty(value = "是否做箱延后",example = "1")
    private Boolean isDelayOrder;
}
