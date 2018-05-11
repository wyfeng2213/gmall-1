package com.atguigu.gmall.cart.service.impl;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.bean.CartInfo;
import com.atguigu.gmall.bean.SkuInfo;
import com.atguigu.gmall.cart.constant.CartConst;
import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.config.RedisUtil;
import com.atguigu.gmall.service.CartService;
import com.atguigu.gmall.service.SkuManageService;
import jdk.nashorn.internal.runtime.arrays.IteratorAction;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;

import java.util.*;

import static sun.security.krb5.Confounder.intValue;


@Service
public class CartServiceImpl implements CartService {

    @Autowired
    CartInfoMapper cartInfoMapper;
    @Reference
    SkuManageService skuManageService;
    @Autowired
    RedisUtil redisUtil;


    //已经登录的前提下，往数据库和缓存中添加
    public void addToCart(String skuId,String userId,Integer skuNum){

        //cart中是否有该商品
        CartInfo cartInfoQuery = new CartInfo();
        cartInfoQuery.setSkuId(skuId);
        cartInfoQuery.setUserId(userId);
        CartInfo cartInfoExist = cartInfoMapper.selectOne(cartInfoQuery);

        //如果存在该商品，更新数量
        if(cartInfoExist!=null){
            cartInfoExist.setSkuNum(cartInfoExist.getSkuNum()+skuNum);
            cartInfoMapper.updateByPrimaryKeySelective(cartInfoExist);
        }else{
            //不存在，购物车添加该商品
            SkuInfo skuInfo = skuManageService.getSkuInfo(skuId);
            CartInfo cartInfo = new CartInfo();

            cartInfo.setSkuId(skuId);
            cartInfo.setSkuNum(skuNum);
            cartInfo.setCartPrice(skuInfo.getPrice());
            cartInfo.setSkuName(skuInfo.getSkuName());
            cartInfo.setImgUrl(skuInfo.getSkuDefaultImg());
            cartInfo.setUserId(userId);


            cartInfoMapper.insertSelective(cartInfo);
            cartInfoExist=cartInfo;
        }

        //更新缓存
        //数据结构采用hash
        String userCartKey= CartConst.USER_KEY_PREFIX+userId+CartConst.USER_CART_KEY_SUFFIX;
        Jedis jedis = redisUtil.getJedis();
        String cartJson = JSON.toJSONString(cartInfoExist);
        jedis.hset(userCartKey,skuId,cartJson);

        //更新购物车过期时间
        String userInfoKey = CartConst.USER_KEY_PREFIX+userId+CartConst.USERINFOKEY_SUFFIX;
        Long ttl = jedis.ttl(userInfoKey);
        jedis.expire(userCartKey,ttl.intValue());
        jedis.close();

    }


    public List<CartInfo> getCartList(String userId){
        //先从缓存中查询
        Jedis jedis = redisUtil.getJedis();
        List<String> skuJsonList = jedis.hvals(CartConst.USER_KEY_PREFIX + userId + CartConst.USER_CART_KEY_SUFFIX);
        List<CartInfo> cartInfoList = new ArrayList<>();


        if(skuJsonList!=null&&skuJsonList.size()>0){
            //序列化
            for (String skuJson : skuJsonList) {
                CartInfo cartInfo = JSON.parseObject(skuJson, CartInfo.class);
                cartInfoList.add(cartInfo);
            }
            //缓存中取出来的是无序的，需要用id进行排序
            cartInfoList.sort(new Comparator<CartInfo>() {
                @Override
                public int compare(CartInfo o1, CartInfo o2) {
                    int compare = Long.compare(Long.parseLong(o2.getId()), Long.parseLong(o1.getId()));
                    return compare;
                }
            });

            return cartInfoList;
        }else{
            //缓存中没有从数据库中查询
            cartInfoList=loadCartCache(userId);
            return cartInfoList;
        }

    }

   //从数据库中查询
    public List<CartInfo> loadCartCache(String userId){
        List<CartInfo> cartInfoList=cartInfoMapper.getCartListWithCurPrice(Long.parseLong(userId));
        if(cartInfoList==null||cartInfoList.size()==0){
            return null;
        }
        //放到缓存中
        Jedis jedis = redisUtil.getJedis();
        Map<String,String> carMap=new HashMap<>();

        for (CartInfo cartInfo : cartInfoList) {
            String cartJson = JSON.toJSONString(cartInfo);
            carMap.put(cartInfo.getSkuId(),cartJson);
        }

        String userCartKey=CartConst.USER_KEY_PREFIX+userId+CartConst.USER_CART_KEY_SUFFIX;
        jedis.hmset(userCartKey,carMap);

        return cartInfoList;
    }

    //合并购物车
    public List<CartInfo> mergeToCartList(List<CartInfo> cartListFromCookie,String userId){
        List<CartInfo> cartListDB = cartInfoMapper.getCartListWithCurPrice(Long.parseLong(userId));

        for (CartInfo cartInfoCk : cartListFromCookie) {
            boolean isMatch=false;
            for (CartInfo cartInfoDB : cartListDB) {
                if(cartInfoDB.getSkuId().equals(cartInfoCk.getSkuId())){
                    cartInfoDB.setSkuNum(cartInfoDB.getSkuNum()+cartInfoCk.getSkuNum());
                    cartInfoMapper.updateByPrimaryKeySelective(cartInfoDB);
                    isMatch=true;
                }
                if(!isMatch){
                    cartInfoCk.setUserId(userId);
                    cartInfoMapper.insertSelective(cartInfoCk);
                }

            }
        }

        List<CartInfo> cartInfoList = loadCartCache(userId);

        for(CartInfo cartInfo:cartListFromCookie){
            if(cartInfo.getIsChecked().equals("1")){
                checkCart(cartInfo.getSkuId(),"1",userId);
            }
        }

        return cartInfoList;

    }


    public void checkCart(String skuId,String isChecked,String userId){
        //更新购物车中的isChecked标志  redis
        Jedis jedis = redisUtil.getJedis();
        String userCartKey=CartConst.USER_KEY_PREFIX+userId+CartConst.USER_CART_KEY_SUFFIX;
        String cartJson = jedis.hget(userCartKey, skuId);

        CartInfo cartInfo = JSON.parseObject(cartJson, CartInfo.class);
        cartInfo.setIsChecked(isChecked);
        String cartCheckedJson = JSON.toJSONString(cartInfo);
        jedis.hset(userCartKey,userId,cartCheckedJson);

        //2.新增到已选中购物车
        String userCheckedKey=CartConst.USER_KEY_PREFIX+userId+CartConst.USER_CHECKED_KEY_SUFFIX;
        if(isChecked.equals("1")){
            jedis.hset(userCheckedKey,skuId,cartCheckedJson);
        }else {
            jedis.hdel(userCheckedKey,skuId);
        }
        jedis.close();

    }

    public List<CartInfo> getCartCheckedList(String userId){
        //从缓存中获取
        Jedis jedis = redisUtil.getJedis();
        String userCheckedKey=CartConst.USER_KEY_PREFIX+userId+CartConst.USER_CHECKED_KEY_SUFFIX;
        List<String> cartCheckedJsonList = jedis.hvals(userCheckedKey);
        List<CartInfo> cartCheckedList = new ArrayList<>(cartCheckedJsonList.size());
        for (String cartJson : cartCheckedJsonList) {
            CartInfo cartInfo = JSON.parseObject(cartJson,CartInfo.class);
            cartCheckedList.add(cartInfo);
        }
        return cartCheckedList;
    }

}
