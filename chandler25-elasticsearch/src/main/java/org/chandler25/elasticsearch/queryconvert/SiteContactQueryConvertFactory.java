package org.chandler25.elasticsearch.queryconvert;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.chandler25.elasticsearch.dto.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 转换器工厂
 */
public class SiteContactQueryConvertFactory {

    private static final Map<String, QueryMapConvert> QUERY_MAP = new HashMap<>();
    private static final Map<String,List<String>> SORT_MAP = new HashMap<>();

    static {
        //查询条件
    	QUERY_MAP.put("key",t->{
    		List<EsMatchPhrasePrefixQueryDTO> list = Lists.newArrayList();
    		 EsBoolQueryDTO boolQueryDTO = new EsBoolQueryDTO();
             boolQueryDTO.setType(EsBoolQueryDTO.Type.SHOULD);
            if(((String)t).contains(",")){
                String[] split = ((String) t).split(",");
                for (String s : split) {
                	if(StringUtils.isNotBlank(s)&&s.length()>1) {
                		list.add(new EsMatchPhrasePrefixQueryDTO("factoryName",s));
                	}
                }
                for (String s : split) {
                	if(StringUtils.isNotBlank(s)&&s.length()>1) {
                		list.add(new EsMatchPhrasePrefixQueryDTO("address",s));
                	}
                }
            }else {
            	if(StringUtils.isNotBlank((String)t) && t.toString().length()>1) {
            		list.add(new EsMatchPhrasePrefixQueryDTO("factoryName",t));
            		list.add(new EsMatchPhrasePrefixQueryDTO("address",t));
            	}
            }
            boolQueryDTO.setMatchPhrasePrefixQueries(list);
            return boolQueryDTO;
        });
    	QUERY_MAP.put("customerId",t->{
    		return new EsMatchQueryDTO("customerId",t );
    	});
    	QUERY_MAP.put("aggBucket",t->{
    		return new EsAggFieldQueryDTO(""+t,""+t );
    	});
    	QUERY_MAP.put("fuzzyKey",t->{
	        List<String> keys= Lists.newArrayList();
	        keys.add("factoryName");
	        keys.add("address");
    		return new EsMultiQueryDTO(keys,t ,80);
        });
    	
    	QUERY_MAP.put("province",t->new EsMatchQueryDTO("province",t));
    	QUERY_MAP.put("city",t->new EsMatchQueryDTO("city",t));
    	QUERY_MAP.put("district",t->new EsMatchQueryDTO("district",t));
    	QUERY_MAP.put("town",t->new EsMatchQueryDTO("town",t));
    	
        /** 排序*/
        //ORDER_SORT_MAP.put("planArrivalTime", Lists.newArrayList("planArrivalTime"));
    }

    /**
     * @return 订单搜索接口转换器
     */
    public static QueryConvert getConvert(){
        return new SiteContactQueryConvert(QUERY_MAP,SORT_MAP);
    }

}
