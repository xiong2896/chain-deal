package chaindeal.controller;


import chaindeal.dto.Result;
import chaindeal.service.IFollowService;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author ChainDeal
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    private final IFollowService iFollowService;

    public FollowController(IFollowService iFollowService) {
        this.iFollowService = iFollowService;
    }

    /**
     * 关注/取关用户（基于 Redis 的 Set 存储关注列表，key 为 follow:用户id）
     * @param followUserId 被关注/取关的用户id
     * @param isFollow true=关注 false=取关
     * @return 操作结果
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow")Boolean isFollow){
        return iFollowService.follow(followUserId,isFollow);
    }

    /**
     * 查询当前用户是否已关注某用户（详情页展示"已关注/关注"按钮状态）
     * @param followUserId 目标用户id
     * @return 是否已关注
     */
    @GetMapping("/or/not/{id}")
    public Result isfollow(@PathVariable("id") Long followUserId){
        return iFollowService.isfollow(followUserId);
    }


    /**
     * 查询共同关注（面试重点：Redis 两个 Set 求交集 SINTER）
     * @param id 目标用户id
     * @return 当前用户与该用户共同关注的人列表
     */
    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long id){
        return iFollowService.followCommons(id);
    }

}
