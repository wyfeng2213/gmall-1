package com.atguigu.gmall.manage.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.*;
import com.atguigu.gmall.config.RedisUtil;
import com.atguigu.gmall.manage.constant.ManageConst;
import com.atguigu.gmall.manage.mapper.*;
import com.atguigu.gmall.service.SkuManageService;
import orestes.bloomfilter.BloomFilter;
import orestes.bloomfilter.CountingBloomFilter;
import orestes.bloomfilter.FilterBuilder;
import org.apache.commons.codec.Charsets;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

    public List<BaseAttrInfo> getAttrList(List attrValueIdList) {
        String attrValueIds = StringUtils.join(attrValueIdList.toArray(), ",");
        List<BaseAttrInfo> baseAttrInfoList = baseAttrInfoMapper.selectAttrInfoListByIds(attrValueIds);
        return baseAttrInfoList;
    }


    public void saveSkuInfo(SkuInfo skuInfo) {
        if (skuInfo.getId() == null || skuInfo.getId().length() == 0) {
            skuInfo.setId(null);
            skuInfoMapper.insertSelective(skuInfo);
        } else {
            skuInfoMapper.updateByPrimaryKeySelective(skuInfo);
        }


        Example example = new Example(SkuImage.class);
        example.createCriteria().andEqualTo("skuId", skuInfo.getId());
        skuImageMapper.deleteByExample(example);

        List<SkuImage> skuImageList = skuInfo.getSkuImageList();
        for (SkuImage skuImage : skuImageList) {
            skuImage.setSkuId(skuInfo.getId());
            if (skuImage.getId() != null && skuImage.getId().length() == 0) {
                skuImage.setId(null);
            }
            skuImageMapper.insertSelective(skuImage);
        }


        Example skuAttrValueExample = new Example(SkuAttrValue.class);
        skuAttrValueExample.createCriteria().andEqualTo("skuId", skuInfo.getId());
        skuAttrValueMapper.deleteByExample(skuAttrValueExample);

        List<SkuAttrValue> skuAttrValueList = skuInfo.getSkuAttrValueList();
        for (SkuAttrValue skuAttrValue : skuAttrValueList) {
            skuAttrValue.setSkuId(skuInfo.getId());
            if (skuAttrValue.getId() != null && skuAttrValue.getId().length() == 0) {
                skuAttrValue.setId(null);
            }
            skuAttrValueMapper.insertSelective(skuAttrValue);
        }


        Example skuSaleAttrValueExample = new Example(SkuSaleAttrValue.class);
        skuSaleAttrValueExample.createCriteria().andEqualTo("skuId", skuInfo.getId());
        skuSaleAttrValueMapper.deleteByExample(skuSaleAttrValueExample);

        List<SkuSaleAttrValue> skuSaleAttrValueList = skuInfo.getSkuSaleAttrValueList();
        for (SkuSaleAttrValue skuSaleAttrValue : skuSaleAttrValueList) {
            skuSaleAttrValue.setSkuId(skuInfo.getId());
            skuSaleAttrValue.setId(null);
            skuSaleAttrValueMapper.insertSelective(skuSaleAttrValue);
        }

    }

    //存储100万个元素
    int count = 1000000;
    //放1000个元素进去
    CountingBloomFilter<String> cbf = new FilterBuilder(count, 0.03).
            buildCountingBloomFilter();
    @Resource
    RedissonClient redissonClient;

    //bloomfilter 过滤器 + redisson分布式锁的实现
    @Override
    public SkuInfo getSkuInfo(String skuId) {
        SkuInfo skuInfo = null;
        Jedis jedis = redisUtil.getJedis();
        String skuInfoKey = ManageConst.SKUKEY_PREFIX + skuId + ManageConst.SKUKEY_SUFFIX;
        //布隆过滤器判断
        if (!cbf.contains(skuInfoKey)) {
            System.out.println("布隆过滤器中不存在 , 非法请求");
            return null;
        }
        // bloomfilter中可能存在 0.3%的误判率 查询redis缓存
        String skuInfoJson = jedis.get(skuInfoKey);
        //如果缓存中没有数据 , 数据库可能有数据
        if (skuInfoJson == null || skuInfoJson.length() == 0) {
            System.err.println(Thread.currentThread().getName() + "缓存未命中");
            RLock rLock = redissonClient.getLock("lock");
            // 获取分布式锁 5分钟过期时间
            rLock.lock(5, TimeUnit.MINUTES);
            try {
                System.err.println(Thread.currentThread().getName() + "获得分布式锁！");
                // 二次判断 如果并发量大 , 排队获取锁的比较多 , 一次查询之后,后面进来直接获取到缓存数据
                skuInfoJson = jedis.get(skuInfoKey);
                if (skuInfoJson != null && skuInfoJson.length() != 0) {
                    skuInfo = JSON.parseObject(skuInfoJson, SkuInfo.class);
                    return skuInfo;
                }
                skuInfo = getSkuInfoFromDB(skuId);
                // 如果数据库中不存在
                if (skuInfo == null) {
                    jedis.setex(skuInfoKey, ManageConst.SKUKEY_TIMEOUT, "empty");
                    return null;
                } else {
                    String skuInfoJsonNew = JSON.toJSONString(skuInfo);
                    // 获取到锁了 skuInfoKey 肯定是有值的
                    jedis.setex(skuInfoKey, ManageConst.SKUKEY_TIMEOUT, skuInfoJsonNew);
                }
                jedis.close();
                return skuInfo;
            } finally {
                // 释放锁
                rLock.unlock();
            }

        } else if (skuInfoJson.equals("empty")) {
            jedis.close();
            return null;
        } else {
            System.err.println(Thread.currentThread().getName() + "缓存已命中！！");
            skuInfo = JSON.parseObject(skuInfoJson, SkuInfo.class);
            jedis.close();
            return skuInfo;
        }
    }


    public SkuInfo getSkuInfoFromDB(String skuId) {
        System.err.println(Thread.currentThread().getName() + "查询数据库！");

        SkuInfo skuInfo = skuInfoMapper.selectByPrimaryKey(skuId);
        if (skuInfo != null) {
            SkuImage skuImageQuery = new SkuImage();
            skuImageQuery.setSkuId(skuId);
            List<SkuImage> skuImageList = skuImageMapper.select(skuImageQuery);
            skuInfo.setSkuImageList(skuImageList);
        }
        if (skuInfo != null) {
            SkuAttrValue skuAttrValue = new SkuAttrValue();
            skuAttrValue.setSkuId(skuId);
            List<SkuAttrValue> skuAttrValueList = skuAttrValueMapper.select(skuAttrValue);

            skuInfo.setSkuAttrValueList(skuAttrValueList);
        }

        if (skuInfo != null) {
            SkuSaleAttrValue skuSaleAttrValue = new SkuSaleAttrValue();
            skuSaleAttrValue.setSkuId(skuId);
            List<SkuSaleAttrValue> skuSaleAttrValueList = skuSaleAttrValueMapper.select(skuSaleAttrValue);
        }
        return skuInfo;
    }


    public List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(String skuId, String spuId) {

        List<SpuSaleAttr> spuSaleAttrList = spuSaleAttrMapper.getSpuSaleAttrListCheckBySku(Long.parseLong(skuId), Long.parseLong(spuId));
        return spuSaleAttrList;

    }

    public List<SkuSaleAttrValue> getSkuSaleAttrValueListBySpu(String spuId) {
        List<SkuSaleAttrValue> skuSaleAttrValueList = skuSaleAttrValueMapper.selectSkuSaleAttrValueListBySpu(Long.parseLong(spuId));
        return skuSaleAttrValueList;
    }


}
