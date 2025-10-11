package org.chandler25.elasticsearch.queryconvert;

import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chandler25.elasticsearch.dto.*;
import org.chandler25.elasticsearch.dto.req.OrderQueryReq;
import org.springframework.cglib.beans.BeanMap;

import java.util.*;

/**
 * 订单搜索
 */
@Slf4j
public class OrderQueryConvert implements QueryConvert<OrderQueryReq> {

    /**
     * 查询条件转换map
     */
    private Map<String, QueryMapConvert> queryMap;
    /**
     * 排序条件转换map
     */
    private Map<String, List<String>> sortMap;
    /**
     * 初始的查询
     */
    private OrderQueryReq query;
    /**
     * es查询条件
     */
    private List<EsBoolQueryDTO> boolQueries = new ArrayList<>();
    /**
     * es排序
     */
    private List<EsSortQueryDTO> sorts = new ArrayList<>();


    /**
     * @param query
     * @return
     */
    @Override
    public EsQueryDTO convert(OrderQueryReq query) {
        if (query == null) {
            return null;
        }
        this.query = query;
        EsQueryDTO esQueryDTO = new EsQueryDTO();
        //分页
        esQueryDTO.setPage(this.query.getPage() - 1);
//        esQueryDTO.setPageSize(this.query.getPageSize());
        esQueryDTO.setPageSize(this.query.getRows());
        //查询条件
        buildQuery();
        EsBoolQueryDTO boolQueryDTO = new EsBoolQueryDTO();
        boolQueryDTO.setType(EsBoolQueryDTO.Type.MUST);
        boolQueryDTO.setBoolQueries(boolQueries);
        esQueryDTO.setBoolQuery(boolQueryDTO);
        //排序
        buildSort();
        esQueryDTO.setSortQueries(this.sorts);
        log.debug("esQueryDTO: {}", JSON.toJSONString(esQueryDTO));
        return esQueryDTO;
    }

    /**
     * 搜索排序
     */
    private void buildSort() {
        int order = 0;
        //有排序字段
        String sortItem = this.query.getSortItem();
        if (StringUtils.isNotEmpty(sortItem)
                && this.sortMap.containsKey(sortItem)) {
            //对应es的key
            List<String> items = sortMap.get(sortItem);
            for (String item : items) {
                EsSortQueryDTO esSortQueryDTO = new EsSortQueryDTO();
                esSortQueryDTO.setKey(item);
                esSortQueryDTO.setOrder(order);
                if (StringUtils.isEmpty(this.query.getSort())
                        || "asc".equalsIgnoreCase(this.query.getSort())) {
                    esSortQueryDTO.setOrderType(EsSortQueryDTO.OrderType.ASC);
                } else {
                    esSortQueryDTO.setOrderType(EsSortQueryDTO.OrderType.DESC);
                }
                this.sorts.add(esSortQueryDTO);
                order++;
            }
        } else {
            //无排序字段时默认按做箱时间排序
            EsSortQueryDTO esSortQueryDTO = new EsSortQueryDTO();
            esSortQueryDTO.setKey("planArrivalTime");
            esSortQueryDTO.setOrderType(EsSortQueryDTO.OrderType.ASC);
            esSortQueryDTO.setOrder(order);
            this.sorts.add(esSortQueryDTO);
            order++;
        }
        //根据id排序保证每次查询结果顺序一致
        EsSortQueryDTO idSort = new EsSortQueryDTO();
        idSort.setKey("containerId");
        idSort.setOrderType(EsSortQueryDTO.OrderType.ASC);
        idSort.setOrder(order);
        this.sorts.add(idSort);
    }


    /**
     * 查询条件
     */
    private void buildQuery() {
        BeanMap beanMap = BeanMap.create(this.query);
        Map<String, Object> map = new HashMap<>();
        map.putAll(beanMap);
        //范围查询
        Map<String, EsRangeQueryDTO> rangeMap = new HashMap<>();
        List<EsMatchQueryDTO> matchs = Lists.newArrayList();
        List<EsRangeQueryDTO> ranges = Lists.newArrayList();
        List<EsWildcardQueryDTO> wildcardQueries = Lists.newArrayList();
        List<EsExistQueryDTO> existQueries = Lists.newArrayList();
        List<EsTermsQueryDTO> termsQueries = Lists.newArrayList();
        List<EsMultiQueryDTO> multiQueries = Lists.newArrayList();
        List<EsMatchPhraseQueryDTO> matchPhraseQueries = new ArrayList<>();
        List<EsMatchPhrasePrefixQueryDTO> matchPhrasePrefixQueries = new ArrayList<>();

        //根据配置获取范围查询和条件查询
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (value instanceof String
                    && StringUtils.isEmpty((String) value)) {
                continue;
            }
            if (value instanceof Collection
                    && ((Collection<?>) value).isEmpty()) {
                continue;
            }
            if (!queryMap.containsKey(key)) {
                continue;
            }
            // 新门点去外面特殊处理
            if ("newDoorPoint".equals(key)) {
                continue;
            }
            //范围查询整合
            QueryMapConvert convert = queryMap.get(key);
            Object queryDto = convert.convert(value);
            if (queryDto instanceof EsRangeQueryDTO) {
                String queryKey = ((EsRangeQueryDTO) queryDto).getKey();
                Object from = ((EsRangeQueryDTO) queryDto).getFrom();
                Object to = ((EsRangeQueryDTO) queryDto).getTo();
                if (rangeMap.containsKey(queryKey)) {
                    if (from != null) {
                        rangeMap.get(queryKey).setFrom(from);
                    }
                    if (to != null) {
                        rangeMap.get(queryKey).setTo(to);
                    }
                } else {
                    rangeMap.put(queryKey, (EsRangeQueryDTO) queryDto);
                }
            }
            //条件查询
            if (queryDto instanceof EsMatchQueryDTO) {
                matchs.add((EsMatchQueryDTO) queryDto);
            }
            //模糊查询
            if (queryDto instanceof EsWildcardQueryDTO) {
                wildcardQueries.add((EsWildcardQueryDTO) queryDto);
            }
            //是否存在查询
            if (queryDto instanceof EsExistQueryDTO) {
                existQueries.add((EsExistQueryDTO) queryDto);
            }
            //复合条件查询
            if (queryDto instanceof EsBoolQueryDTO) {
                boolQueries.add((EsBoolQueryDTO) queryDto);
            }
            if (queryDto instanceof EsTermsQueryDTO) {
                termsQueries.add((EsTermsQueryDTO) queryDto);
            }
            if (queryDto instanceof EsMultiQueryDTO) {
                multiQueries.add((EsMultiQueryDTO) queryDto);
            }
            if (queryDto instanceof EsMatchPhraseQueryDTO) {
                matchPhraseQueries.add((EsMatchPhraseQueryDTO) queryDto);
            }
            if (queryDto instanceof EsMatchPhrasePrefixQueryDTO) {
                matchPhrasePrefixQueries.add((EsMatchPhrasePrefixQueryDTO) queryDto);
            }


        }
        //不查询待提交
        EsBoolQueryDTO mustNotWaitSubmitQueryDTO = new EsBoolQueryDTO();
        mustNotWaitSubmitQueryDTO.setType(EsBoolQueryDTO.Type.MUST_NOT);
        EsMatchQueryDTO waitStatus = new EsMatchQueryDTO("status", "CONTAINER_WAIT_SUBMIT");
        mustNotWaitSubmitQueryDTO.setMatchQueries(Lists.newArrayList(waitStatus));
        boolQueries.add(mustNotWaitSubmitQueryDTO);
        //默认不查询已取消的订单
        if (!map.containsKey("statuses")
                || map.get("statuses") == null) {
            EsBoolQueryDTO esBoolQueryDTO = new EsBoolQueryDTO();
            esBoolQueryDTO.setType(EsBoolQueryDTO.Type.MUST_NOT);
            EsMatchQueryDTO status = new EsMatchQueryDTO("status", "CONTAINER_CANCELLED");
            esBoolQueryDTO.setMatchQueries(Lists.newArrayList(status));
            boolQueries.add(esBoolQueryDTO);
        }
        // 新门点特殊处理
        if (map.containsKey("newDoorPoint")
                && map.get("newDoorPoint") != null) {
            Boolean value = (Boolean) map.get("newDoorPoint");
            EsBoolQueryDTO esBoolQueryDTO = new EsBoolQueryDTO();
            if (Boolean.TRUE.equals(value)) {
                esBoolQueryDTO.setType(EsBoolQueryDTO.Type.MUST);
            } else {
                esBoolQueryDTO.setType(EsBoolQueryDTO.Type.MUST_NOT);
            }
            EsMatchQueryDTO newDoorPoint = new EsMatchQueryDTO("newDoorPoint", true);
            esBoolQueryDTO.setMatchQueries(Lists.newArrayList(newDoorPoint));
            boolQueries.add(esBoolQueryDTO);
        }
        // 默认不查询 orderTypeCode 为 'EMPTY_TRANS', 'SCHEDULED_ACCRUAL', 'GATE_IN_SHORT', 'EMPTY_TRANSFER_TURN', 'INTERNAL', 'HEAVY_TRANS'
        if (map.containsKey("outOfShortBarge")) {
            EsBoolQueryDTO esBoolQueryDTO = new EsBoolQueryDTO();
            esBoolQueryDTO.setType(EsBoolQueryDTO.Type.MUST_NOT);
            String[] typeCodes = {"EMPTY_TRANS", "SCHEDULED_ACCRUAL", "GATE_IN_SHORT", "EMPTY_TRANSFER_TURN", "INTERNAL", "HEAVY_TRANS"};
            List<EsMatchQueryDTO> querys = Lists.newArrayList();
            for (String code : typeCodes) {
                EsMatchQueryDTO orderTypeCode = new EsMatchQueryDTO("orderTypeCode", code);
                querys.add(orderTypeCode);
            }
            esBoolQueryDTO.setMatchQueries(querys);
            boolQueries.add(esBoolQueryDTO);
        }
        //范围查询赋值
        rangeMap.forEach((key, value) -> {
            ranges.add(value);
        });
        EsBoolQueryDTO esBoolQueryDTO = new EsBoolQueryDTO();
        esBoolQueryDTO.setType(EsBoolQueryDTO.Type.MUST);
        esBoolQueryDTO.setMatchQueries(matchs);
        esBoolQueryDTO.setWildcardQueries(wildcardQueries);
        esBoolQueryDTO.setRangeQueries(ranges);
        esBoolQueryDTO.setExistQueries(existQueries);
        esBoolQueryDTO.setTermsQueries(termsQueries);
        esBoolQueryDTO.setMatchPhrasePrefixQueries(matchPhrasePrefixQueries);

        boolQueries.add(esBoolQueryDTO);
    }

    /**
     * @param queryMap 查询条件转换map
     * @param sortMap  排序条件转换map
     */
    public OrderQueryConvert(Map<String, QueryMapConvert> queryMap, Map<String, List<String>> sortMap) {
        this.queryMap = queryMap;
        this.sortMap = sortMap;
    }

}
