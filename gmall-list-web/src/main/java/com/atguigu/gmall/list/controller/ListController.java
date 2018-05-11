package com.atguigu.gmall.list.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.*;
import com.atguigu.gmall.service.ListService;
import com.atguigu.gmall.service.SkuManageService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Controller
public class ListController {

    @Reference
    ListService listService;
    @Reference
    SkuManageService skuManageService;

   /* @RequestMapping("list")
    @ResponseBody
    public String list(SkuLsParams skuLsParams){
        SkuLsResult skuLsResult=listService.searchSkuInfoList(skuLsParams);

        return JSON.toJSONString(skuLsResult);
    }*/


    @RequestMapping("list.html")
    public String getList(SkuLsParams skuLsParams,Model model){

        SkuLsResult skuLsResult = listService.searchSkuInfoList(skuLsParams);


        List<BaseAttrInfo> attrList = null;
        if(skuLsParams.getCatalog3Id()!=null){
            attrList=skuManageService.getAttrList(skuLsParams.getCatalog3Id());
        }else{
            List<String> attrValueIdList = skuLsResult.getAttrValueIdList();
            if(attrValueIdList!=null&&attrValueIdList.size()>0){
                attrList=skuManageService.getAttrList(attrValueIdList);
            }
        }

        String urlParam = makeUrlParam(skuLsParams);
        String catalog3Id = skuLsParams.getCatalog3Id();
        String keyword=skuLsParams.getKeyword();
        String[] valueIds = skuLsParams.getValueId();

        List<BaseAttrValueExt> selectedValuelist = new ArrayList<>();

        if(skuLsParams.getValueId()!=null&&attrList!=null){
            for (int i = 0; valueIds!=null&&i< valueIds.length; i++) {
                String selectedValueId = valueIds[i];

                for(Iterator<BaseAttrInfo> iterator=attrList.iterator();iterator.hasNext();){
                    BaseAttrInfo baseAttrInfo = iterator.next();
                    List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
                    for (BaseAttrValue baseAttrValue : attrValueList) {
                        if(selectedValueId.equals(baseAttrValue.getId())){
                            BaseAttrValueExt baseAttrValueExt = new BaseAttrValueExt();
                            baseAttrValueExt.setValueName(baseAttrInfo.getAttrName());
                            baseAttrValueExt.setId(baseAttrValue.getId());
                            baseAttrValueExt.setValueName(baseAttrValue.getValueName());
                            baseAttrValueExt.setAttrId(baseAttrInfo.getId());
                            baseAttrValueExt.setAttrName(baseAttrInfo.getAttrName());
                            String cancelUrlParam = makeUrlParam(skuLsParams);
                            baseAttrValueExt.setCancelUrlParam(cancelUrlParam);

                            selectedValuelist.add(baseAttrValueExt);
                            iterator.remove();
                        }
                    }
                }

            }
        }

        long totalPages = skuLsResult.getTotalPages();
        model.addAttribute("totalPages",totalPages);
        model.addAttribute("pageNo",skuLsParams.getPageNo());


        model.addAttribute("attrList",attrList);
        model.addAttribute("selectedValuelist",selectedValuelist);
        model.addAttribute("keyword",keyword);
        model.addAttribute("skuLsInfoList",skuLsResult.getSkuLsInfoList());
        model.addAttribute("urlParam",urlParam);
        return "list";
    }

    private String makeUrlParam(SkuLsParams skuLsParams){
        String urlParam="";
        if(skuLsParams.getKeyword()!=null){
            urlParam+="keyword="+skuLsParams.getKeyword();
        }
        if(skuLsParams.getCatalog3Id()!=null){
            if(urlParam.length()>0){
                urlParam+="&";
            }

        }
        if(skuLsParams.getValueId()!=null&&skuLsParams.getValueId().length>0){
            for (int i = 0; i < skuLsParams.getValueId().length; i++) {
                String valueId = skuLsParams.getValueId()[i];
                if(urlParam.length()>0){
                    urlParam+="&";
                }
                urlParam+="valuId="+valueId;
            }
        }
        return urlParam;
    }

}
