package org.chandler25.ai.demo;

import com.mybatisflex.core.query.QueryWrapper;
import org.chandler25.ai.demo.respository.entity.Account;
import org.chandler25.ai.demo.respository.mapper.AccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.chandler25.ai.demo.respository.entity.table.AccountTableDef.ACCOUNT;


/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/4 15:46
 * @version 1.0.0
 * @since 21
 */
@SpringBootTest
public class MybatisFlexTestApplicationTests {
    @Autowired
    private AccountMapper accountMapper;

    @Test
    void contextLoads() {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .select()
                .where(ACCOUNT.AGE.eq(18));
        Account account = accountMapper.selectOneByQuery(queryWrapper);
        System.out.println(account);
    }
}