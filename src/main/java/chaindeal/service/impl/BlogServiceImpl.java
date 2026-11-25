package chaindeal.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import chaindeal.dto.Result;
import chaindeal.dto.ScrollResult;
import chaindeal.dto.UserDTO;
import chaindeal.entity.Blog;
import chaindeal.entity.Follow;
import chaindeal.entity.User;
import chaindeal.mapper.BlogMapper;
import chaindeal.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import chaindeal.service.IFollowService;
import chaindeal.service.IUserService;
import chaindeal.utils.SystemConstants;
import chaindeal.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static chaindeal.utils.RedisConstants.BLOG_LIKED_KEY;
import static chaindeal.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author ChainDeal
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;

    /**
     * 查询热门笔记（首页"推荐"feed，按点赞数倒序分页）
     * @param current 页码
     * @return 热门笔记列表
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据点赞数倒序分页查询
        Page<Blog> page =query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 遍历补全每条笔记的作者昵称/头像 + 当前用户是否已点赞
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 查询笔记详情（补全作者信息 + 当前用户是否点赞）
     * @param id 笔记id
     * @return 笔记详情
     */
    @Override
    public Result queryBlogById(Long id) {
        //1.查询blog
        Blog blog = getById(id);
        if(blog==null){
            return Result.fail("笔记不存在");
        }
        //2.查询bolg有关的用户（昵称、头像）
        queryBlogUser(blog);
        //3.查询blog是否被点赞（当前用户）
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    //判断当前登录用户是否已点赞该笔记（查询 Redis ZSet 中是否有该用户，有则 score 不为 null）
    private void isBlogLiked(Blog blog) {
        //获取登录用户（未登录直接返回，不加点赞状态）
        UserDTO user=UserHolder.getUser();
        if(user==null){
            return;
        }
        Long userId=UserHolder.getUser().getId();
        //判断当前用户是否已经点赞
        String key=BLOG_LIKED_KEY+blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score!=null);
    }

    /**
     * 点赞/取消点赞（面试重点：Redis ZSet 实现"一人一赞" + 点赞时间排序）
     * ZSet 的 member=用户id，score=点赞时间戳。点赞时数据库+1并写入ZSet，取消时数据库-1并移除
     * 用 score 是否为空判断是否已赞（避免用 Set 还要额外查询）
     * @param id 笔记id
     * @return 操作结果
     */
    @Override
    public Result likeBlog(Long id) {
        //1.获取登录用户
        Long userId=UserHolder.getUser().getId();
        //2.判断当前用户是否已经点赞（ZSet 中查不到 score=null 说明没赞过）
        String key=BLOG_LIKED_KEY+id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if(score==null){
            //3.如果未点赞，可以点赞
            //3.1.数据库点赞数+1
            boolean isSuccess = update().setSql("liked=liked+1").eq("id", id).update();
            //3.2.保存用户到Redis的ZSet集合（score=当前时间戳，用于后续按点赞时间排序）
            if(isSuccess){
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        }else {
            //4.如果已经点赞，取消点赞
            //4.1.数据库点赞数-1
            update().setSql("liked=liked-1").eq("id", id).update();
            //4.2.把用户从redis移除
            stringRedisTemplate.opsForZSet().remove(key,userId.toString());
        }
        return Result.ok();
    }

    /**
     * 查询笔记点赞用户Top5（面试重点：ZSet range 按 score【点赞时间】倒序取前5）
     * 取到5个用户id后用 ORDER BY FIELD 保持与 Redis 返回顺序一致
     * @param id 笔记id
     * @return 点赞用户信息列表
     */
    @Override
    public Result queryBlogLikes(Long id) {
        String key=BLOG_LIKED_KEY+id;
        //1.查询top5的点赞用户 zrange key 0 4（score 升序=点赞时间从早到晚，这里拿到的就是前5个点赞的人）
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5==null||top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //2.解析出其中用户Id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",",ids);
        //3.根据用户id查询用户（ORDER BY FIELD 保证查询结果顺序和Redis返回一致）
        List<UserDTO> userDTOS = userService.query()
                .in("id",ids).last("ORDER BY FIELD(id,"+idStr+")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    /**
     * 发布探店笔记（面试重点：Feed 流"推模式"）
     * 笔记写入数据库后，把笔记id推送到【所有粉丝】的关注收件箱 ZSet（key=feed:粉丝id，score=时间戳）
     * 粉丝刷新关注流时直接从自己的收件箱取，性能高（写扩散）
     * @param blog 笔记内容
     * @return 发布结果，成功返回笔记id
     */
    @Override
    public Result saveBlog(Blog blog) {
        //获得登录用户
        UserDTO user=UserHolder.getUser();
        blog.setUserId(user.getId());
        //保存探店笔记
        boolean isSuccess = save(blog);
        if(!isSuccess){
            return Result.fail("新增笔记失败");
        }
        //查询笔记作者的所有粉丝（tb_follow 中 follow_user_id=作者id 的记录，userId字段是粉丝id）
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        //推送笔记id给粉丝（每个粉丝一个收件箱）
        for(Follow follow:follows){
            //获取粉丝id
            Long userId=follow.getUserId();
            //把笔记id推入粉丝收件箱，score=当前时间戳（用于滚动分页游标）
            String key="feed:"+userId;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        //返回id
        return Result.ok(blog.getId());
    }

    /**
     * 查询关注用户发布的笔记（面试重点：Feed 滚动分页，解决传统分页在动态数据下的重复/漏数据问题）
     * ZREVRANGEBYSCORE 按分数【时间戳】从大到小取2条，max=上次最后一条的时间戳做游标
     * 同一时间戳可能有多个笔记，用 offset 记录偏移避免重复
     * @param max 游标（上次返回最后一条笔记的时间戳）
     * @param offset 偏移量（同一时间戳下跳过几条）
     * @return 笔记列表 + 下一次查询用的游标(minTime)和偏移量(offset)
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //查询收件箱 zrevrangebyscore key Max Min LIMIT offset count（按时间倒序取2条）
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate
                .opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        //非空判断
        if(typedTuples==null||typedTuples.isEmpty()){
            return Result.ok();
        }
        //解析数据，blogId,minTime(时间戳),offset----------------------滚动分页查询
        ArrayList<Object> ids = new ArrayList<>(typedTuples.size());
        long minTime=0;
        int os=1;
        for(ZSetOperations.TypedTuple<String> tuple:typedTuples){
            //获取id
            ids.add(Long.valueOf(tuple.getValue()));
            //获取分数-----------------score就是发布笔记的时间戳
            long time=tuple.getScore().longValue();
            //统计同一时间戳的笔记数量：下次从这个偏移继续取，防止重复
            if(time<minTime){
                os++;
            }else {
                minTime=time;
                os=1;
            }
        }
        //根据id查询blog（ORDER BY FIELD 保持与Redis返回顺序一致）
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        for(Blog blog:blogs) {
            //2.查询bolg有关的用户
            queryBlogUser(blog);
            //3.查询blog是否被点赞
            isBlogLiked(blog);
        }
        //封装并返回（把游标minTime和偏移os返回给前端，下次请求带上）
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);
        return Result.ok(r);
    }


    //补全笔记作者信息（昵称、头像）
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
    
}
