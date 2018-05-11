package com.atguigu.gmall.service;

import com.atguigu.gmall.bean.BaseAttrInfo;
import com.atguigu.gmall.bean.SkuInfo;
import com.atguigu.gmall.bean.SkuSaleAttrValue;
import com.atguigu.gmall.bean.SpuSaleAttr;

import java.util.List;

public interface SkuManageService {

    public List<BaseAttrInfo> getAttrList(String catalog3Id);

    public void saveSkuInfo(SkuInfo skuInfo);

    public SkuInfo getSkuInfo(String skuId);

    public List<SpuSaleAttr>  getSpuSaleAttrListCheckBySku(String skuId,String spuId);

    public List<SkuSaleAttrValue> getSkuSaleAttrValueListBySpu(String spuId);

    public List<BaseAttrInfo> getAttrList(List attrValueIdList);
}
