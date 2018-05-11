package com.atguigu.gmall.gmallusermanager.service.impl;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.UserAddress;
import com.atguigu.gmall.bean.UserInfo;
import com.atguigu.gmall.config.RedisUtil;
import com.atguigu.gmall.gmallusermanager.mapper.UserAddressMapper;
import com.atguigu.gmall.gmallusermanager.mapper.UserInfoMapper;
import com.atguigu.gmall.service.UserService;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;
import tk.mybatis.mapper.entity.Example;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    UserInfoMapper userInfoMapper;
    
    @Autowired
    RedisUtil redisUtil;

    @Autowired
    UserAddressMapper userAddressMapper;

    private String userKey_prefix="user:";
    private String userinfoKey_suffix=":info";
    private int userinfo_expire=60*60;

    public List<UserInfo> getUserInfoListAll(){
        List<UserInfo> userInfos = userInfoMapper.selectAll();
        UserInfo userinfoQuery =new UserInfo();
        userinfoQuery.setLoginName("chenge");
        List<UserInfo> userInfos1 = userInfoMapper.select(userinfoQuery);

        Example example=new Example(UserInfo.class);
        example.createCriteria().andLike("name" ,"张%").andEqualTo("id","3");
        List<UserInfo> userInfos2 = userInfoMapper.selectByExample(example);

        return userInfos2;
    }

    public void addUser(UserInfo userInfo){
        userInfoMapper.insert(userInfo);
    }


    public void updateUser(String id,UserInfo userInfo){
        Example example=new Example(UserInfo.class);
        example.createCriteria().andLike("name" ,"张%").andEqualTo("id","3");
        userInfoMapper.updateByExampleSelective(userInfo,example);

    }

    public List<UserAddress> getUserAddressList(String userId){

        UserAddress userAddress=new UserAddress();
        userAddress.setUserId(userId);

        List<UserAddress> userAddressList = userAddressMapper.select(userAddress);

        return  userAddressList;
    }

    public UserInfo login(UserInfo userInfo){
        String md5Hex = DigestUtils.md5Hex(userInfo.getPasswd());
        userInfo.setPasswd(md5Hex);

        UserInfo userInfoResult = userInfoMapper.selectOne(userInfo);
        if(userInfoResult!=null){
            Jedis jedis = redisUtil.getJedis();
            String userJson = JSON.toJSONString(userInfoResult);
            jedis.setex(userKey_prefix+userInfoResult.getId()+userinfoKey_suffix,userinfo_expire,userJson);
            jedis.close();
            return userInfoResult;
        }
        return null;
    }

    public  UserInfo verify(String userId){
        Jedis jedis = redisUtil.getJedis();
        String userKey=userKey_prefix+userId+userinfoKey_suffix;
        String userJson = jedis.get(userKey);
        jedis.expire(userKey,userinfo_expire);
        jedis.close();
        if(userJson!=null){
            UserInfo userInfo = JSON.parseObject(userJson, UserInfo.class);
            return userInfo;
        }
        return null;
    }


}
