package com.atguigu.gmall.service;

import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.enums.ProcessStatus;

import java.util.List;

public interface OrderService {

    public String saveOrder(OrderInfo orderInfo);

    public void delTradeCode(String userId);

    public String genTradeCode(String userId);

    public boolean checkTradeCode(String userId,String tradeCodePage);

    public OrderInfo getOrderInfo(String orderId);

    public void updateOrderStatus(String orderId, ProcessStatus processStatus);

    public void sendOrderStatus(String orderId);

    public String initWareOrder(String orderId);

    public List<OrderInfo> getExpiredOrderList();

    public void execExpiredOrder(OrderInfo orderInfo);
}
