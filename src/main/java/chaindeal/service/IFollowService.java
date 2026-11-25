package chaindeal.service;

import chaindeal.dto.Result;
import chaindeal.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author ChainDeal
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注/取关用户（数据库 tb_follow 落库 + Redis Set 存关注列表，key = follow:用户id）
     * @param followUserId 目标用户id
     * @param isFollow true关注 / false取关
     * @return 操作结果
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * 判断当前用户是否关注了目标用户（Redis Set 判断成员存在）
     * @param followUserId 目标用户id
     * @return 是否已关注
     */
    Result isfollow(Long followUserId);

    /**
     * 查询共同关注（面试重点：Redis SINTER 求两个 Set 交集）
     * @param id 目标用户id
     * @return 共同关注用户列表
     */
    Result followCommons(Long id);
}
