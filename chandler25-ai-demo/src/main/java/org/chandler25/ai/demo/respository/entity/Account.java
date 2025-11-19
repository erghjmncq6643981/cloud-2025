package org.chandler25.ai.demo.respository.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.sql.Date;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/4 15:44
 * @version 1.0.0
 * @since 21
 */
@Data
@Table("tb_account")
public class Account {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private String userName;
    private Integer age;
    private Date birthday;
}