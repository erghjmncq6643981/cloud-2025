package org.chandler25.elasticsearch.queryconvert;


import org.chandler25.elasticsearch.dto.EsQueryDTO;

/**
 * 将现有接口的查询条件转换为对应es查询条件
 */
public interface QueryConvert<T> {

    /**
     * @param query 现有接口的查询条件
     * @return es查询条件
     */
    EsQueryDTO convert(T query);

}
