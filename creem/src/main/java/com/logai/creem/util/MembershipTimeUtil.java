package com.logai.creem.util;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 会员时长折算工具类
 * 用于处理用户升级、降级时的剩余时长转换逻辑
 */
public class MembershipTimeUtil {

    /**
     * 计算折算后的天数
     *
     * @param oldPrice      原会员月价格 (例如 4.1)
     * @param newPrice      新会员月价格 (例如 8.3)
     * @param remainingDays 原会员剩余天数
     * @return 折算后的天数
     */
    public static long calculateConvertedDays(double oldPrice, double newPrice, long remainingDays) {
        if (remainingDays <= 0 || oldPrice <= 0 || newPrice <= 0) {
            return 0;
        }

        double oldDailyPrice = oldPrice / 30.0;
        double newDailyPrice = newPrice / 30.0;

        // 折算天数 = 剩余天数 × (原套餐日价 ÷ 新套餐日价)
        return Math.round((remainingDays * oldDailyPrice) / newDailyPrice);
    }

    /**
     * 计算会员新结束时间（升级或降级）
     *
     * @param now               当前时间
     * @param oldEndTime        原会员结束时间
     * @param oldPrice          原会员月价格
     * @param newPrice          新会员月价格
     * @param newDurationMonths 新会员周期（月）
     * @return 新的会员结束时间
     */
    public static LocalDateTime calculateNewEndTime(LocalDateTime now,
                                                    LocalDateTime oldEndTime,
                                                    double oldPrice,
                                                    double newPrice,
                                                    int newDurationMonths) {
        // 剩余天数
        long remainingDays = ChronoUnit.DAYS.between(now, oldEndTime);

        // 折算后的天数
        long convertedDays = calculateConvertedDays(oldPrice, newPrice, remainingDays);

        // 新套餐周期 + 折算天数
        return now.plusMonths(newDurationMonths).plusDays(convertedDays);
    }
}
