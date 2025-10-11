package org.chandler25.elasticsearch.queryconvert;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.chandler25.elasticsearch.dto.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 转换器工厂
 */
public class QueryConvertFactory {

    private static final Map<String, QueryMapConvert> ORDER_QUERY_MAP = new HashMap<>();
    private static final Map<String, List<String>> ORDER_SORT_MAP = new HashMap<>();

    static {
        //查询条件
        ORDER_QUERY_MAP.put("planArriveTimeFrom", t -> new EsRangeQueryDTO("planArrivalTime", t, null));
        ORDER_QUERY_MAP.put("planArriveTimeTo", t -> new EsRangeQueryDTO("planArrivalTime", null, t));
        ORDER_QUERY_MAP.put("driverPlanArrivalTimeFrom", t -> new EsRangeQueryDTO("driverPlanArrivalTime", t, null));
        ORDER_QUERY_MAP.put("driverPlanArrivalTimeTo", t -> new EsRangeQueryDTO("driverPlanArrivalTime", null, t));
        ORDER_QUERY_MAP.put("containerNo", t -> new EsWildcardQueryDTO("containerNo", "*" + t + "*"));
        ORDER_QUERY_MAP.put("deviceSingleType", t -> new EsMatchQueryDTO("deviceSingleType", t));
        ORDER_QUERY_MAP.put("hasDeviceSingleType", t -> {
            EsBoolQueryDTO boolQueryDTO = new EsBoolQueryDTO();
            boolQueryDTO.setExistQueries(Lists.newArrayList(new EsExistQueryDTO("deviceSingleType")));
            if ((Boolean) t) {
                boolQueryDTO.setType(EsBoolQueryDTO.Type.MUST);
            } else {
                boolQueryDTO.setType(EsBoolQueryDTO.Type.MUST_NOT);
            }
            return boolQueryDTO;
        });
        ORDER_QUERY_MAP.put("isDeviceTypePaperless", t -> new EsMatchQueryDTO("isDeviceTypePaperless", t));


        ORDER_QUERY_MAP.put("commissionTimeFrom", t -> new EsRangeQueryDTO("commissionTime", t, null));
        ORDER_QUERY_MAP.put("commissionTimeTo", t -> new EsRangeQueryDTO("commissionTime", null, t));
        ORDER_QUERY_MAP.put("etdFrom", t -> new EsRangeQueryDTO("etd", t, null));
        ORDER_QUERY_MAP.put("etdTo", t -> new EsRangeQueryDTO("etd", null, t));
        ORDER_QUERY_MAP.put("businessNo", t -> new EsWildcardQueryDTO("businessNo", "*" + t + "*"));
        ORDER_QUERY_MAP.put("buyerId", t -> new EsMatchQueryDTO("buyerId", t));

        ORDER_QUERY_MAP.put("orderId", t -> new EsMatchQueryDTO("orderId", t));
        ORDER_QUERY_MAP.put("orderNo", t -> {
            if (((String) t).contains("-")) {
                String[] split = ((String) t).split("-");
                EsBoolQueryDTO boolQueryDTO = new EsBoolQueryDTO();
                boolQueryDTO.setType(EsBoolQueryDTO.Type.MUST);
                List<EsMatchQueryDTO> matchQueries = boolQueryDTO.getMatchQueries();
                matchQueries.add(new EsMatchQueryDTO("truckOrderNo", split[0]));
                if (split.length > 1 && StringUtils.isNotBlank(split[1])) {
                    matchQueries.add(new EsMatchQueryDTO("sequenceNo", split[1]));
                }
                return boolQueryDTO;
            }
            return new EsWildcardQueryDTO("truckOrderNo", "*" + t + "*");
        });
        ORDER_QUERY_MAP.put("customerService", t -> {
            if (((String) t).contains(",")) {
                String[] split = ((String) t).split(",");
                EsBoolQueryDTO boolQueryDTO = new EsBoolQueryDTO();
                boolQueryDTO.setType(EsBoolQueryDTO.Type.SHOULD);
                List<EsMatchQueryDTO> list = Lists.newArrayList();
                for (String s : split) {
                    list.add(new EsMatchQueryDTO("customerService", s));
                }
                boolQueryDTO.setMatchQueries(list);
                return boolQueryDTO;
            }
            return new EsMatchQueryDTO("customerService", t);
        });
        /** 车牌号*/
        ORDER_QUERY_MAP.put("truckPlateNumber", t -> new EsMatchPhrasePrefixQueryDTO("truckPlateNumber", t));
        ORDER_QUERY_MAP.put("truckPlateNumbers", t -> {
            if (((String) t).contains(",")) {
                String[] split = ((String) t).split(",");
                return new EsTermsQueryDTO("truckPlateNumberKey", Arrays.stream(split).collect(Collectors.toList()));
            }
            return new EsTermsQueryDTO("truckPlateNumberKey", Lists.newArrayList(t));
        });

        ORDER_QUERY_MAP.put("blNo", t -> new EsWildcardQueryDTO("blNo", "*" + t + "*"));
        ORDER_QUERY_MAP.put("forwarderOrderId", t -> new EsWildcardQueryDTO("forwarderOrderId", "*" + t + "*"));
        ORDER_QUERY_MAP.put("forwarderContainerId", t -> new EsWildcardQueryDTO("forwarderContainerId", "*" + t + "*"));
        ORDER_QUERY_MAP.put("containerSizeType", t -> new EsMatchQueryDTO("containerSizeType", t));
        ORDER_QUERY_MAP.put("depotArea", t -> new EsMatchPhrasePrefixQueryDTO("depotArea", t));
        ORDER_QUERY_MAP.put("dropDepotId", t -> new EsTermsQueryDTO("depotId", Lists.newArrayList(t)));
        ORDER_QUERY_MAP.put("dropDepotName", t -> new EsTermsQueryDTO("dropDepotName", Lists.newArrayList(t)));
        ORDER_QUERY_MAP.put("orderCreateBy", t -> new EsMatchQueryDTO("orderCreateBy", t));

        ORDER_QUERY_MAP.put("vessel", t -> {
            if (((String) t).contains(" ")) {
                String split = ((String) t).replace(" ", "");
                return new EsMatchPhrasePrefixQueryDTO("vessel", split);
            }
            return new EsMatchPhrasePrefixQueryDTO("vessel", t);
        });
        ORDER_QUERY_MAP.put("voyage", t -> new EsMatchPhrasePrefixQueryDTO("voyage", t));
        ORDER_QUERY_MAP.put("doorAddress", t -> new EsMatchPhrasePrefixQueryDTO("doorAddress", t));
        ORDER_QUERY_MAP.put("doorName", t -> new EsMatchPhrasePrefixQueryDTO("doorName", t));
        ORDER_QUERY_MAP.put("doorArea", t -> new EsMatchPhrasePrefixQueryDTO("doorArea", t));

        ORDER_QUERY_MAP.put("driverId", t -> new EsMatchQueryDTO("driverId", t));
        ORDER_QUERY_MAP.put("driverName", t -> new EsMatchQueryDTO("driverName", t));
        ORDER_QUERY_MAP.put("orderTaker", t -> new EsMatchQueryDTO("orderTaker", t));
        ORDER_QUERY_MAP.put("hasCustomerConfirmedPlan", t -> new EsMatchQueryDTO("hasCustomerConfirmedPlan", t));
        ORDER_QUERY_MAP.put("isContainerNoBooked", t -> new EsMatchQueryDTO("isContainerNoBooked", t));
        ORDER_QUERY_MAP.put("isCustomerContainerNumber", t -> new EsMatchQueryDTO("isCustomerContainerNumber", t));

        ORDER_QUERY_MAP.put("transportType", t -> new EsMatchQueryDTO("transportType", t));
        ORDER_QUERY_MAP.put("transitPoint", t -> new EsMatchQueryDTO("transitPoint", t));


        ORDER_QUERY_MAP.put("isImportantContainer", t -> new EsMatchQueryDTO("isImportantContainer", (int) t == 1));
        ORDER_QUERY_MAP.put("importantReasonIds", t -> new EsTermsQueryDTO("importantReasonIdList", (Collection<?>) t));

        ORDER_QUERY_MAP.put("isDropContainer", t -> new EsMatchQueryDTO("isDropContainer", t));
        ORDER_QUERY_MAP.put("isEarlierGateIn", t -> new EsMatchQueryDTO("isEarlierGateIn", t));
        ORDER_QUERY_MAP.put("gateIn", t -> new EsMatchQueryDTO("gateIn", t));
        ORDER_QUERY_MAP.put("isVip", t -> new EsMatchQueryDTO("isVip", t));
        ORDER_QUERY_MAP.put("driverRecord", t -> new EsMatchQueryDTO("driverRecord", t));
        ORDER_QUERY_MAP.put("isOpeningTime", t -> {
            EsBoolQueryDTO boolQueryDTO = new EsBoolQueryDTO();
            boolQueryDTO.setExistQueries(Lists.newArrayList(new EsExistQueryDTO("openingTime")));
            if ((Boolean) t) {
                boolQueryDTO.setType(EsBoolQueryDTO.Type.MUST);
            } else {
                boolQueryDTO.setType(EsBoolQueryDTO.Type.MUST_NOT);
            }
            return boolQueryDTO;
        });

        ORDER_QUERY_MAP.put("openingTimeFrom", t -> new EsRangeQueryDTO("openingTime", t, null));
        ORDER_QUERY_MAP.put("openingTimeTo", t -> new EsRangeQueryDTO("openingTime", null, t));
        ORDER_QUERY_MAP.put("isClosingTime", t -> {
            EsBoolQueryDTO boolQueryDTO = new EsBoolQueryDTO();
            boolQueryDTO.setExistQueries(Lists.newArrayList(new EsExistQueryDTO("closingTime")));
            if ((Boolean) t) {
                boolQueryDTO.setType(EsBoolQueryDTO.Type.MUST);
            } else {
                boolQueryDTO.setType(EsBoolQueryDTO.Type.MUST_NOT);
            }
            return boolQueryDTO;
        });
        ORDER_QUERY_MAP.put("closingTimeFrom", t -> new EsRangeQueryDTO("closingTime", t, null));
        ORDER_QUERY_MAP.put("closingTimeTo", t -> new EsRangeQueryDTO("closingTime", null, t));
        ORDER_QUERY_MAP.put("cutoffCustomsTimeWarningFrom", t -> new EsRangeQueryDTO("cutoffCustomsTimeWarning", t, null));
        ORDER_QUERY_MAP.put("cutoffCustomsTimeWarningTo", t -> new EsRangeQueryDTO("cutoffCustomsTimeWarning", null, t));
        ORDER_QUERY_MAP.put("orderDocumentCreatedByName", t -> new EsMatchQueryDTO("orderDocumentCreatedBy", t));
        ORDER_QUERY_MAP.put("orderDocumentCreatedBy", t -> new EsMatchQueryDTO("orderDocumentCreatedBy", t));
        ORDER_QUERY_MAP.put("orderPayTypeId", t -> new EsMatchQueryDTO("orderPayTypeId", t));
        ORDER_QUERY_MAP.put("isFreeze", t -> new EsMatchQueryDTO("isFreeze", t));
        ORDER_QUERY_MAP.put("needNucleicTest", t -> new EsMatchQueryDTO("needNucleicTest", t));
        ORDER_QUERY_MAP.put("newDoorPoint", t -> new EsMatchQueryDTO("newDoorPoint", t));
        ORDER_QUERY_MAP.put("isCustomsRecord", t -> new EsMatchQueryDTO("isCustomsRecord", t));
        ORDER_QUERY_MAP.put("isIncidentally", t -> new EsMatchQueryDTO("isIncidentally", t));
        ORDER_QUERY_MAP.put("isChartered", t -> new EsMatchQueryDTO("isChartered", t));
        ORDER_QUERY_MAP.put("orderTypeCode", t -> {
            if (((String) t).contains(",")) {
                String[] split = ((String) t).split(",");
                EsBoolQueryDTO boolQueryDTO = new EsBoolQueryDTO();
                boolQueryDTO.setType(EsBoolQueryDTO.Type.SHOULD);
                List<EsMatchQueryDTO> list = Lists.newArrayList();
                for (String s : split) {
                    list.add(new EsMatchQueryDTO("orderTypeCode", s));
                }
                boolQueryDTO.setMatchQueries(list);
                return boolQueryDTO;
            }
            return new EsMatchQueryDTO("orderTypeCode", t);
        });
        ORDER_QUERY_MAP.put("portDistrict", t -> new EsMatchPhrasePrefixQueryDTO("portDistrict", t));
        ORDER_QUERY_MAP.put("portId", t -> {
            if (((String) t).contains(",")) {
                String[] split = ((String) t).split(",");
                EsBoolQueryDTO boolQueryDTO = new EsBoolQueryDTO();
                boolQueryDTO.setType(EsBoolQueryDTO.Type.SHOULD);
                List<EsMatchQueryDTO> list = Lists.newArrayList();
                for (String s : split) {
                    list.add(new EsMatchQueryDTO("portId", s));
                }
                boolQueryDTO.setMatchQueries(list);
                return boolQueryDTO;
            }
            return new EsMatchQueryDTO("portId", t);
        });
        ORDER_QUERY_MAP.put("customerServiceGroupId", t -> {
            if (((String) t).contains(",")) {
                String[] split = ((String) t).split(",");
                EsBoolQueryDTO boolQueryDTO = new EsBoolQueryDTO();
                boolQueryDTO.setType(EsBoolQueryDTO.Type.SHOULD);
                List<EsMatchQueryDTO> list = Lists.newArrayList();
                for (String s : split) {
                    list.add(new EsMatchQueryDTO("customerServiceGroupId", s));
                }
                boolQueryDTO.setMatchQueries(list);
                return boolQueryDTO;
            }
            return new EsMatchQueryDTO("customerServiceGroupId", t);
        });
        //ORDER_QUERY_MAP.put("customerServiceGroupId",t->new EsMatchQueryDTO("customerServiceGroupId",t));
        ORDER_QUERY_MAP.put("requireReceipt", t -> new EsMatchQueryDTO("requireReceipt", t));
        ORDER_QUERY_MAP.put("salesmanfirst", t -> new EsMatchQueryDTO("salesmanfirst", t));
        ORDER_QUERY_MAP.put("salesId", t -> new EsMatchQueryDTO("salesId", t));
        ORDER_QUERY_MAP.put("shippingCompany", t -> new EsMatchPhrasePrefixQueryDTO("shippingCompany", t));
        ORDER_QUERY_MAP.put("hasShippingCompany", t -> new EsMatchQueryDTO("hasShippingCompany", t));
//        ORDER_QUERY_MAP.put("hasShippingCompany", t -> {
//            EsBoolQueryDTO boolQueryDTO = new EsBoolQueryDTO();
//            boolQueryDTO.setExistQueries(Lists.newArrayList(new EsExistQueryDTO("shippingCompany")));
//            if ((Boolean) t) {
//                boolQueryDTO.setType(EsBoolQueryDTO.Type.MUST);
//            } else {
//                boolQueryDTO.setType(EsBoolQueryDTO.Type.MUST_NOT);
//            }
//            return boolQueryDTO;
//        });
        ORDER_QUERY_MAP.put("isCustomerNotifyApplyEir", t -> new EsMatchQueryDTO("isCustomerNotifyApplyEir", t));
        ORDER_QUERY_MAP.put("allowPrint", t -> {
            if ("1".equals(t)) {
                return new EsMatchQueryDTO("allowPrint", true);
            }
            return new EsMatchQueryDTO("allowPrint", false);
        });
        ORDER_QUERY_MAP.put("statuses", t -> {
            EsBoolQueryDTO boolQueryDTO = new EsBoolQueryDTO();
            boolQueryDTO.setType(EsBoolQueryDTO.Type.SHOULD);
            List<EsMatchQueryDTO> matchQueries = boolQueryDTO.getMatchQueries();
            for (String s : (List<String>) t) {
                matchQueries.add(new EsMatchQueryDTO("status", s));
            }
            return boolQueryDTO;
        });
        ORDER_QUERY_MAP.put("driverVariety", t -> {
            if (1 == (int) t) {
                return new EsMatchQueryDTO("isVip", Boolean.TRUE);
            }
            if (2 == (int) t) {
                return new EsMatchQueryDTO("selfSupport", Boolean.TRUE);
            }
            if (3 == (int) t) {
                return new EsMatchQueryDTO("planformBuyTruck", Boolean.TRUE);
            }
            if (4 == (int) t) {
                return new EsMatchQueryDTO("dispatchType", "外发");
            }
            return null;
        });

        ORDER_QUERY_MAP.put("validEndTimeFrom", t -> new EsRangeQueryDTO("validEndTime", t, null));
        ORDER_QUERY_MAP.put("validEndTimeTo", t -> new EsRangeQueryDTO("validEndTime", null, t));

        ORDER_QUERY_MAP.put("operationVoiceCallStatus", t -> {
            if (((String) t).contains(",")) {
                String[] split = ((String) t).split(",");
                return new EsTermsQueryDTO("operationVoiceCallStatusKey", Arrays.stream(split).collect(Collectors.toList()));
            }
            return new EsTermsQueryDTO("operationVoiceCallStatusKey", Lists.newArrayList(t));
        });
        ORDER_QUERY_MAP.put("apptGateInStatus", t -> new EsMatchQueryDTO("apptGateInStatus", t));
        ORDER_QUERY_MAP.put("dispatchType", t -> new EsMatchQueryDTO("dispatchType", t));
        ORDER_QUERY_MAP.put("truckType", t -> new EsMatchQueryDTO("truckType", t));
        ORDER_QUERY_MAP.put("terminationNeedReturnBack", t -> new EsMatchQueryDTO("terminationNeedReturnBack", t));
        ORDER_QUERY_MAP.put("isTerminationReturnBack", t -> new EsMatchQueryDTO("isTerminationReturnBack", t));
        ORDER_QUERY_MAP.put("containerNumberConfirmed", t -> new EsMatchQueryDTO("containerNumberConfirmed", t));
        ORDER_QUERY_MAP.put("customerLevel", t -> new EsMatchQueryDTO("customerLevel", t));
        ORDER_QUERY_MAP.put("documentaryOffTimeFrom", t -> new EsRangeQueryDTO("documentaryOffTime", t, null));
        ORDER_QUERY_MAP.put("documentaryOffTimeTo", t -> new EsRangeQueryDTO("documentaryOffTime", null, t));
        ORDER_QUERY_MAP.put("requiredGateInTimeFrom", t -> new EsRangeQueryDTO("requiredGateInTime", t, null));
        ORDER_QUERY_MAP.put("requiredGateInTimeTo", t -> new EsRangeQueryDTO("requiredGateInTime", null, t));
        ORDER_QUERY_MAP.put("hasDropContainer", t -> new EsMatchQueryDTO("hasDropContainer", t));
        ORDER_QUERY_MAP.put("hasPickup", t -> new EsMatchQueryDTO("hasPickup", t));
        ORDER_QUERY_MAP.put("hasDoorArrived", t -> new EsMatchQueryDTO("hasDoorArrived", t));
        ORDER_QUERY_MAP.put("doorArrivalTimeFrom", t -> new EsRangeQueryDTO("doorArrivalTime", t, null));
        ORDER_QUERY_MAP.put("doorArrivalTimeTo", t -> new EsRangeQueryDTO("doorArrivalTime", null, t));
        ORDER_QUERY_MAP.put("hasDoorLeft", t -> new EsMatchQueryDTO("hasDoorLeft", t));
        ORDER_QUERY_MAP.put("needContainerWeighing", t -> new EsMatchQueryDTO("needContainerWeighing", t));
        ORDER_QUERY_MAP.put("depot", t -> new EsMatchQueryDTO("depot", t));
        ORDER_QUERY_MAP.put("depotId", t -> {
            if (((String) t).contains(",")) {
                String[] split = ((String) t).split(",");
                return new EsTermsQueryDTO("depotId", Arrays.stream(split).collect(Collectors.toList()));
            }
            return new EsTermsQueryDTO("depotId", Lists.newArrayList(t));
        });
        ORDER_QUERY_MAP.put("hasSpecialRequirement", t -> new EsMatchQueryDTO("hasSpecialRequirement", t));
        ORDER_QUERY_MAP.put("isVGMCutoff", t -> new EsMatchQueryDTO("isVGMCutoff", t));
        ORDER_QUERY_MAP.put("hasTrack", t -> {
            if ((Boolean) t) {
                return new EsRangeQueryDTO("nextTrackingTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), null);
            } else {
                return new EsRangeQueryDTO("nextTrackingTime", null, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
        });
        ORDER_QUERY_MAP.put("taskTypeId", t -> new EsMatchQueryDTO("taskTypeId", t));
        ORDER_QUERY_MAP.put("excludeTaskTypeId", t -> {
            EsBoolQueryDTO boolQueryDTO = new EsBoolQueryDTO();
            boolQueryDTO.setType(EsBoolQueryDTO.Type.MUST_NOT);
            List<EsMatchQueryDTO> list = Lists.newArrayList();
            if (((String) t).contains(",")) {
                String[] split = ((String) t).split(",");
                for (String s : split) {
                    list.add(new EsMatchQueryDTO("taskTypeId", s));
                }
                boolQueryDTO.setMatchQueries(list);
            } else {
                list.add(new EsMatchQueryDTO("taskTypeId", t));
                boolQueryDTO.setMatchQueries(list);
            }
            return boolQueryDTO;
        });
        ORDER_QUERY_MAP.put("belongsArea", t -> new EsMatchQueryDTO("belongsArea", t));
        ORDER_QUERY_MAP.put("noviceDriver", t -> new EsMatchQueryDTO("noviceDriver", t));
        ORDER_QUERY_MAP.put("isMultiPlace", t -> {
            if ((Boolean) t) {
                return new EsRangeQueryDTO("doorPointNumber", 2, null);
            } else if (!(Boolean) t) {
                return new EsRangeQueryDTO("doorPointNumber", null, 1);
            }
            return null;
        });

        ORDER_QUERY_MAP.put("isDelayOrder", t -> {
            if ((Boolean) t) {
                return new EsRangeQueryDTO("delayCountInfo", 1, null);
            } else if (!(Boolean) t) {
                return new EsMatchQueryDTO("delayCountInfo", 0);
            }
            return null;
        });
        ORDER_QUERY_MAP.put("isPressNight", t -> new EsMatchQueryDTO("isPressNight", t));
        ORDER_QUERY_MAP.put("needDeclared", t -> new EsMatchQueryDTO("needDeclared", t));
        ORDER_QUERY_MAP.put("customsDeclareConfirm", t -> {
            if (((String) t).contains(",")) {
                String[] split = ((String) t).split(",");
                return new EsTermsQueryDTO("customsDeclareConfirm", Arrays.stream(split).collect(Collectors.toList()));
            }
            return new EsTermsQueryDTO("customsDeclareConfirm", Lists.newArrayList(t));
        });
        ORDER_QUERY_MAP.put("onTimeRateFrom", t -> new EsRangeQueryDTO("onTimeRate", t, null));
        ORDER_QUERY_MAP.put("onTimeRateTo", t -> new EsRangeQueryDTO("onTimeRate", null, t));
        ORDER_QUERY_MAP.put("captainId", t -> {
            if (((String) t).contains(",")) {
                String[] split = ((String) t).split(",");
                return new EsTermsQueryDTO("captainId", Arrays.stream(split).collect(Collectors.toList()));
            }
            return new EsTermsQueryDTO("captainId", Lists.newArrayList(t));
        });
        /** 放箱公司*/
        ORDER_QUERY_MAP.put("releaseCompanyId", t -> new EsMatchQueryDTO("releaseCompanyId", t));
        /** 放箱状态*/
        ORDER_QUERY_MAP.put("releaseOrderStatusId", t -> new EsMatchQueryDTO("releaseOrderStatusId", t));
        ORDER_QUERY_MAP.put("taskBandType", t -> new EsMatchQueryDTO("taskBandType", t));
        ORDER_QUERY_MAP.put("hasTaskBandType", t -> {
            EsBoolQueryDTO boolQueryDTO = new EsBoolQueryDTO();
            boolQueryDTO.setExistQueries(Lists.newArrayList(new EsExistQueryDTO("taskBandType")));
            if ((Boolean) t) {
                boolQueryDTO.setType(EsBoolQueryDTO.Type.MUST);
            } else {
                boolQueryDTO.setType(EsBoolQueryDTO.Type.MUST_NOT);
            }
            return boolQueryDTO;
        });

        ORDER_QUERY_MAP.put("hasDropContainerPointId", t -> {
            EsBoolQueryDTO boolQueryDTO = new EsBoolQueryDTO();
            boolQueryDTO.setExistQueries(Lists.newArrayList(new EsExistQueryDTO("dropContainerPointId")));
            if ((Boolean) t) {
                boolQueryDTO.setType(EsBoolQueryDTO.Type.MUST);
            } else {
                boolQueryDTO.setType(EsBoolQueryDTO.Type.MUST_NOT);
            }
            return boolQueryDTO;
        });
        ORDER_QUERY_MAP.put("isDropContainerPointIdentical", t -> new EsMatchQueryDTO("isDropContainerPointIdentical", t));
        ORDER_QUERY_MAP.put("feedback", t -> new EsMatchQueryDTO("feedback", t));
        ORDER_QUERY_MAP.put("hasInspection", t -> new EsMatchQueryDTO("hasInspection", t));
        ORDER_QUERY_MAP.put("weighingFeedback", t -> new EsMatchQueryDTO("weighingFeedback", t));
        ORDER_QUERY_MAP.put("dropContainerConfirm", t -> new EsMatchQueryDTO("dropContainerConfirm", t));
        ORDER_QUERY_MAP.put("routeId", t -> new EsMatchQueryDTO("routeId", t));

        ORDER_QUERY_MAP.put("incidentallyRequire", t -> new EsMatchQueryDTO("incidentallyRequire", t));
        ORDER_QUERY_MAP.put("lclRequire", t -> new EsMatchQueryDTO("lclRequire", t));

        /**是否有异常*/
        ORDER_QUERY_MAP.put("isException", t -> new EsMatchQueryDTO("isException", t));
        ORDER_QUERY_MAP.put("returnHasVisit", t -> new EsMatchQueryDTO("returnHasVisit", t));
        ORDER_QUERY_MAP.put("contractDeclarationStatus", t -> new EsMatchQueryDTO("contractDeclarationStatus", t));
        ORDER_QUERY_MAP.put("completeCustomsDeclaration", t -> new EsMatchQueryDTO("completeCustomsDeclaration", t));
        ORDER_QUERY_MAP.put("businessContainerId", t -> {
            if (((String) t).contains(",")) {
                String[] split = ((String) t).split(",");
                return new EsTermsQueryDTO("businessContainerId", Arrays.stream(split).collect(Collectors.toList()));
            }
            return new EsTermsQueryDTO("businessContainerId", Lists.newArrayList(t));
        });

        ORDER_QUERY_MAP.put("waterageOrigin", t -> {
            if (((String) t).contains(",")) {
                String[] split = ((String) t).split(",");
                return new EsTermsQueryDTO("waterageOrigin", Arrays.stream(split).collect(Collectors.toList()));
            }
            return new EsTermsQueryDTO("waterageOrigin", Lists.newArrayList(t));
        });

        /** company   start **/
        ORDER_QUERY_MAP.put("customerQualityType", t -> new EsMatchQueryDTO("customerQualityType", t));
        ORDER_QUERY_MAP.put("businessType", t -> new EsMatchQueryDTO("businessType", t));
        /** company end  **/

        /** 排序 做箱时间*/
        ORDER_SORT_MAP.put("planArrivalTime", Lists.newArrayList("planArrivalTime"));
        /**订单号*/
        ORDER_SORT_MAP.put("orderNo", Lists.newArrayList("truckOrderNo", "sequenceNo"));
        /**提单号*/
        ORDER_SORT_MAP.put("blNo", Lists.newArrayList("blNo"));
        /**箱型*/
        ORDER_SORT_MAP.put("containerSizeType", Lists.newArrayList("containerSizeType"));
        ORDER_SORT_MAP.put("clientName", Lists.newArrayList("buyerId"));
        /**客服确认做箱时间*/
        ORDER_SORT_MAP.put("confirmOrderTime", Lists.newArrayList("confirmOrderTime"));
        /**客户旺季标签*/
        ORDER_SORT_MAP.put("customerQualityType", Lists.newArrayList("customerQualityType"));
        /**箱号*/
        ORDER_SORT_MAP.put("containerNo", Lists.newArrayList("containerNo"));
        /**司机姓名*/
        ORDER_SORT_MAP.put("driverName", Lists.newArrayList("driverNameAllPinyin"));
        /**距离*/
        ORDER_SORT_MAP.put("distance", Lists.newArrayList("distance"));
        /**预计货重*/
        ORDER_SORT_MAP.put("estimatedWeight", Lists.newArrayList("estimatedWeight"));
        /**是否压夜*/
        ORDER_SORT_MAP.put("isPressNight", Lists.newArrayList("isPressNight"));

        /**截单时间*/
        ORDER_SORT_MAP.put("documentaryOffTime", Lists.newArrayList("documentaryOffTime"));

        /**最晚进港时间*/
        ORDER_SORT_MAP.put("requiredGateInTime", Lists.newArrayList("requiredGateInTime"));
        /**计划提箱时间*/
        ORDER_SORT_MAP.put("appEstimatedPickupTime", Lists.newArrayList("appEstimatedPickupTime"));
        /**到厂时间*/
        ORDER_SORT_MAP.put("doorArrivalTime", Lists.newArrayList("doorArrivalTime"));
        /**迟到时间*/
        ORDER_SORT_MAP.put("lateArrivalTime", Lists.newArrayList("lateArrivalTime"));
        /**任务类型*/
        ORDER_SORT_MAP.put("taskTypeId", Lists.newArrayList("taskTypeId"));
        /**开港时间*/
        ORDER_SORT_MAP.put("openingTime", Lists.newArrayList("openingTime"));
        /**截港时间*/
        ORDER_SORT_MAP.put("closingTime", Lists.newArrayList("closingTime"));
        /**eir有效期*/
        ORDER_SORT_MAP.put("effectiveTerm", Lists.newArrayList("effectiveTerm"));
        /**eir 截止时间*/
        ORDER_SORT_MAP.put("validEndTime", Lists.newArrayList("validEndTime"));
        /**单票箱量*/
        ORDER_SORT_MAP.put("containerQuantity", Lists.newArrayList("containerQuantity"));


        /**重点跟单原因*/
        ORDER_SORT_MAP.put("importantReasonIds", Lists.newArrayList("importantReasonIds"));
        /**进港还箱时间*/
        ORDER_SORT_MAP.put("gateInTime", Lists.newArrayList("gateInTime"));

        /** 排序 离厂时间*/
        ORDER_SORT_MAP.put("doorLeaveTime", Lists.newArrayList("doorLeaveTime"));
        /** 排序 做箱延后次数*/
        ORDER_SORT_MAP.put("delayCountInfo", Lists.newArrayList("delayCountInfo"));

    }

    /**
     * @return 订单搜索接口转换器
     */
    public static QueryConvert getOrderConvert() {
        return new OrderQueryConvert(ORDER_QUERY_MAP, ORDER_SORT_MAP);
    }

}
