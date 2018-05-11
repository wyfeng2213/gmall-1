package com.atguigu.gmall.payment.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.atguigu.gmall.bean.PaymentInfo;
import com.atguigu.gmall.config.ActiveMQUtil;
import com.atguigu.gmall.enums.PaymentStatus;
import com.atguigu.gmall.payment.mapper.PaymentInfoMapper;
import com.atguigu.gmall.service.PaymentService;
import org.apache.activemq.ScheduledMessage;
import org.apache.activemq.command.ActiveMQMapMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tk.mybatis.mapper.entity.Example;

import javax.jms.*;


@Service
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    PaymentInfoMapper paymentInfoMapper;
    @Autowired
    ActiveMQUtil activeMQUtil;
    @Autowired
    AlipayClient alipayClient;

    public void savePaymentInfo(PaymentInfo paymentInfo){
        paymentInfoMapper.insertSelective(paymentInfo);
    }

    public PaymentInfo getPaymentInfo(PaymentInfo paymentInfoQuery){
        PaymentInfo paymentInfo= paymentInfoMapper.selectOne(paymentInfoQuery);
        return  paymentInfo;
    }

    public void updatePaymentInfo(String outTradeNo,PaymentInfo paymentInfo){
        Example example=new Example(PaymentInfo.class);
        example.createCriteria().andEqualTo("outTradeNo",outTradeNo);
        paymentInfoMapper.updateByExampleSelective(paymentInfo,example);
        return   ;
    }

    public void sendPaymentResult(PaymentInfo paymentInfo,String result){
        //发送支付结果
        Connection connection = activeMQUtil.getConnection();
        try {
            connection.start();
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            Queue paymentResultQueue = session.createQueue("PAYMENT_RESULT_QUEUE");
            MessageProducer producer = session.createProducer(paymentResultQueue);
            MapMessage mapMessage= new ActiveMQMapMessage();
            mapMessage.setString("orderId",paymentInfo.getOrderId());
            mapMessage.setString("result",result);
            producer.send(mapMessage);

            session.commit();
            producer.close();
            session.close();
            connection.close();

        } catch (JMSException e) {
            e.printStackTrace();
        }

    }

    public void sendDelayPaymentResultCheck(String outTradeNo,int delaySec,int checkCount){
        //发送支付结果
        Connection connection = activeMQUtil.getConnection();
        try {
            connection.start();
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            Queue paymentResultQueue = session.createQueue("PAYMENT_RESULT_CHECK_QUEUE");
            MessageProducer producer = session.createProducer(paymentResultQueue);
            MapMessage mapMessage= new ActiveMQMapMessage();
            mapMessage.setString("outTradeNo",outTradeNo);
            mapMessage.setInt("delaySec",delaySec);
            mapMessage.setInt("checkCount",checkCount);

            mapMessage.setLongProperty(ScheduledMessage.AMQ_SCHEDULED_DELAY,delaySec*1000);
            producer.send(mapMessage);

            session.commit();
            producer.close();
            session.close();
            connection.close();

        } catch (JMSException e) {
            e.printStackTrace();
        }

    }

    public void closePayment(String orderId){
        Example example=new Example(PaymentInfo.class);
        example.createCriteria().andEqualTo("orderId",orderId);
        PaymentInfo paymentInfo=new PaymentInfo();
        paymentInfo.setPaymentStatus(PaymentStatus.ClOSED);
        paymentInfoMapper.updateByExampleSelective(paymentInfo,example);

    }

    public boolean  checkPayment(PaymentInfo paymentInfoQuery){

        PaymentInfo paymentInfo = getPaymentInfo(paymentInfoQuery);
        if(paymentInfo.getPaymentStatus()== PaymentStatus.PAID||paymentInfo.getPaymentStatus()==PaymentStatus.ClOSED){
            return true;
        }
        System.out.println("初始化支付参数 = "+paymentInfo.getOutTradeNo()   );
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizContent("{" +
                "    \"out_trade_no\":\""+paymentInfo.getOutTradeNo()+"\" "+
                "  }");
        AlipayTradeQueryResponse response = null;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }

        if(response.isSuccess()){

            if("TRADE_SUCCESS".equals(response.getTradeStatus())||"TRADE_FINISHED".equals(response.getTradeStatus())) {
                System.out.println("支付成功！！ = "+paymentInfo.getOutTradeNo()   );
                PaymentInfo paymentInfo4Upt = new PaymentInfo();
                paymentInfo4Upt.setPaymentStatus(PaymentStatus.PAID);
                updatePaymentInfo(paymentInfo.getOutTradeNo(), paymentInfo4Upt);

                sendPaymentResult(paymentInfo,"success");

                return true;
            }else{
                System.out.println("未支付！！ = "+paymentInfo.getOutTradeNo()   );
                return false;
            }
        } else {
            System.out.println("未支付！！ = "+paymentInfo.getOutTradeNo()   );
            return false;
        }
    }


}
