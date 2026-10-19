package com.teriteri.backend.comment;

import com.teriteri.backend.pojo.VideoStats;
import com.teriteri.backend.service.comment.CommentService;
import com.teriteri.backend.utils.RedisUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@SpringBootTest(properties = {"spring.profiles.active=test"})
public class TestComment {
    @Autowired
    private CommentService commentService;

    @Autowired
    private RedisUtil redisUtil;

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
        Set<Object> members = redisUtil.getMembers("video_status:1");
        System.out.println(members);
        List<VideoStats> videoStats = new ArrayList<>();
        redisUtil.getAllList("videoStats", VideoStats.class);
        System.out.println(videoStats);
    }


}
