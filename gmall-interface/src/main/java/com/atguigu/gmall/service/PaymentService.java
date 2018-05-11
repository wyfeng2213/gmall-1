package com.atguigu.gmall.service;

import com.atguigu.gmall.bean.PaymentInfo;

public interface PaymentService {

    public void savePaymentInfo(PaymentInfo paymentInfo);

    public PaymentInfo getPaymentInfo(PaymentInfo paymentInfoQuery);

    public void updatePaymentInfo(String outTradeNo,PaymentInfo paymentInfo);

    public void sendPaymentResult(PaymentInfo paymentInfo,String result);

    public boolean  checkPayment(PaymentInfo paymentInfoQuery);

    public void sendDelayPaymentResultCheck(String outTradeNo,int delaySec,int checkCount);

    public void closePayment(String orderId);

}
