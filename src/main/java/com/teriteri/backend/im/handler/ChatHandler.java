package com.teriteri.backend.im.handler;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.teriteri.backend.im.IMServer;
import com.teriteri.backend.mapper.ChatDetailedMapper;
import com.teriteri.backend.pojo.ChatDetailed;
import com.teriteri.backend.pojo.IMResponse;
import com.teriteri.backend.service.message.ChatService;
import com.teriteri.backend.service.user.UserService;
import com.teriteri.backend.utils.RedisUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Component
public class ChatHandler {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatDetailedMapper chatDetailedMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private Executor taskExecutor;



    /**
     * 发送消息
     * @param ctx
     * @param tx
     */
    public void send(ChannelHandlerContext ctx, TextWebSocketFrame tx) {
        try {
            ChatDetailed chatDetailed = JSONObject.parseObject(tx.text(), ChatDetailed.class);

            // 从channel中获取当前用户id 封装写库
            Integer user_id = (Integer) ctx.channel().attr(AttributeKey.valueOf("userId")).get();
            chatDetailed.setUserId(user_id);
            chatDetailed.setUserDel(0);
            chatDetailed.setAnotherDel(0);
            chatDetailed.setWithdraw(0);
            chatDetailed.setTime(new Date());
            chatDetailedMapper.insert(chatDetailed);

            redisUtil.zset("chat_detailed_zset:" + user_id + ":" + chatDetailed.getAnotherId(), chatDetailed.getId());
            redisUtil.zset("chat_detailed_zset:" + chatDetailed.getAnotherId() + ":" + user_id, chatDetailed.getId());

            // online = whisper:to:from (anotherId:user_id)
            boolean online = chatService.updateChat(user_id, chatDetailed.getAnotherId());

            // 转发到发送者和接收者的全部channel
            Map<String, Object> map = new HashMap<>();
            map.put("type", "接收");
            map.put("online", online);  // 对方是否在窗口
            map.put("detail", chatDetailed);
            CompletableFuture<Void> chatFuture = CompletableFuture.runAsync(() -> {
                map.put("chat", chatService.getChat(user_id, chatDetailed.getAnotherId()));
            }, taskExecutor);
            CompletableFuture<Void> userFuture = CompletableFuture.runAsync(() -> {
                map.put("user", userService.getUserById(user_id));
            }, taskExecutor);
            chatFuture.join();
            userFuture.join();

            // 发给自己的全部channel
            Set<Channel> from = IMServer.userChannel.get(user_id);
            if (from != null) {
                for (Channel channel : from) {
                    channel.writeAndFlush(IMResponse.message("whisper", map));
                }
            }
            // 发给对方的全部channel
            Set<Channel> to = IMServer.userChannel.get(chatDetailed.getAnotherId());
            if (to != null) {
                for (Channel channel : to) {
                    channel.writeAndFlush(IMResponse.message("whisper", map));
                }
            }

        } catch (Exception e) {
            log.error("发送聊天信息时出错了：", e);
            ctx.channel().writeAndFlush(IMResponse.error("发送消息时出错了 Σ(ﾟдﾟ;)"));
        }
    }

    /**
     * 撤回消息
     * @param ctx
     * @param tx
     */
    public void withdraw(ChannelHandlerContext ctx, TextWebSocketFrame tx) {
        try {
            JSONObject jsonObject = JSONObject.parseObject(tx.text());
            Integer id = jsonObject.getInteger("id");
            Integer user_id = (Integer) ctx.channel().attr(AttributeKey.valueOf("userId")).get();

            // 查询数据库
            ChatDetailed chatDetailed = chatDetailedMapper.selectById(id);
            if (chatDetailed == null) {
                ctx.channel().writeAndFlush(IMResponse.error("消息不存在"));
                return;
            }
            if (!Objects.equals(chatDetailed.getUserId(), user_id)) {
                ctx.channel().writeAndFlush(IMResponse.error("无权撤回此消息"));
                return;
            }
            long diff = System.currentTimeMillis() - chatDetailed.getTime().getTime();
            if (diff > 120000) {
                ctx.channel().writeAndFlush(IMResponse.error("发送时间超过两分钟不能撤回"));
                return;
            }
            // 更新 withdraw 字段
            UpdateWrapper<ChatDetailed> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", id).setSql("withdraw = 1");
            chatDetailedMapper.update(null, updateWrapper);

            // 转发到发送者和接收者的全部channel
            Map<String, Object> map = new HashMap<>();
            map.put("type", "撤回");
            map.put("sendId", chatDetailed.getUserId());
            map.put("acceptId", chatDetailed.getAnotherId());
            map.put("id", id);

            // 发给自己的全部channel
            Set<Channel> from = IMServer.userChannel.get(user_id);
            if (from != null) {
                for (Channel channel : from) {
                    channel.writeAndFlush(IMResponse.message("whisper", map));
                }
            }
            // 发给对方的全部channel
            Set<Channel> to = IMServer.userChannel.get(chatDetailed.getAnotherId());
            if (to != null) {
                for (Channel channel : to) {
                    channel.writeAndFlush(IMResponse.message("whisper", map));
                }
            }

        } catch (Exception e) {
            log.error("撤回聊天信息时出错了：", e);
            ctx.channel().writeAndFlush(IMResponse.error("撤回消息时出错了 Σ(ﾟдﾟ;)"));
        }
    }
}
/*  send()中创建的 map集合有什么用？
 *  ChatHandler 里的 map，是一个给前端的大礼包，里面装了前端重绘界面需要的所有材料
    Map<String, Object> map = new HashMap<>();
    map.put("type", "接收");       // 告诉前端：这是一条收到的新消息
    map.put("online", online);     // 告诉前端：对方现在在不在聊天窗口里？（决定要不要放提示音/红点）
    map.put("detail", chatDetailed); // 核心材料：气泡的内容（谁发的、什么时间、文字是什么）
    map.put("chat", chatService.getChat(...)); // 会话材料：更新左侧列表的排序、最新时间、未读数
    map.put("user", userService.getUserById(user_id)); // 用户材料：发送者的头像、昵称，画气泡旁边的头像
 */
