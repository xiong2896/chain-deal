package chaindeal.service;

import chaindeal.dto.Result;
import chaindeal.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author ChainDeal
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 查询热门笔记（首页推荐流，按点赞数倒序分页）
     * @param current 页码
     * @return 热门笔记列表
     */
    Result queryHotBlog(Integer current);

    /**
     * 查询笔记详情（补全作者昵称/头像、判断当前用户是否点赞）
     * @param id 笔记id
     * @return 笔记详情
     */
    Result queryBlogById(Long id);

    /**
     * 点赞/取消点赞（面试重点：Redis Set 保证一人一赞 + 判断是否已赞）
     * @param id 笔记id
     * @return 操作结果
     */
    Result likeBlog(Long id);

    /**
     * 查询笔记的点赞用户（前5名，Redis ZSet 按点赞时间倒序）
     * @param id 笔记id
     * @return 点赞用户信息列表
     */
    Result queryBlogLikes(Long id);

    /**
     * 发布探店笔记（面试重点：Feed 推模式——笔记写入DB后推送到所有粉丝的收件箱ZSet）
     * @param blog 笔记内容
     * @return 发布结果
     */
    Result saveBlog(Blog blog);

    /**
     * 查询关注用户发布的笔记（Feed 滚动分页，面试重点：ZSet + 游标分页避免分页数据重复/丢失）
     * @param max 滚动分页游标（上次最后一条笔记的时间戳）
     * @param offset 偏移量（同一时间戳跳过的条数）
     * @return 关注笔记列表
     */
    Result queryBlogOfFollow(Long max, Integer offset);

}
