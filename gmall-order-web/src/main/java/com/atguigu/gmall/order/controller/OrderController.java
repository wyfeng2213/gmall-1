package com.atguigu.gmall.order.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.*;
import com.atguigu.gmall.config.LoginRequire;
import com.atguigu.gmall.enums.OrderStatus;
import com.atguigu.gmall.enums.ProcessStatus;
import com.atguigu.gmall.service.*;
import com.atguigu.gmall.util.HttpClientUtil;
import jdk.nashorn.internal.parser.JSONParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

@Controller
public class OrderController {

    @Reference
    UserService userService;
    @Reference
    CartService cartService;
    @Reference
    OrderService orderService;
    @Reference
    SkuManageService skuManageService;

    @RequestMapping("/trade")
    @LoginRequire
    public String trade(HttpServletRequest httpServletRequest){

        String userId = (String)httpServletRequest.getAttribute("userId");
        //查询用户地址信息
        List<UserAddress> userAddressList = userService.getUserAddressList(userId);
        httpServletRequest.setAttribute("userAddressList",userAddressList);

        //查询商品清单
        List<CartInfo> cartCheckedList = cartService.getCartCheckedList(userId);

        List<OrderDetail> orderDetaileList=new ArrayList<>(cartCheckedList.size());

        for (CartInfo cartInfo : cartCheckedList) {
            OrderDetail orderDetail = new OrderDetail();
            orderDetail.setSkuId(cartInfo.getSkuId());
            orderDetail.setSkuName(cartInfo.getSkuName());
            orderDetail.setImgUrl(cartInfo.getImgUrl());
            orderDetail.setSkuNum(cartInfo.getSkuNum());
            orderDetail.setOrderPrice(cartInfo.getCartPrice());
            orderDetaileList.add(orderDetail);
        }
        httpServletRequest.setAttribute("orderDetaileList",orderDetaileList);
        OrderInfo orderInfo=new OrderInfo();
        orderInfo.setOrderDetailList(orderDetaileList);
        orderInfo.sumTotalAmount();

        httpServletRequest.setAttribute("totalAmount",orderInfo.getTotalAmount());

        String tradeCode = orderService.genTradeCode(userId);

        httpServletRequest.setAttribute("tradeCode",tradeCode);

        return "trade";
    }

    @RequestMapping(value = "submitOrder",method = RequestMethod.POST)
    @LoginRequire
    public String submitOrder(OrderInfo orderInfo,HttpServletRequest request){
        String userId=(String)request.getAttribute("userId");
        String tradeCodePage = request.getParameter("tradeCode");
        boolean existsTradeCode = orderService.checkTradeCode(userId, tradeCodePage);
        if(!existsTradeCode){
            request.setAttribute("errMsg","请勿重复提交订单！");
            return "tradeFail";
        }

        //初始化参数
        orderInfo.setOrderStatus(OrderStatus.UNPAID);
        orderInfo.setProcessStatus(ProcessStatus.UNPAID);
        orderInfo.setUserId(userId);
        orderInfo.sumTotalAmount();
        //校验  验价
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            SkuInfo skuInfo = skuManageService.getSkuInfo(orderDetail.getSkuId());
            if(!skuInfo.getPrice().equals(orderDetail.getOrderPrice())){
                request.setAttribute("errMsg","您选择的商品可能存在价格变动，请重新下单");
                return "tradeFail";
            }
            boolean hasStock = checkStock(orderDetail.getSkuId(), orderDetail.getSkuNum());
            if(!hasStock){
                request.setAttribute("errMsg","您的商品【"+orderDetail.getSkuName()+"】库存不足，请重新下单。。");
                return "tradeFail";
            }


        }

        //保存
       String orderId= orderService.saveOrder(orderInfo);
        orderService.delTradeCode(userId);

        //重定向
        return "redirect://payment.gmall.com/index?orderId="+orderId;


    }


    private boolean checkStock(String skuId,Integer skuNum){
        String result= HttpClientUtil.doGet("http://www.gware.com/hasStock?skuId=" + skuId + "&num=" + skuNum);
        if("1".equals(result)){
            return true;
        }
        return false;
    }






}
