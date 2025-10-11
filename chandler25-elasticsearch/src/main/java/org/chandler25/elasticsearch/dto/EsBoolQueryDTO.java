package org.chandler25.elasticsearch.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@ApiModel("es查询请求DTO")
public class EsBoolQueryDTO {

    public enum Type{
        MUST,SHOULD,MUST_NOT;
    }

    @ApiModelProperty(value = "类型",allowableValues = "MUST,SHOULD")
    @NotBlank(message = "请指定查询类型")
    private Type type;
    @ApiModelProperty("查询")
    private List<EsBoolQueryDTO> boolQueries = new ArrayList<>();
    @ApiModelProperty("模糊搜索")
    private List<EsWildcardQueryDTO> wildcardQueries = new ArrayList<>();
    @ApiModelProperty("范围查询")
    private List<EsRangeQueryDTO> rangeQueries = new ArrayList<>();
    @ApiModelProperty("条件查询")
    private List<EsMatchQueryDTO> matchQueries = new ArrayList<>();
    @ApiModelProperty("存在查询")
    private List<EsExistQueryDTO> existQueries = new ArrayList<>();
	@ApiModelProperty("数组查询")
    private List<EsTermsQueryDTO> termsQueries = new ArrayList<>();
	@ApiModelProperty("短语条件查询")
    private List<EsMatchPhraseQueryDTO> matchPhraseQueries = new ArrayList<>();
	@ApiModelProperty("短语条件查询")
    private List<EsMatchPhrasePrefixQueryDTO> matchPhrasePrefixQueries = new ArrayList<>();
	
	@ApiModelProperty("多字段查询")
    private List<EsMultiQueryDTO> multiQueries = new ArrayList<>();
	
	@ApiModelProperty("经纬度查询")
    private EsGeoDistanceQueryDTO geoQuery = null;
	


}
