package chaindeal.service;

import chaindeal.entity.UserInfo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户详情服务接口
 *
 * <p>对应用户详情表 tb_user_info（昵称、性别、生日、介绍、头像、地址等）。
 * 目前仅使用 MyBatis-Plus 提供的通用 CRUD，无自定义业务方法。
 *
 * @author ChainDeal
 * @since 2021-12-24
 */
public interface IUserInfoService extends IService<UserInfo> {

}
