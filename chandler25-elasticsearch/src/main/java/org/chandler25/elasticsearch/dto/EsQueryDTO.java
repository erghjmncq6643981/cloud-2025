package org.chandler25.elasticsearch.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

@Data
@ApiModel("es查询请求DTO")
public class EsQueryDTO {

    @ApiModelProperty("查询条件")
    private EsBoolQueryDTO boolQuery;
    @ApiModelProperty("排序")
    private List<EsSortQueryDTO> sortQueries;
    @ApiModelProperty("位置距离排序")
    private List<EsGeoSortQueryDTO> geoSortQueries;
    
    @ApiModelProperty("桶聚合字段名称")
    private List<EsAggFieldQueryDTO> aggBuckets;
    
    @ApiModelProperty(value = "页码，默认0")
    @Min(value = 0,message = "页数必须大于0")
    private int page = 0;
    @ApiModelProperty(value = "页长，默认25，最大支持500")
    @Max(value = 500,message = "每页最多展示500条数据")
    private int pageSize = 25;
    
    @ApiModelProperty(value = "将用于高亮显示的预标记",example = "<span style=\"color:red\">")
    private String preTags;
    
    @ApiModelProperty(value = "用于高亮显示的帖子标签",example = "</span>")
    private String postTags;

}
