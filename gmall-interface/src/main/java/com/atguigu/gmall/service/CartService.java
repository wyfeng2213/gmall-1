package com.atguigu.gmall.service;

import com.atguigu.gmall.bean.CartInfo;

import java.util.List;

public interface CartService {

    public void addToCart(String skuId,String userId,Integer skuNum);

    public List<CartInfo> getCartList(String userId);

    public List<CartInfo> mergeToCartList(List<CartInfo> cartListFromCookie,String userId);

    public void checkCart(String skuId,String isChecked,String userId);

    public List<CartInfo> getCartCheckedList(String userId);
}
