package com.lotus.bsystem.controller;




import com.lotus.bserver.entity.Test;
import com.lotus.bserver.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 唐磊
 * @since 2022-09-22
 */
@RestController
@RequestMapping("/test")
public class TestController {

    @Autowired
    TestService testService;

    @GetMapping("/test")
    public String test(){
       List<Test> list = testService.list();

       String s = new String();

        for (Test test : list) {
            s += test.getId();
            s+= test.getName();
        }

       return s;
    }

}
