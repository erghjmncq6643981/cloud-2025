package org.chandler25.elasticsearch.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("es搜索排序参数")
public class EsSortQueryDTO {

    public enum OrderType{
        ASC,
        DESC;
    }
    @ApiModelProperty("es对应的key值")
    private String key;
    @ApiModelProperty(value = "排序模式",allowableValues = "ASC,DESC")
    private OrderType orderType;
    @ApiModelProperty("排序的优先级，0优先级最高，值越大优先级越低")
    private int order;

}
