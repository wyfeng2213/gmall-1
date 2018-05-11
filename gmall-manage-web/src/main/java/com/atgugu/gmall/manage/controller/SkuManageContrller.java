package com.atgugu.gmall.manage.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.bean.BaseAttrInfo;
import com.atguigu.gmall.bean.SkuInfo;
import com.atguigu.gmall.bean.SkuLsInfo;
import com.atguigu.gmall.service.ListService;
import com.atguigu.gmall.service.ManageService;
import com.atguigu.gmall.service.SkuManageService;
import org.apache.commons.beanutils.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

@Controller
public class SkuManageContrller {

    @Reference
    SkuManageService skuManageService;
    @Reference
    ListService listService;

    @RequestMapping("attrInfoList")
    @ResponseBody
    public List<BaseAttrInfo> getAttrList(@RequestParam Map<String,String> map){
        String catalog3Id =   map.get("catalog3Id") ;
        List<BaseAttrInfo> attrList = skuManageService.getAttrList(catalog3Id);
        return attrList;
    }

    @RequestMapping(value = "saveSku",method = RequestMethod.POST)
    @ResponseBody
    public String saveSkuInfo(SkuInfo skuInfo){
        skuManageService.saveSkuInfo(skuInfo);
        return "success";
    }


    @RequestMapping(value="onSale",method = RequestMethod.GET)
    public void onSale(String skuId){
        SkuInfo skuInfo = skuManageService.getSkuInfo(skuId);
        SkuLsInfo skuLsInfo = new SkuLsInfo();

        try {
            BeanUtils.copyProperties(skuLsInfo,skuInfo);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        listService.saveSkuInfo(skuLsInfo);

    }






}
