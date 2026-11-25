package chaindeal.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import chaindeal.dto.Result;
import chaindeal.dto.UserDTO;
import chaindeal.entity.Follow;
import chaindeal.mapper.FollowMapper;
import chaindeal.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import chaindeal.service.IUserService;
import chaindeal.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author ChainDeal
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService iUserService;

    /**
     * 关注/取关（面试重点：数据库落库 + Redis Set 缓存关注列表）
     * 关注：tb_follow 插入记录 + Redis Set(key=follow:用户id) add 目标用户
     * 取关：删除记录 + Set remove 目标用户
     * @param followUserId 目标用户id
     * @param isFollow true关注/false取关
     * @return 操作结果
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取登录用户
        Long userId = UserHolder.getUser().getId();
        //当前用户的关注列表key（Redis Set，存关注的所有用户id）
        String key = "follow:" + userId;
        //判断是关注还是取关
        if (isFollow) {
            //关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                //把关注用户的id，放入redis的set集合
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            //取关，删除用户
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId).eq("follow_user_id", followUserId));
            if(isSuccess) {
                //把关注的用户id从集合中移除
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 判断当前用户是否已关注目标用户（查数据库，也可用 Redis Set 判断）
     * @param followUserId 目标用户id
     * @return 是否已关注
     */
    @Override
    public Result isfollow(Long followUserId) {
        //获取登录用户
        Long userId = UserHolder.getUser().getId();
        //查询关注关系记录是否存在
        Integer count=query().eq("user_id",userId).eq("follow_user_id",followUserId).count();
        //判断
        return Result.ok(count>0);
    }

    /**
     * 查询共同关注（面试重点：Redis SINTER 求两个 Set 交集）
     * 把当前用户和目标用户的关注列表Set求交集，得到共同关注的人id，再查库补全用户信息
     * @param id 目标用户id
     * @return 共同关注的用户列表
     */
    @Override
    public Result followCommons(Long id) {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //当前用户的关注列表
        String key = "follow:" + userId;
        //目标用户的关注列表
        String key2 = "follow:" + id;
        //求交集（SINTER）得到共同关注的人id
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if(intersect==null||intersect.isEmpty()){
            //无交集，返回空
            return Result.ok(Collections.emptyList());
        }
        //解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //查询用户（转成脱敏DTO返回）
        List<UserDTO> users = iUserService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
