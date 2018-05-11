package com.atguigu.gmall.order.mq;

import com.atguigu.gmall.enums.ProcessStatus;
import com.atguigu.gmall.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import javax.jms.MapMessage;

@Component
public class OrderConsumer {


    @Autowired
    OrderService orderService;

    @JmsListener(destination = "PAYMENT_RESULT_QUEUE",containerFactory = "jmsQueueListener")
    public void consumePaymentResult(MapMessage mapMessage) throws JMSException {

        String orderId = mapMessage.getString("orderId");
        String result = mapMessage.getString("result");
        System.out.println("result = " + result);
        System.out.println("orderId = " + orderId);
        if("success".equals(result)){

            orderService.updateOrderStatus(  orderId,   ProcessStatus.PAID);

            orderService.sendOrderStatus(orderId);

            orderService.updateOrderStatus(  orderId,   ProcessStatus.NOTIFIED_WARE);
        }

    }


    @JmsListener(destination = "SKU_DEDUCT_QUEUE",containerFactory = "jmsQueueListener")
    public void consumeDeductQueue(MapMessage mapMessage) throws JMSException {
        String orderId = mapMessage.getString("orderId");
        String status = mapMessage.getString("status");
        if("DEDUCTED".equals(status)){
            orderService.updateOrderStatus(  orderId,   ProcessStatus.WAITING_DELEVER);
        }else{
            orderService.updateOrderStatus(  orderId,   ProcessStatus.STOCK_EXCEPTION);
        }

    }

}
