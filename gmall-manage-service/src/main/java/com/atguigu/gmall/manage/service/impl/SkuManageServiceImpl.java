package com.atguigu.gmall.manage.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.*;
import com.atguigu.gmall.config.RedisUtil;
import com.atguigu.gmall.manage.constant.ManageConst;
import com.atguigu.gmall.manage.mapper.*;
import com.atguigu.gmall.service.SkuManageService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;
import tk.mybatis.mapper.entity.Example;

import java.util.List;

@Service
public class SkuManageServiceImpl implements SkuManageService {

    @Autowired
    BaseAttrInfoMapper baseAttrInfoMapper;
    @Autowired
    SkuInfoMapper skuInfoMapper;
    @Autowired
    SkuImageMapper skuImageMapper;
    @Autowired
    SkuAttrValueMapper skuAttrValueMapper;
    @Autowired
    SkuSaleAttrValueMapper skuSaleAttrValueMapper;
    @Autowired
    SpuSaleAttrMapper spuSaleAttrMapper;
    @Autowired
    RedisUtil redisUtil;

    @Override
    public List<BaseAttrInfo> getAttrList(String catalog3_id) {

        List<BaseAttrInfo> baseAttrInfoList = baseAttrInfoMapper.selectAttrInfoList(Long.parseLong(catalog3_id));
        return baseAttrInfoList;

    }

    public List<BaseAttrInfo> getAttrList(List attrValueIdList){
        String attrValueIds = StringUtils.join(attrValueIdList.toArray(), ",");
        List<BaseAttrInfo> baseAttrInfoList = baseAttrInfoMapper.selectAttrInfoListByIds(attrValueIds);
        return baseAttrInfoList;
    }



    public void saveSkuInfo(SkuInfo skuInfo){
        if(skuInfo.getId()==null||skuInfo.getId().length()==0){
            skuInfo.setId(null);
            skuInfoMapper.insertSelective(skuInfo);
        }else {
            skuInfoMapper.updateByPrimaryKeySelective(skuInfo);
        }


        Example example=new Example(SkuImage.class);
        example.createCriteria().andEqualTo("skuId",skuInfo.getId());
        skuImageMapper.deleteByExample(example);

        List<SkuImage> skuImageList = skuInfo.getSkuImageList();
        for (SkuImage skuImage : skuImageList) {
            skuImage.setSkuId(skuInfo.getId());
            if(skuImage.getId()!=null&&skuImage.getId().length()==0) {
                skuImage.setId(null);
            }
            skuImageMapper.insertSelective(skuImage);
        }


        Example skuAttrValueExample=new Example(SkuAttrValue.class);
        skuAttrValueExample.createCriteria().andEqualTo("skuId",skuInfo.getId());
        skuAttrValueMapper.deleteByExample(skuAttrValueExample);

        List<SkuAttrValue> skuAttrValueList = skuInfo.getSkuAttrValueList();
        for (SkuAttrValue skuAttrValue : skuAttrValueList) {
            skuAttrValue.setSkuId(skuInfo.getId());
            if(skuAttrValue.getId()!=null&&skuAttrValue.getId().length()==0) {
                skuAttrValue.setId(null);
            }
            skuAttrValueMapper.insertSelective(skuAttrValue);
        }


        Example skuSaleAttrValueExample=new Example(SkuSaleAttrValue.class);
        skuSaleAttrValueExample.createCriteria().andEqualTo("skuId",skuInfo.getId());
        skuSaleAttrValueMapper.deleteByExample(skuSaleAttrValueExample);

        List<SkuSaleAttrValue> skuSaleAttrValueList = skuInfo.getSkuSaleAttrValueList();
        for (SkuSaleAttrValue skuSaleAttrValue : skuSaleAttrValueList) {
            skuSaleAttrValue.setSkuId(skuInfo.getId());
            skuSaleAttrValue.setId(null);
            skuSaleAttrValueMapper.insertSelective(skuSaleAttrValue);
        }

    }


    public SkuInfo getSkuInfo(String skuId){
        SkuInfo skuInfo=null;
        try {
            Jedis jedis=redisUtil.getJedis();
            String skuInfoKey = ManageConst.SKUKEY_PREFIX+skuId+ManageConst.SKUKEY_SUFFIX;
            String skuInfoJson = jedis.get(skuInfoKey);

            if(skuInfoJson==null || skuInfoJson.length()==0){
                System.err.println(Thread.currentThread().getName()+"缓存未命中");
                String skuLockKey=ManageConst.SKUKEY_PREFIX+skuId+ManageConst.SKULOCK_SUFFIX;
                String lock=jedis.set(skuLockKey,"OK","NX","PX",ManageConst.SKULOCK_EXPIRE_PX);

                if("OK".equals(lock)){
                    System.err.println(Thread.currentThread().getName()+"获得分布式锁！");
                    skuInfo = getSkuInfoFromDB(skuId);
                    if(skuInfo==null){
                        jedis.setex(skuInfoKey,ManageConst.SKUKEY_TIMEOUT,"empty");
                        return null;
                    }

                    String skuInfoJsonNew = JSON.toJSONString(skuInfo);
                    jedis.setex(skuInfoKey, ManageConst.SKUKEY_TIMEOUT, skuInfoJsonNew);
                    jedis.close();
                    return skuInfo;
                }else{
                    System.err.println(Thread.currentThread().getName()+"未获得分布式锁，开始自旋！");
                    Thread.sleep(1000);
                    jedis.close();
                    return   getSkuInfo(  skuId);
                }
            }else if(skuInfoJson.equals("empty")){
                return null;
            }else{
                System.err.println(Thread.currentThread().getName()+"缓存已命中！！");
                skuInfo = JSON.parseObject(skuInfoJson, SkuInfo.class);
                jedis.close();
                return skuInfo;
            }
        }catch (InterruptedException e) {
            e.printStackTrace();
        }

        return getSkuInfoFromDB(skuId);

    }

    public SkuInfo getSkuInfoFromDB(String skuId){
        System.err.println(Thread.currentThread().getName()+"查询数据库！");

        SkuInfo skuInfo=skuInfoMapper.selectByPrimaryKey(skuId);
        if(skuInfo!=null) {
            SkuImage skuImageQuery = new SkuImage();
            skuImageQuery.setSkuId(skuId);
            List<SkuImage> skuImageList = skuImageMapper.select(skuImageQuery);
            skuInfo.setSkuImageList(skuImageList);
        }
        if(skuInfo!=null) {
            SkuAttrValue skuAttrValue = new SkuAttrValue();
            skuAttrValue.setSkuId(skuId);
            List<SkuAttrValue> skuAttrValueList = skuAttrValueMapper.select(skuAttrValue);

            skuInfo.setSkuAttrValueList(skuAttrValueList);
        }

        if(skuInfo!=null) {
            SkuSaleAttrValue skuSaleAttrValue = new SkuSaleAttrValue();
            skuSaleAttrValue.setSkuId(skuId);
            List<SkuSaleAttrValue> skuSaleAttrValueList = skuSaleAttrValueMapper.select(skuSaleAttrValue);
        }
        return skuInfo;
    }



    public  List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(String skuId,String spuId) {

        List<SpuSaleAttr> spuSaleAttrList = spuSaleAttrMapper.getSpuSaleAttrListCheckBySku(Long.parseLong(skuId), Long.parseLong(spuId));
        return spuSaleAttrList;

    }

    public List<SkuSaleAttrValue> getSkuSaleAttrValueListBySpu(String spuId){
        List<SkuSaleAttrValue> skuSaleAttrValueList = skuSaleAttrValueMapper.selectSkuSaleAttrValueListBySpu(Long.parseLong(spuId));
        return skuSaleAttrValueList;
    }



}
