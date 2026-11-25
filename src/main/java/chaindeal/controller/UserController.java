package chaindeal.controller;


import cn.hutool.core.bean.BeanUtil;
import chaindeal.dto.LoginFormDTO;
import chaindeal.dto.Result;
import chaindeal.dto.UserDTO;
import chaindeal.entity.User;
import chaindeal.entity.UserInfo;
import chaindeal.service.IUserInfoService;
import chaindeal.service.IUserService;
import chaindeal.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author ChainDeal
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     * @param phone 手机号
     * @param session 会话对象，验证码会存到 session 中供登录时校验
     * @return 发送结果
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 生成验证码并保存到 session，正式环境应对接短信服务商发送
        return userService.sendCode(phone,session);
    }

    /**
     * 登录功能（手机号+验证码 / 手机号+密码）
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     * @param session 会话对象
     * @return 登录结果，成功后携带用户信息
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // 校验验证码/密码，成功后把用户信息存入 session 并返回
        return userService.login(loginForm,session);
    }

    /**
     * 登出功能（当前未实现）
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        return Result.fail("功能未完成");
    }

    /**
     * 获取当前登录用户信息（供前端判断登录态、展示头像昵称）
     * @return 当前登录用户的 UserDTO（脱敏，不含密码等敏感字段）
     */
    @GetMapping("/me")
    public Result me(){
        // 从 ThreadLocal 取当前登录用户（由拦截器在请求进入时放入）
        UserDTO user=UserHolder.getUser();
        return Result.ok(user);
    }

    /**
     * 查询用户详情信息（个人资料页，如简介、性别、生日等）
     * @param userId 用户id
     * @return 用户详情，首次查看可能没有详情返回空
     */
    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        // 隐藏数据库内部字段，不返回给前端
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    /**
     * 根据id查询用户基本信息（浏览他人主页/评论区展示作者用）
     * @param userId 用户id
     * @return 脱敏后的用户信息 UserDTO
     */
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        // 查询用户实体
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        // 实体转 DTO，只暴露非敏感字段
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }


    /**
     * 用户签到（基于 Redis BitMap 实现，一个 bit 代表一天）
     * @return 签到结果
     */
    @PutMapping("/sign")
    public Result sign(){
        return userService.sign();
    }

    /**
     * 统计当前用户本月累计签到天数（面试重点：BitMap 位运算 + 连续签到逻辑）
     * @return 本月签到天数
     */
    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }
}
