package chaindeal.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import chaindeal.dto.Result;
import chaindeal.dto.UserDTO;
import chaindeal.entity.Blog;
import chaindeal.service.IBlogService;
import chaindeal.utils.SystemConstants;
import chaindeal.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author ChainDeal
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;


    /**
     * 发布探店笔记（核心流程：保存数据库 + 推送到所有粉丝的关注收件箱 feed）
     * @param blog 笔记内容
     * @return 发布结果
     */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    /**
     * 点赞/取消点赞笔记（基于 Redis 的 Set 保证一人一赞，SortedSet 存点赞时间）
     * @param id 笔记id
     * @return 操作结果
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    /**
     * 查询我发布的笔记（分页）
     * @param current 页码
     * @return 我的笔记列表
     */
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 查询热门笔记（首页推荐流，按点赞数排序）
     * @param current 页码
     * @return 热门笔记列表
     */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    /**
     * 查询笔记详情（补全作者昵称、头像、当前用户是否已点赞）
     * @param id 笔记id
     * @return 笔记详情
     */
    @GetMapping("/{id}")
    public  Result queryBlogById(@PathVariable("id") Long id) {
        return blogService.queryBlogById(id);
    }

    /**
     * 查询笔记的点赞用户列表（前5名，从 Redis ZSet 按点赞时间倒序取）
     * @param id 笔记id
     * @return 点赞用户信息列表
     */
    @GetMapping("/likes/{id}")
    public  Result queryBlogLikes(@PathVariable("id") Long id) {
        return blogService.queryBlogLikes(id);
    }


    /**
     * 查询某用户发布的笔记（浏览他人主页时用，分页）
     * @param current 页码
     * @param id 目标用户id
     * @return 该用户的笔记列表
     */
    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 查询我关注的用户发布的笔记（关注 feed 流，面试重点：Redis 推模式 + 滚动分页）
     * @param max 上一次返回列表的最后一条笔记的时间戳（游标，用于滚动分页）
     * @param offset 偏移量（同一时间戳下跳过几条，保证不重复）
     * @return 关注列表的笔记（按发布时间倒序）
     */
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(@RequestParam("lastId") Long max,@RequestParam(value ="offset",defaultValue = "0")Integer offset){
        return blogService.queryBlogOfFollow(max,offset);
    }
}
