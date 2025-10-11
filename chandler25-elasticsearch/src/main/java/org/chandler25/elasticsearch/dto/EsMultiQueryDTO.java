package org.chandler25.elasticsearch.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel("es多字段联合搜索")
public class EsMultiQueryDTO {
    @ApiModelProperty("es对应的key值")
    @NotBlank(message = "无效的key")
    private List<String> key;
    
    @ApiModelProperty("匹配值")
    @NotNull(message = "无效的值")
    private String value;
    
    @ApiModelProperty("匹配值")
    @NotNull(message = "无效的值")
    @Min(value = 1)
    @Max(value = 100)
    private Integer minimum;
    
    
    public String getMinimumShouldMatch() {
    	if(minimum!=null) {
    		return minimum+"%";
    	}else {
    		return "50%";
    	}
    }

}
