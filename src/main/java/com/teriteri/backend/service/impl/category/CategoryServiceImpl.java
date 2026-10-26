package com.teriteri.backend.service.impl.category;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.teriteri.backend.mapper.CategoryMapper;
import com.teriteri.backend.pojo.CustomResponse;
import com.teriteri.backend.pojo.dto.CategoryDTO;
import com.teriteri.backend.pojo.Category;
import com.teriteri.backend.service.category.CategoryService;
import com.teriteri.backend.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static com.teriteri.backend.utils.ConstantsUtil.MC_ID_LIST;

@Slf4j
@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private Executor taskExecutor;

    /**
     * 获取全部分区数据
     * @return CustomResponse对象
     */
    @Override
    public CustomResponse getAll() {
        CustomResponse customResponse = new CustomResponse();
        List<CategoryDTO> sortedCategories = new ArrayList<>();

        // 尝试从redis中获取数据
        try {
            sortedCategories = redisUtil.getAllList("categoryList", CategoryDTO.class);
            if (sortedCategories.size() != 0) {
                customResponse.setData(sortedCategories);
                return customResponse;
            }
            log.warn("redis中获取不到分区数据");
        } catch (Exception e) {
            log.error("获取redis分区数据失败", e);
        }

        // 将分区表一次全部查询出来，再在内存执行处理逻辑，可以减少数据库的IO
        QueryWrapper<Category> queryWrapper = new QueryWrapper<>();
        List<Category> list = categoryMapper.selectList(queryWrapper);

        // 开一个临时整合map
        Map<String, CategoryDTO> categoryDTOMap = new HashMap<>();

        for (Category category : list) {
            String mcId = category.getMcId();
            String scId = category.getScId();
            String mcName = category.getMcName();
            String scName = category.getScName();
            String descr = category.getDescr();
            List<String> rcmTag = new ArrayList<>();
            if (category.getRcmTag() != null) {
                String[] strings = category.getRcmTag().split("\n");    // 将每个标签切出来组成列表封装
                rcmTag = Arrays.asList(strings);
            }

            // 先将主分类和空的子分类列表整合到map中
            if (!categoryDTOMap.containsKey(mcId)) {
                CategoryDTO categoryDTO = new CategoryDTO();
                categoryDTO.setMcId(mcId);
                categoryDTO.setMcName(mcName);
                categoryDTO.setScList(new ArrayList<>());
                categoryDTOMap.put(mcId, categoryDTO);
            }

            // 把子分类整合到map的子分类列表里
            Map<String, Object> scMap = new HashMap<>();
            scMap.put("mcId", mcId);
            scMap.put("scId", scId);
            scMap.put("scName", scName);
            scMap.put("descr", descr);
            scMap.put("rcmTag", rcmTag);
            categoryDTOMap.get(mcId).getScList().add(scMap);
        }

        /**
         * 那如果我改变了 这里的顺序，前端还能正常接收吗？另外，前端应该有一个映射表对吗，因为这里都是 拼音/英文，页面显示则是 汉字 ？
         * 1. MC_ID_LIST 顺序可以进行更改，前端正常接收，会改变页面布局
         * 2. 前端并没有 映射表，而是 后端返回的 CategoryDTO中有 mcId/scId，以及 mcName/scName：中文显示名
         */

        // MC_ID_LIST：按指定序列排序
        for (String mcId : MC_ID_LIST) {
            if (categoryDTOMap.containsKey(mcId)) {
                sortedCategories.add(categoryDTOMap.get(mcId));
            }
        }
        // 将分类添加到redis缓存中
        try {
            /**
             * 1. 必须先删除，才能进行添加吗 ?
             *    默认情况下，是这样的；因为 setAllList()的底层是 rightPushAll()，Redis的 Push 是 追加，而非覆盖
             * 2. setAllList()默认设置 ttl是多少 ?
             *    它没有设置 ttl，因此是 永久缓存
             */
            redisUtil.delValue("categoryList");
            redisUtil.setAllList("categoryList", sortedCategories);
        } catch (Exception e) {
            log.error("存储redis分类列表失败", e);
        }
        customResponse.setData(sortedCategories);
        return customResponse;
    }

    /**
     * 根据id查询对应分区信息
     * @param mcId 主分区ID
     * @param scId 子分区ID
     * @return Category类信息
     */
    /**
    *  我觉得，若这个key只存在 1h，那其实没什么用，不如 直接查 mysql，而取消 redis查询 ?
    *  1. 实际上，并非这样，它的调用频率比我想的高；
    *  2. 另外，getCategoryById() 是 每条视频调一次，而非 每个分类调一次
     */
    @Override
    public Category getCategoryById(String mcId, String scId) {
        // 从redis中获取最新数据
        Category category = redisUtil.getObject("category:" + mcId + ":" + scId, Category.class);
        // 如果redis中没有数据，就从mysql中获取并更新到redis
        if (category == null) {
            QueryWrapper<Category> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("mc_id", mcId).eq("sc_id", scId);
            category = categoryMapper.selectOne(queryWrapper);
            if (category == null) {
                return new Category();    // 如果不存在则返回空
            }

            Category finalCategory = category;
            CompletableFuture.runAsync(() -> {
                redisUtil.setExObjectValue("category:" + mcId + ":" + scId, finalCategory);  // 默认存活1小时
            }, taskExecutor);
        }
        return category;
    }
}