package com.atguigu.gmall.cart.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.CartInfo;
import com.atguigu.gmall.bean.SkuInfo;
import com.atguigu.gmall.config.CookieUtil;
import com.atguigu.gmall.service.SkuManageService;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

@Component
public class CartCookieHandler {

    @Reference
    SkuManageService skuManageService;

    private String COOKIE_CART_NAME="CART";

    private int COOKIE_CART_MAXAGE=7*24*3600;

    //未登录状态加到购物车
    public void addToCart(HttpServletRequest request, HttpServletResponse response,String skuId,Integer skuNum){
        //1、先查询cart中是否有该商品
        String cartJson = CookieUtil.getCookieValue(request, COOKIE_CART_NAME, true);
        List<CartInfo> cartInfoList = new ArrayList<>();
        boolean ifExist=false;
        if(cartJson!=null){
            cartInfoList=JSON.parseArray(cartJson,CartInfo.class);
            for (CartInfo cartInfo : cartInfoList) {
                if(cartInfo.getSkuId().equals(skuId)){
                    cartInfo.setSkuNum(cartInfo.getSkuNum()+skuNum);
                    cartInfo.setSkuPrice(cartInfo.getCartPrice());
                    ifExist=true;
                    break;
                }
            }
        }

        //购物车里没有对应的商品或者没有购物车
        if(!ifExist){
            //把商品信息取出来 ，新增到购物车
            SkuInfo skuInfo = skuManageService.getSkuInfo(skuId);
            CartInfo cartInfo = new CartInfo();

            cartInfo.setSkuId(skuId);
            cartInfo.setCartPrice(skuInfo.getPrice());
            cartInfo.setSkuPrice(skuInfo.getPrice());
            cartInfo.setSkuName(skuInfo.getSkuName());
            cartInfo.setImgUrl(skuInfo.getSkuDefaultImg());

            //cartInfo.setUserId(userId);
            cartInfo.setSkuNum(skuNum);
            cartInfoList.add(cartInfo);
        }

        //把购物车写入cookie
        String newCartJson = JSON.toJSONString(cartInfoList);
        CookieUtil.setCookie(request,response,COOKIE_CART_NAME,newCartJson,COOKIE_CART_MAXAGE,true);

    }

    public List<CartInfo> getCartList(HttpServletRequest request){
        String cartJson = CookieUtil.getCookieValue(request, COOKIE_CART_NAME, true);
        List<CartInfo> cartInfoList = JSON.parseArray(cartJson, CartInfo.class);
        return cartInfoList;
    }

    public void deleteCartList(HttpServletRequest request,HttpServletResponse response){
        CookieUtil.deleteCookie(request,response,COOKIE_CART_NAME);
    }

    public void checkCart(HttpServletRequest request,HttpServletResponse response,String skuId,String isChecked){
        //1.取出所有购物车中的商品
        List<CartInfo> cartList = getCartList(request);

        //2.循环比较skuId
        for (CartInfo cartInfo : cartList) {
            if (cartInfo.getIsChecked().equals(skuId)) {
                //3.如果匹配上isChecked
                cartInfo.setIsChecked(isChecked);
            }
        }

        //4.保存进cookie
        String newCartJson = JSON.toJSONString(cartList);
        CookieUtil.setCookie(request,response,COOKIE_CART_NAME,newCartJson,COOKIE_CART_MAXAGE,true);
    }

}
