package com.teriteri.backend.service.user;

import com.teriteri.backend.pojo.CustomResponse;

import java.io.IOException;
import java.util.Map;

public interface UserAccountService {
    /**
     * 用户注册
     * @param username 账号
     * @param password 密码
     * @param confirmedPassword 确认密码
     * @return CustomResponse对象
     */
    CustomResponse register(String username, String password, String confirmedPassword) throws IOException;

    /**
     * 登录
     * @param username  账号
     * @param password  密码
     * @param isAdmin   true：管理员登录  false：用户登录
     * @return CustomResponse对象
     */
    CustomResponse login(String username, String password , boolean isAdmin);

    /**
     * 获取用户个人信息
     * @return CustomResponse对象
     */
    CustomResponse personalInfo();

    /**
     * 获取管理员个人信息
     * @return CustomResponse对象
     */
    CustomResponse adminPersonalInfo();

    /**
     * 退出登录，清空redis中相关用户登录认证
     */
    void logout();

    /**
     * 管理员退出登录，清空redis中相关管理员登录认证
     */
    void adminLogout();

    /**
     * 重置密码
     * @param pw    旧密码
     * @param npw   新密码
     * @return  响应对象
     */
    CustomResponse updatePassword(String pw, String npw);
}
