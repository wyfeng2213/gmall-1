package com.atguigu.gmall.order.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.service.UserService;
import com.atguigu.gmall.bean.UserAddress;
import jdk.nashorn.internal.parser.JSONParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Controller
public class OrderController {

    @Reference
    UserService userService;

    @RequestMapping("/trade")
    @ResponseBody
    public String trade(HttpServletRequest httpServletRequest){

        String userId = httpServletRequest.getParameter("userId");
        List<UserAddress> userAddressList = userService.getUserAddressList(userId);
        String s = JSON.toJSONString(userAddressList);
        return s;
    }
}
