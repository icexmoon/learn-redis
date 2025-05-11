package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.SIGN_KEY;

/**
 * Created with IntelliJ IDEA.
 *
 * @author : 魔芋红茶
 * @version : 1.0
 * @Project : hm-dianping
 * @Package : com.hmdp.utils
 * @ClassName : .java
 * @createTime : 2025/5/11 18:51
 * @Email : icexmoon@qq.com
 * @Website : https://icexmoon.cn
 * @Description : 用 Redis BitMap 实现的签到功能
 */
public class RedisSign {

    private final ValueOperations<String, String> opsForValue;
    private final StringRedisTemplate stringRedisTemplate;

    public RedisSign(StringRedisTemplate stringRedisTemplate) {
        opsForValue = stringRedisTemplate.opsForValue();
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 签到
     *
     * @param userId 用户id
     * @param date   日期
     * @return 成功/失败
     */
    public boolean sign(long userId, LocalDate date) {
        final String key = getKey(userId, date);
        // 判断给定日期是所在月份的第几天
        int dayOfMonth = date.getDayOfMonth();
        Boolean result = opsForValue.setBit(key, dayOfMonth - 1, true);
        return BooleanUtil.isTrue(result);
    }

    /**
     * 返回用于记录单个用户指定月的签到记录的 key，类似于 user:sign:1001:200501
     *
     * @param userId 用户id
     * @param date   日期
     * @return key
     */
    private String getKey(long userId, LocalDate date) {
        YearMonth yearMonth = YearMonth.of(date.getYear(), date.getMonth());
        return getKey(userId, yearMonth);
    }

    private String getKey(long userId, YearMonth yearMonth) {
        String monthStr = yearMonth.format(DateTimeFormatter.ofPattern("yyyyMM"));
        return String.format("%s%d:%s", SIGN_KEY, userId, monthStr);
    }

    /**
     * 用户是否在某一天签到过
     *
     * @param userId 用户id
     * @param date   指定日期
     * @return 是否签到过
     */
    public boolean isSigned(long userId, LocalDate date) {
        String key = getKey(userId, date);
        // 判断记录签到的 BitMap 是否存在
        Long count = stringRedisTemplate.countExistingKeys(Collections.singletonList(key));
        if (count == null || count <= 0) {
            // 不存在，视作没有签到过
            return false;
        }
        Boolean bit = opsForValue.getBit(key, date.getDayOfMonth() - 1);
        return BooleanUtil.isTrue(bit);
    }

    /**
     * 获取用户的月度签到记录
     *
     * @param userId    用户id
     * @param yearMonth 指定月
     * @return 月度签到记录
     */
    public Map<LocalDate, Boolean> getSignRecord(long userId, YearMonth yearMonth) {
        // 判断指定月份有多少天
        int lengthOfMonth = yearMonth.lengthOfMonth();
        // 获取签到信息
        final String key = getKey(userId, yearMonth);
        // 读取无符号正月数据，比如 u31
        BitFieldSubCommands.BitFieldType type = BitFieldSubCommands.BitFieldType.unsigned(lengthOfMonth);
        BitFieldSubCommands subCommands = BitFieldSubCommands.create().get(type).valueAt(0);
        List<Long> longList = opsForValue.bitField(key, subCommands);
        // 月度签到记录
        Map<LocalDate, Boolean> record = new HashMap<>();
        // 初始化
        for (int i = 1; i <= lengthOfMonth; i++) {
            LocalDate date = LocalDate.of(yearMonth.getYear(), yearMonth.getMonth(), i);
            record.put(date, Boolean.FALSE);
        }
        if (longList == null || longList.isEmpty()) {
            return record;
        }
        Long decimal = longList.get(0);
        if (decimal == null || decimal == 0) {
            return record;
        }
        // 按bit位从低到高遍历
        for (int i = lengthOfMonth; i >= 1; i--) {
            if ((decimal & 1) == 1) {
                // 当前bit位是1，说明当天签到了
                LocalDate date = LocalDate.of(yearMonth.getYear(), yearMonth.getMonth(), i);
                record.put(date, Boolean.TRUE);
            }
            // 十进制数右移一位
            decimal = decimal >>> 1;
        }
        return record;
    }

    /**
     * 返回用户已经连续签到的天数
     *
     * @param userId 用户 id
     * @return 已经连续签到的天数
     */
    public int getContinueSignDays(long userId) {
        // 获取当月签到记录
        final LocalDate now = LocalDate.now();
        Map<LocalDate, Boolean> signRecord = this.getSignRecord(userId, YearMonth.of(now.getYear(), now.getMonth()));
        // 从当前天向前遍历统计
        int signedDays = 0;
        LocalDate currentDate = now;
        do {
            Month currentMonth = currentDate.getMonth();
            do {
                Boolean isSigned = signRecord.get(currentDate);
                if (BooleanUtil.isTrue(isSigned)) {
                    signedDays++;
                } else {
                    break;
                }
                currentDate = currentDate.minusDays(1);
            }
            while (currentDate.getMonth().equals(currentMonth));
            if (currentMonth.equals(currentDate.getMonth())){
                // 连续签到没有跨月
                break;
            }
            // 连续签到跨月，重新获取所在月的签到记录
            signRecord = this.getSignRecord(userId, YearMonth.of(currentDate.getYear(), currentDate.getMonth()));
        }
        while (true);
        return signedDays;
    }
}
