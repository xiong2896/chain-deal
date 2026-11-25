package chaindeal.service.impl;

import chaindeal.entity.UserInfo;
import chaindeal.mapper.UserInfoMapper;
import chaindeal.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author ChainDeal
 * @since 2021-12-24
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {
    // 用户详情服务实现：使用 MyBatis-Plus 通用 CRUD，无自定义业务
}
