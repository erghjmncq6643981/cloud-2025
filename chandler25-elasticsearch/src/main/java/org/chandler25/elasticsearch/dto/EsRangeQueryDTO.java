package org.chandler25.elasticsearch.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("es搜索范围查询参数")
public class EsRangeQueryDTO {

    @ApiModelProperty(value = "es对应的key值")
    private String key;
    @ApiModelProperty(value = "起始值")
    private Object from;
    @ApiModelProperty(value = "结束值")
    private Object to;

}
