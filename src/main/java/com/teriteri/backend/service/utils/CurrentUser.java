package com.teriteri.backend.service.utils;

import com.teriteri.backend.mapper.UserMapper;
import com.teriteri.backend.pojo.User;
import com.teriteri.backend.service.impl.user.UserDetailsImpl;
import com.teriteri.backend.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUser {

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private UserMapper userMapper;

    /**
     * 获取当前登录用户的uid，也是JWT认证的一环
     * @return 当前登录用户的uid
     */
    public Integer getUserId() {
        UsernamePasswordAuthenticationToken authenticationToken =
                (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl loginUser = (UserDetailsImpl) authenticationToken.getPrincipal();
        User suser = loginUser.getUser();   // 过滤器放入上下文的用户快照（来自 user:{uid} 缓存），只用作获取 uid
        return suser.getUid();
    }

    /**
     * 判断当前用户是否管理员
     * @return  是否管理员 true/false
     */
    public Boolean isAdmin() {
        Integer uid = getUserId();
        User user = redisUtil.getObject("user:" + uid, User.class);
        if (user == null) {
            user = userMapper.selectById(uid);
            if (user != null) {
                redisUtil.setExObjectValue("user:" + uid, user);   // 回填，默认存活 1 小时
            }
        }
        return (user != null && (user.getRole() == 1 || user.getRole() == 2));
    }
}
