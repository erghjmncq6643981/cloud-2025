package org.chandler25.elasticsearch.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel("es聚合字段")
public class EsAggFieldQueryDTO {
   
	@ApiModelProperty("聚合名称")
    @NotBlank(message = "无效的key")
    private String key;
    
    @ApiModelProperty("聚合字段")
    @NotNull(message = "无效的值")
    private String value;

}
