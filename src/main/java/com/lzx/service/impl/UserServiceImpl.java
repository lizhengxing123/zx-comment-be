package com.lzx.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzx.dto.LoginFormDTO;
import com.lzx.dto.UserDTO;
import com.lzx.entity.User;
import com.lzx.exception.BaseException;
import com.lzx.exception.PhoneInvalidException;
import com.lzx.mapper.UserMapper;
import com.lzx.redis.RedisConstants;
import com.lzx.service.UserService;
import com.lzx.utils.RegexUtils;
import com.lzx.utils.UserHolder;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;


import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.lzx.redis.RedisConstants.*;
import static com.lzx.constant.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * 用户服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码
     *
     * @param phone   手机号
     * @param session HttpSession
     * @return 验证码
     */
    @Override
    public String sendCode(String phone, HttpSession session) {
        // 1、校验手机号
        checkPhone(phone);
        // 2、生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 3、将验证码保存到 redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        log.info("调用第三方服务发送验证码，验证码为：{}", code);

        // 4、返回验证码
        return code;
    }

    /**
     * 登录
     *
     * @param loginForm 登录表单
     * @return token 登录凭证
     */
    @Override
    public String login(LoginFormDTO loginForm) {
        // 1、校验手机号
        checkPhone(loginForm.getPhone());
        // 2、校验验证码
        checkCode(loginForm.getPhone(), loginForm.getCode());
        // 3、根据手机号查询用户
        User user = createUserByPhone(loginForm.getPhone());
        // 4、保存用户到 redis
        return saveUserToRedis(user);
    }

    /**
     * 根据 ID 查询用户信息
     *
     * @param id 用户 ID
     * @return 用户信息
     */
    @Override
    public UserDTO queryUserById(Long id) {
        // 1、从数据库查询用户信息
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BaseException("用户不存在");
        }
        // 2、将 user 转换为 UserDTO
        return BeanUtil.copyProperties(user, UserDTO.class);
    }

    /**
     * 用户签到
     * 使用 Redis 中的 BitMap 来记录用户签到
     */
    @Override
    public void sign() {
        // 构建 Redis key: sign:userId:yyyy:MM
        String key = getSignKey();
        // 签到：SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, LocalDate.now().getDayOfMonth() - 1, true);
    }

    /**
     * 签到统计：获取当前用户截止当前时间在本月的连续签到次数
     *
     * @return 签到统计信息
     */
    @Override
    public Integer signCount() {
        // 获取当前用户
        UserDTO user = UserHolder.getUser();
        // 获取当前年月日，使用数组存储
        String[] date = LocalDate.now().toString().split("-");
        // 构建 Redis key: sign:userId:yyyy:MM
        String key = getSignKey();
        // 获取本月的位图：GETFIELD key GET u天 0
        List<Long> longs = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().get(
                        BitFieldSubCommands.BitFieldType.unsigned(LocalDate.now().getDayOfMonth())
                ).valueAt(0));
        if (longs == null || longs.isEmpty()) {
            // 没有签到记录
            return 0;
        }
        // 获取签到记录的十进制整数
        Long signRecord = longs.getFirst();
        // 初始化连续签到次数
        int count = 0;
        // 循环遍历签到记录的每一位
        for (int i = 0; i < LocalDate.now().getDayOfMonth(); i++) {
            // 如果第 i 位是 1，则说明用户在第 i 天签到了
            if ((signRecord >> i & 1) != 0) {
                count++;
            } else {
                // 如果第 i 位是 0，则说明用户在第 i 天没有签到
                // 直接退出循环
                break;
            }
        }
        // 返回连续签到次数
        return count;
    }


    // --------------------------- 私有方法 ---------------------------

    /**
     * 校验手机号
     */
    private void checkPhone(String phone) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            throw new PhoneInvalidException("手机号格式错误");
        }
    }

    /**
     * 校验验证码
     */
    private void checkCode(String phone, String code) {
        // 1、从 redis 获取验证码
        String redisCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        // 2、校验验证码
        if (code == null || !code.equals(redisCode)) {
            throw new BaseException("验证码错误");
        }
    }

    /**
     * 根据手机号创建用户
     */
    private User createUserByPhone(String phone) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user == null) {
            user = User.builder().phone(phone).nickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(6) + phone.substring(7)).build();
            userMapper.insert(user);
        }
        return user;
    }

    /**
     * 保存用户到 redis
     */
    private String saveUserToRedis(User user) {
        // 1、将用户转换为 UserDTO
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 2、创建一个 UUID 作为 token
        String token = UUID.randomUUID().toString(true); // 不带 - 的 UUID
        String key = LOGIN_USER_KEY + token;
        // 3、将 UserDTO 转为 Hash 存储
        // 需要注意的是，Long 类型的转换会出错，需要手动转换为 String
        stringRedisTemplate.opsForHash().putAll(key, BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())));
        // 4、设置过期时间
        // TODO 后面需要改为分钟
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.DAYS);

        log.info("用户登录成功，用户信息：{}", userDTO);

        // 5、返回 token
        return token;
    }

    /**
     * 获取位图 key
     */
    private String getSignKey() {
        // 获取当前用户
        UserDTO user = UserHolder.getUser();
        // 获取当前年月日，使用数组存储
        String[] date = LocalDate.now().toString().split("-");
        // 构建 Redis key: sign:userId:yyyy:MM
        return StrUtil.format(USER_SIGN_KEY + "{}:{}:{}", user.getId(), date[0], date[1]);
    }
}
