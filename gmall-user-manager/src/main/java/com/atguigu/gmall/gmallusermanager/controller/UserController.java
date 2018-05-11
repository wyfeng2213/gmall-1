package com.atguigu.gmall.gmallusermanager.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.bean.UserInfo;
import com.atguigu.gmall.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class UserController {

    @Reference
    UserService userService;

    @RequestMapping("userList")
    public ResponseEntity<List<UserInfo>> userList(){
        List<UserInfo> userInfoListAll = userService.getUserInfoListAll();
        return  ResponseEntity.ok(userInfoListAll);
    }

    @RequestMapping(value = "/user" ,method = RequestMethod.POST)
    public    ResponseEntity<Void> add(UserInfo userInfo){ ;

        userService.addUser(userInfo);
        System.out.println("userInfo = " + userInfo);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/user" ,method = RequestMethod.PUT)
    public    ResponseEntity<Void> update(UserInfo userInfo){
        userService.updateUser(userInfo.getId(),userInfo);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/user" ,method = RequestMethod.DELETE)
    public    ResponseEntity<Void> delete(UserInfo userInfo){
        //   userService.delete(userInfo);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/user" ,method = RequestMethod.GET)
    public    ResponseEntity<UserInfo> getUserInfo(UserInfo userInfoQuery){
        //UserInfo userInfo = userService.getUserInfo(userInfoQuery);
        return ResponseEntity.ok().body(new UserInfo());
    }
}
