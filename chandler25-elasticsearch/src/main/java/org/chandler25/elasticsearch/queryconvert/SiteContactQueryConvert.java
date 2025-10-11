package org.chandler25.elasticsearch.queryconvert;

import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chandler25.elasticsearch.dto.*;
import org.chandler25.elasticsearch.dto.req.GeoPoint;
import org.chandler25.elasticsearch.dto.req.SiteContactQueryReq;
import org.springframework.cglib.beans.BeanMap;

import java.util.*;

/**
 * 订单搜索
 */
@Slf4j
public class SiteContactQueryConvert implements QueryConvert<SiteContactQueryReq>{

    /**
     * 查询条件转换map
     */
    private Map<String,QueryMapConvert> queryMap;
    /**
     * 排序条件转换map
     */
    private Map<String,List<String>> sortMap;
    /**
     * 初始的查询
     */
    private SiteContactQueryReq query;
    /**
     * es查询条件
     */
    private List<EsBoolQueryDTO> boolQueries = new ArrayList<>();
    /**
     * es排序
     */
    private List<EsSortQueryDTO> sorts = new ArrayList<>();
    
    /**
     * es排序
     */
    private List<EsGeoSortQueryDTO> geoSorts = new ArrayList<>();

    /**
     * 聚合字段
     */
    private List<EsAggFieldQueryDTO> aggFields  = new ArrayList<>();
    
    /**
     *
     * @param query
     * @return
     */
    @Override
    public EsQueryDTO convert(SiteContactQueryReq query){
        if(query == null){
            return null;
        }
        this.query = query;
        EsQueryDTO esQueryDTO = new EsQueryDTO();
        //分页
        esQueryDTO.setPage(this.query.getPage()-1);
//        esQueryDTO.setPageSize(this.query.getPageSize());
        esQueryDTO.setPageSize(this.query.getRows());
        esQueryDTO.setPreTags(query.getPreTags());
        esQueryDTO.setPostTags(query.getPostTags());
        //查询条件
        buildQuery();
        esQueryDTO.setAggBuckets(aggFields);;
        
        EsBoolQueryDTO boolQueryDTO = new EsBoolQueryDTO();
        boolQueryDTO.setType(EsBoolQueryDTO.Type.MUST);
        boolQueryDTO.setBoolQueries(boolQueries);
        esQueryDTO.setBoolQuery(boolQueryDTO);
       
        //排序
        buildSort();
        esQueryDTO.setSortQueries(this.sorts);
        esQueryDTO.setGeoSortQueries(this.geoSorts);
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
        if(StringUtils.isNotEmpty(sortItem)
                && this.sortMap.containsKey(sortItem)){
            //对应es的key
            List<String> items = sortMap.get(sortItem);
            for (String item : items) {
                EsSortQueryDTO esSortQueryDTO = new EsSortQueryDTO();
                esSortQueryDTO.setKey(item);
                esSortQueryDTO.setOrder(order);
                if(StringUtils.isEmpty(this.query.getSort())
                        ||"asc".equalsIgnoreCase(this.query.getSort())){
                    esSortQueryDTO.setOrderType(EsSortQueryDTO.OrderType.ASC);
                }else {
                    esSortQueryDTO.setOrderType(EsSortQueryDTO.OrderType.DESC);
                }
                this.sorts.add(esSortQueryDTO);
                order++;
            }
        }
        
        //距离查询 默认按照距离排序
        if(this.query.getGeoPoint()!=null) {
        	EsGeoSortQueryDTO  geoSort=new EsGeoSortQueryDTO();
        	geoSort.setKey("geoPoint");
        	geoSort.setGeoUnitType(EsGeoSortQueryDTO.GeoUnitType.Kilometers);
        	geoSort.setOrderType(EsGeoSortQueryDTO.OrderType.ASC);
        	geoSort.setLat(this.query.getGeoPoint().getLat());
        	geoSort.setLon(this.query.getGeoPoint().getLon());
        	geoSort.setOrder(1);
        	this.geoSorts.add(geoSort);
        }
    }


    /**
     * 查询条件
     */
    private void buildQuery(){
        BeanMap beanMap = BeanMap.create(this.query);
        Map<String,Object> map = new HashMap<>();
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
                }else {
                    rangeMap.put(queryKey,(EsRangeQueryDTO)queryDto);
                }
            }
            //条件查询
            if (queryDto instanceof EsMatchQueryDTO) {
                matchs.add((EsMatchQueryDTO) queryDto);
            }
            //模糊查询
            if(queryDto instanceof EsWildcardQueryDTO){
                wildcardQueries.add((EsWildcardQueryDTO) queryDto);
            }
            //是否存在查询
            if(queryDto instanceof EsExistQueryDTO){
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
            if (queryDto instanceof EsAggFieldQueryDTO) {
            	aggFields.add((EsAggFieldQueryDTO) queryDto);
            }
            
        }
        // 距离查询特殊处理
        if(map.containsKey("geoPoint")
                && map.get("geoPoint") != null){
        	GeoPoint geoPoint =  (GeoPoint)map.get("geoPoint");
        	EsBoolQueryDTO esBoolQueryDTO = new EsBoolQueryDTO();
        	esBoolQueryDTO.setGeoQuery(new EsGeoDistanceQueryDTO("geoPoint",geoPoint.getLat(),geoPoint.getLon(),geoPoint.getDistance()));
        	boolQueries.add(esBoolQueryDTO);
        }
        //范围查询赋值
        rangeMap.forEach((key,value)->{
            ranges.add(value);
        });
        
        		
        EsBoolQueryDTO esBoolQueryDTO = new EsBoolQueryDTO();
        esBoolQueryDTO.setType(EsBoolQueryDTO.Type.MUST);
        esBoolQueryDTO.setMatchQueries(matchs);
        esBoolQueryDTO.setWildcardQueries(wildcardQueries);
        esBoolQueryDTO.setRangeQueries(ranges);
        esBoolQueryDTO.setExistQueries(existQueries);
        esBoolQueryDTO.setTermsQueries(termsQueries);
        esBoolQueryDTO.setMultiQueries(multiQueries);
        esBoolQueryDTO.setMatchPhrasePrefixQueries(matchPhrasePrefixQueries);
        esBoolQueryDTO.setMatchPhraseQueries(matchPhraseQueries);
        boolQueries.add(esBoolQueryDTO);
    }

    /**
     * @param queryMap 查询条件转换map
     * @param sortMap 排序条件转换map
     */
    public SiteContactQueryConvert(Map<String,QueryMapConvert> queryMap,Map<String,List<String>> sortMap){
        this.queryMap = queryMap;
        this.sortMap = sortMap;
    }

}
