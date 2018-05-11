package com.atguigu.gmall.order.service.impl;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.OrderDetail;
import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.config.ActiveMQUtil;
import com.atguigu.gmall.config.RedisUtil;
import com.atguigu.gmall.enums.ProcessStatus;
import com.atguigu.gmall.order.mapper.OrderDetailMapper;
import com.atguigu.gmall.order.mapper.OrderInfoMapper;
import com.atguigu.gmall.service.OrderService;
import com.atguigu.gmall.service.PaymentService;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import redis.clients.jedis.Jedis;
import tk.mybatis.mapper.entity.Example;

import javax.jms.*;
import java.util.*;
import javax.jms.Queue;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    OrderInfoMapper orderInfoMapper;
    @Autowired
    OrderDetailMapper orderDetailMapper;
    @Autowired
    RedisUtil redisUtil;
    @Autowired
    ActiveMQUtil activeMQUtil;
    @Reference
    PaymentService paymentService;


    public String saveOrder(OrderInfo orderInfo){
        orderInfo.setCreateTime(new Date());

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE,1);

        orderInfo.setExpireTime(calendar.getTime());

        String outTradeNo="ATGUIGU"+System.currentTimeMillis()+""+new Random().nextInt(1000);
        orderInfo.setOutTradeNo(outTradeNo);

        orderInfoMapper.insertSelective(orderInfo);

        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();

        for (OrderDetail orderDetail : orderDetailList) {
            orderDetail.setOrderId(orderInfo.getId());
            orderDetailMapper.insertSelective(orderDetail);
        }
        String orderId=orderInfo.getId();

        return orderId;
    }

    public OrderInfo getOrderInfo(String orderId){
        OrderInfo orderInfo = orderInfoMapper.selectByPrimaryKey(orderId);

        OrderDetail orderDetailQuery = new OrderDetail();
        orderDetailQuery.setOrderId(orderId);

        List<OrderDetail> orderDetailList = orderDetailMapper.select(orderDetailQuery);
        orderInfo.setOrderDetailList(orderDetailList);

        return orderInfo;
    }


    public String genTradeCode(String userId){
        Jedis jedis = redisUtil.getJedis();
        String tradeNoKey="user:"+userId+":tradeCode";
        String tradeCode= UUID.randomUUID().toString();
        jedis.setex(tradeNoKey,10*60,tradeCode);
        jedis.close();
        return tradeCode;
    }

    public boolean checkTradeCode(String userId,String tradeCodePage){
        Jedis jedis = redisUtil.getJedis();
        String tradeNokey="user:"+userId+":tradeCode";
        String tradeCode = jedis.get(tradeNokey);
        jedis.close();
        if(tradeCode!=null&&tradeCode.equals(tradeCodePage)){
            return true;
        }else {
            return false;
        }
    }



    public void delTradeCode(String userId){
        Jedis jedis = redisUtil.getJedis();
        String tradeNoKey="user:"+userId+":tradeCode";
        jedis.del(tradeNoKey);
        jedis.close();
    }

    public void updateOrderStatus(String orderId, ProcessStatus processStatus){
        OrderInfo orderInfo =new OrderInfo();

        orderInfo.setProcessStatus(processStatus);

        orderInfo.setOrderStatus(processStatus.getOrderStatus());

        orderInfo.setId(orderId);
        orderInfoMapper.updateByPrimaryKeySelective(orderInfo);

    }

    public void sendOrderStatus(String orderId){
        String orderJson = initWareOrder(orderId);
        Connection connection = activeMQUtil.getConnection();

        try {
            connection.start();
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            Queue orderStatusQueue = session.createQueue("ORDER_RESULT_QUEUE");
            MessageProducer producer = session.createProducer(orderStatusQueue);

            TextMessage textMessage=new ActiveMQTextMessage();
            textMessage.setText(orderJson);

            producer.send(textMessage);

            session.commit();

            session.close();
            producer.close();
            connection.close();


        } catch (JMSException e) {
            e.printStackTrace();
        }


    }

    public String initWareOrder(String orderId){
        OrderInfo orderInfo = getOrderInfo(  orderId);
        Map map=new HashMap();
        map.put("orderId",orderId);
        map.put("consignee", orderInfo.getConsignee());
        map.put("consigneeTel",orderInfo.getConsigneeTel());
        map.put("orderComment",orderInfo.getOrderComment());
        map.put("orderBody",orderInfo.getTradeBody());
        map.put("deliveryAddress",orderInfo.getDeliveryAddress());
        map.put("paymentWay","2");

        List detailList=new ArrayList();
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            Map  detailMap =new HashMap();
            detailMap.put("skuId",orderDetail.getSkuId());
            detailMap.put("skuName",orderDetail.getSkuName());
            detailMap.put("skuNum",orderDetail.getSkuNum());

            detailList.add(detailMap);
        }

        map.put("details",detailList);

        String jsonString = JSON.toJSONString(map);

        return jsonString;

    }

    public List<OrderInfo> getExpiredOrderList(){
        Example example=new Example(OrderInfo.class);
        example.createCriteria().andLessThan("expireTime",new Date()).andEqualTo("processStatus",ProcessStatus.UNPAID);


        List<OrderInfo> orderInfos = orderInfoMapper.selectByExample(example);
        return orderInfos;
    }

    @Async
    public void execExpiredOrder(OrderInfo orderInfo){
        updateOrderStatus(orderInfo.getId(), ProcessStatus.CLOSED);
        paymentService.closePayment(orderInfo.getId());
    }

}
