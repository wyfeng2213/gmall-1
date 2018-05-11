package com.atguigu.gmall.service;

import com.atguigu.gmall.bean.*;

import java.util.List;

public interface SpuManageService {

    public List<SpuInfo> getSpuInfoList(SpuInfo spuInfo);

    public void saveSpuInfo(SpuInfo spuInfo);

    public List<SpuImage> getSpuImageList(String spuId);

    public  List<SpuSaleAttr> getSpuSaleAttrList(String spuId);

    public  List<SpuSaleAttrValue> getSpuSaleAttrValueList(SpuSaleAttrValue spuSaleAttrValue);

    public List<BaseSaleAttr> getBaseSaleAttrList();



}
