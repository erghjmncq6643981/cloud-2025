package org.chandler25.elasticsearch.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public  abstract class BaseDTO {

	@ApiModelProperty(value = "索引id",example = "1168555")
    private String id;
}
