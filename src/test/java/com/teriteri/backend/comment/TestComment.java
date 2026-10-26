package com.teriteri.backend.comment;

import com.teriteri.backend.config.DruidConfig;
import com.teriteri.backend.pojo.VideoStats;
import com.teriteri.backend.service.comment.CommentService;
import com.teriteri.backend.utils.RedisUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;
import java.util.*;
import java.util.stream.Collectors;

@SpringBootTest(properties = {"spring.profiles.active=test", "server.port=9191"})
public class TestComment {
    @Autowired
    private CommentService commentService;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private DruidConfig druidConfig;

    @Test
    public void testAddComment() {
        Integer vid = 1;
        Integer uid = 1;
        boolean isRoot = true;
        Integer fatherId = null;
        Integer toUserId = null;
        String content = "这是第一条根级评论";
//        boolean result = commentService.sendComment(vid, uid, isRoot, fatherId, toUserId, content);
//        System.out.println(result);
    }

    @Test
    public void test(){
        System.out.println( Runtime.getRuntime().availableProcessors() == 16);
    }

    @Test
    public void test1(){
        DataSource druid1 = druidConfig.druid();
        DataSource druid2 = druidConfig.druid();
        System.out.println(druid1 == druid2);
        System.out.println(druid1.toString());
        System.out.println(druid2.toString());
    }

}
