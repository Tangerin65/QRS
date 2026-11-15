package com.ruoyi.web.controller.common;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.ruoyi.common.core.controller.BaseController;

/**
 * 档案工作台页面入口。沿用现有 common 包，不承载业务写操作。
 */
@Controller
@RequestMapping("/records")
public class RecordsWorkbenchController extends BaseController
{
    @RequiresPermissions("records:workbench:view")
    @GetMapping("/workbench")
    public String workbench()
    {
        return "system/records-workbench";
    }

    @RequiresPermissions("records:register:view")
    @GetMapping("/register")
    public String register()
    {
        return "system/records-register";
    }
}
