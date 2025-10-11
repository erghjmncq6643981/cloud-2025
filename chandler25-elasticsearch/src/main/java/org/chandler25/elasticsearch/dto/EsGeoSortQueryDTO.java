package org.chandler25.elasticsearch.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
@ApiModel("es距离搜索排序参数")
public class EsGeoSortQueryDTO {

    public enum OrderType{
        ASC,
        DESC;
    }
    
    public enum GeoUnitType{
        Kilometers ,
        NauticMiles
    }
    
    @ApiModelProperty("es对应的key值")
    private String key;
    
	@ApiModelProperty("维度")
	@NotNull(message = "无效的值")
	private Double lat;
	
	@ApiModelProperty("经度")
	@NotNull(message = "无效的值")
	private Double lon;
	
	@ApiModelProperty("距离单位")
	@NotNull(message = "无效的值")
	private GeoUnitType geoUnitType;
	
    @ApiModelProperty(value = "排序模式",allowableValues = "ASC,DESC")
    private OrderType orderType;
    
    @ApiModelProperty("排序的优先级，0优先级最高，值越大优先级越低")
    private int order;
    
}
