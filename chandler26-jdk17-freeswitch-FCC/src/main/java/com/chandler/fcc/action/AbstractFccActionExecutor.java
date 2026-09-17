package com.chandler.fcc.action;

import com.chandler.fcc.fcc.client.FccClient;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractFccActionExecutor implements IFccAction {

    @Autowired
    @Getter
    private FccClient fccClient;
}
