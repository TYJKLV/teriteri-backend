package com.teriteri.backend.service.impl.video;

import com.teriteri.backend.mapper.VideoMapper;
import com.teriteri.backend.mapper.VideoStatsMapper;
import com.teriteri.backend.pojo.Video;
import com.teriteri.backend.pojo.VideoStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 视频投稿的数据库写入。
 *
 * 单独抽成一个 Bean，是为了让 @Transactional 走 Spring 代理：
 * VideoUploadServiceImpl.addVideo 里是异步 lambda 调用，直接调用 this.mergeChunks()
 * 不会经过代理，事务注解不会生效。
 */
@Service
public class VideoPersistService {

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private VideoStatsMapper videoStatsMapper;

    /**
     * 在同一个事务里插入 video 和 video_stats。
     * 任意一条失败都会回滚，不会出现"video 有行、video_stats 没行"。
     *
     * @param video 已填好除 vid 外字段的视频对象
     * @return 插入后的 VideoStats（vid 已回填）
     */
    @Transactional(rollbackFor = Exception.class)
    public VideoStats saveVideoAndStats(Video video) {
        if (videoMapper.insert(video) <= 0) {
            throw new RuntimeException("插入 video 失败");
        }

        VideoStats videoStats = new VideoStats(video.getVid(), 0, 0, 0, 0, 0, 0, 0, 0);
        if (videoStatsMapper.insert(videoStats) <= 0) {
            throw new RuntimeException("插入 video_stats 失败");
        }
        return videoStats;
    }
}
