package org.chandler25.elasticsearch.dto.resp;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
@ApiModel("响应结果")
public class PageResultRes<T> {

    @ApiModelProperty("数据列表")
    private List<T> results;
    @ApiModelProperty("总条数")
    private Long total;
    @ApiModelProperty("当前页码")
    private Integer page;
    @ApiModelProperty("总页数")
    private Long totalPage;
    @ApiModelProperty("每页条数")
    private Integer pageSize;

    public void calcTotalPage() {
        if(this.total%this.pageSize==0){
            this.totalPage = this.total/this.pageSize;
        } else{
            this.totalPage = (this.total/this.pageSize)+1;
        }
    }

}
