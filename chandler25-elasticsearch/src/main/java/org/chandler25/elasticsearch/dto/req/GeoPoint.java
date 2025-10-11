package org.chandler25.elasticsearch.dto.req;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel("地理位置")
public class GeoPoint {
	
	@ApiModelProperty("维度")
	@NotNull(message = "无效的值")
	private Double lat;
	
	@ApiModelProperty("经度")
	@NotNull(message = "无效的值")
	private Double lon;
	
	@ApiModelProperty("范围距离")
	@NotNull(message = "无效的值")
	private Double distance;
	
	
}
