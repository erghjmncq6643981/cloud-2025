package org.chandler25.elasticsearch.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel("es搜索精确查询")
public class EsTermsQueryDTO {
    @ApiModelProperty("es对应的key值")
    @NotBlank(message = "无效的key")
    private String key;
    @ApiModelProperty("匹配值")
    @NotNull(message = "无效的值")
    private Collection<String> value;

}
