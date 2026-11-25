package chaindeal.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import chaindeal.dto.LoginFormDTO;
import chaindeal.dto.Result;
import chaindeal.dto.UserDTO;
import chaindeal.entity.User;
import chaindeal.mapper.UserMapper;
import chaindeal.service.IUserService;
import chaindeal.utils.RegexUtils;
import chaindeal.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;


import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static chaindeal.utils.RedisConstants.*;
import static chaindeal.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author ChainDeal
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送短信验证码（面试重点：Redis 存验证码 + 过期时间）
     * key = login:code:手机号，value = 6位随机验证码，有效期2分钟
     * @param phone 手机号
     * @param session 会话对象（课程用 Redis 替代 session 存储）
     * @return 发送结果
     */
    @Override//告诉是实现方法
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        //符合，生成验证码//随机生成6位的
        String code = /*"123456";*/RandomUtil.randomNumbers(6);
        //保存验证码到 redis //为了区分，加个前缀 //两分钟过期
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone , code, LOGIN_CODE_TTL , TimeUnit.MINUTES);
        //发送验证码//模拟一下（真实项目对接短信服务商）
        log.debug("发送验证码成功，验证码:{}",code);
        //返回ok
        return Result.ok();
    }

    /**
     * 登录（面试重点：验证码校验 + 首次登录自动注册 + 基于 Redis 的 token 登录态）
     * 成功后将脱敏用户信息存到 Redis Hash（key=login:token:xxx，30分钟过期），返回 token 给前端
     * 前端后续请求带 token，由拦截器解析并刷新有效期
     * @param loginForm 登录参数（手机号+验证码）
     * @param session 会话对象
     * @return 登录结果，成功返回 token
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            //如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        //从redis中获取验证码并校验（与用户输入的验证码比对）
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String code = loginForm.getCode();
        if(cacheCode == null|| !cacheCode.equals(code)){
            //不一致，报错
            return Result.fail("验证码错误");
        }
        //一致，根据手机号查询用户------就是mybatispuls提供的数据库查询方式
        User user = query().eq("phone", phone).one();
        //判断用户是否存在
        if(user == null){
            //不存在，创建新用户（首次登录自动注册，生成随机昵称）
            user=createUserWithPhone(phone);
        }
        //保存用户信息到redis
        //随机生成token，作为登录令牌（登录态的钥匙）
        String token = UUID.randomUUID().toString(true);
        //将User对象转为HashMap存储（只存脱敏字段 UserDTO）
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
        //把 id 从 Long 转成 String，否则 StringRedisTemplate 的 Hash 操作会报 ClassCastException
        userMap.put("id", userDTO.getId().toString());
        //存储到 Redis Hash：login:token:xxx -> {id,phone,nickName,icon}
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        //设置有效期（30分钟），拦截器每次请求会刷新，实现"活跃则续期"
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        //返回token给前端
        return Result.ok(token);
    }

    /**
     * 用户签到（面试重点：Redis BitMap 位图存储签到记录，一个用户一个月只用一个key）
     * key = sign:用户id:202608（年月后缀），偏移量 = 当月第几天-1，值为1表示已签到
     * 相比数据库记录，一个bit存一天，内存占用极低
     * @return 签到结果
     */
    @Override
    public Result sign() {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key（形如 sign:5:202608）
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key=USER_SIGN_KEY+userId+keySuffix;
        //4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //5.写入redis--setbit key offset 1（offset从0开始，所以减1）
        // setBit 返回该位原来的值：true 表示今天已经签过，false 表示本次签到成功
        Boolean oldValue = stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok(Boolean.TRUE.equals(oldValue));
    }

    /**
     * 统计签到天数（面试重点：BITFIELD 一次性取出本月签到位图 + 位运算计算连续签到天数）
     * 用 unsigned 类型把当月的位全部取出转成十进制数字，再从低位往高位逐位判断
     * @return 截止今天连续签到的天数（从今天往回数连续签到几天）
     */
    @Override
    public Result signCount() {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key=USER_SIGN_KEY+userId+keySuffix;
        //4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //5.获取本月截止今天为止的所有签到记录，返回的是一个十进制的数字 bitfield sign:1010:202608 get u14 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if(result==null||result.isEmpty()){
            //没有任何签到结果
            return Result.ok(0);
        }
        Long num=result.get(0);
        if(num==null||num==0){
            return  Result.ok(0);
        }
        //6.循环遍历（从最后一位【今天】开始向前数连续签到的天数）
        int count=0;
        while (true){
            //6.1.让这个数字与1运算，得到数字的最后一个bit位 //判断这个bit位是否为0
            if((num&1)==0){
                //如果是0，说明这天没签到，连续签到中断，结束
                break;
            }else {
                //如果不为0，就说明这天签到了，就算上1天
                count++;
            }
            //把数字右移一位，丢掉最后一个bit位，继续下一个bit位
            num>>>=1;
        }
        return Result.ok(count);
    }

    //创建新用户方法（首次登录手机号注册，生成默认昵称）
    private User createUserWithPhone(String phone) {
    //创建用户
        User user = new User();
        user.setPhone(phone);
        //USER_NICK_NAME_PREFIX就是自己定的昵称前缀（user_ + 随机10位字符串）
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        //保存用户到数据库
        save(user);
        return  user;
    }
}
