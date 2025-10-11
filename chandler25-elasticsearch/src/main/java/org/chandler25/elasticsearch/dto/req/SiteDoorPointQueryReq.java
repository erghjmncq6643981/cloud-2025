package org.chandler25.elasticsearch.dto.req;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@ApiModel("工厂搜索")
public class SiteDoorPointQueryReq {

	    
	
	@ApiModelProperty(value = "精确搜索关键字 多个以英文半角逗号分隔",example = "")
	private String key;
	
	
	@ApiModelProperty(value = "类型",example = "sdp")
	private String type;
	
	@ApiModelProperty(value = "模糊搜索关键字 多个以英文半角逗号分隔",example = "")
	private String fuzzyKey;
	
	@ApiModelProperty(value = "基于经纬度搜索",example = "lat,lon")
	private GeoPoint geoPoint;
	
	@ApiModelProperty(value = "基于该位置维度信息计算距离",example = "lat")
	private Double calcDistanceLat;
	@ApiModelProperty(value = "基于该位置经度信息计算距离",example = "lon")
	private Double calcDistanceLon;
	
	private String province;
	    
    private String city;
    
    private String district;
    
    private String town;
    
    @ApiModelProperty(value = "分桶聚合维度",example = "province,city,district,town")
	private String aggBucket;
	
    @ApiModelProperty(value = "排序字段",example = "planArrivalTime")
    private String sortItem;
    @ApiModelProperty(value = "排序方式",allowableValues = "ASC,DESC",example = "DESC")
    private String sort;
    @ApiModelProperty(value = "当前页数",example = "1")
    @Min(1) @Max(1000)
    private int page = 1;
    @ApiModelProperty(value = "每页的行数",example = "25")
    @Min(1) @Max(2000)
    private int rows = 25;
    
    @ApiModelProperty(value = "将用于高亮显示的预标记",example = "<span style=\"color:red\">")
    private String preTags;
    
    @ApiModelProperty(value = "用于高亮显示的帖子标签",example = "</span>")
    private String postTags;
	
}
