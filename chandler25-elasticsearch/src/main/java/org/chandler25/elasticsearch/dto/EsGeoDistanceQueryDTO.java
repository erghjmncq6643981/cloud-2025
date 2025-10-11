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
@ApiModel("es地理位置搜索查询")
public class EsGeoDistanceQueryDTO {
	
	@ApiModelProperty("es对应的key值")
	@NotBlank(message = "无效的key")
	private String key;
	
	@ApiModelProperty("维度")
	@NotNull(message = "无效的值")
	private Double lat;
	
	@ApiModelProperty("经度")
	@NotNull(message = "无效的值")
	private Double lon;
	
	@ApiModelProperty("范围距离")
	@NotNull(message = "无效的值")
	private Double distance=10.0D;

}
