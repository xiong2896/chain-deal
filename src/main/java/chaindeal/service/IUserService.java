package chaindeal.service;

import com.baomidou.mybatisplus.extension.service.IService;
import chaindeal.dto.LoginFormDTO;
import chaindeal.dto.Result;
import chaindeal.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author ChainDeal
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    /**
     * 发送手机短信验证码
     * 生成6位随机验证码保存到 session（key=code），校验同一手机号60秒内不能重复发送（session 存储发送时间）
     * @param phone 手机号
     * @param session 会话对象（验证码 + 发送时间存这里）
     * @return 发送结果
     */
    Result sendCode(String phone, HttpSession session);

    /**
     * 用户登录（面试重点）
     * 支持两种方式：验证码登录（手机号+验证码，默认）或密码登录（手机号+密码）
     * 校验通过后把脱敏用户信息(UserDTO)存入 session，并返回用户信息给前端
     * @param loginForm 登录参数
     * @param session 会话对象
     * @return 登录结果，成功携带用户信息
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    /**
     * 用户签到（基于 Redis BitMap，key = sign:年月:userId，位下标 = 当月第几天）
     * @return 签到结果
     */
    Result sign();

    /**
     * 统计当月累计签到天数（面试重点：BitMap 位运算 + 连续签到天数计算）
     * @return 当月签到天数（含今日）
     */
    Result signCount();
}
