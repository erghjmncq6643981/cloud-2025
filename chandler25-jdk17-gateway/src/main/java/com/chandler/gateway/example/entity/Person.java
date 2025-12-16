package com.chandler.gateway.example.entity;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2019/5/17下午2:02
 * @since 1.8
 */
@Getter
@Setter
@Builder
@ApiModel(value = "测试对象",description = "这个类定义了用户的所有属性")
public class Person {
    @ApiModelProperty(value = "姓名")
    private String name;
    @ApiModelProperty(value = "性别")
    private String age;
    @ApiModelProperty(value = "性别")
    private String sex;
}
