package com.omnibank.service.agent.dd.support;

import com.omnibank.service.agent.dd.tool.ToolRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 业务域 Mock 数据生成器：所有模拟数据由企业信用代码派生，保证同一企业多次执行结果一致，禁止使用随机数。
 */
public final class DdDeterministicMockData {

    private DdDeterministicMockData() {
    }

    /** 由业务主键与用途派生稳定种子，种子范围非负 */
    public static long seed(String key, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(((key == null ? "" : key) + "|" + salt)
                    .getBytes(StandardCharsets.UTF_8));
            long value = 0L;
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (bytes[i] & 0xFFL);
            }
            return value & Long.MAX_VALUE;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 稳定区间整数，闭区间 [min, max] */
    public static int intBetween(String key, String salt, int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + (int) (seed(key, salt) % (max - min + 1));
    }

    /** 稳定区间比例，保留 4 位小数 */
    public static BigDecimal rateBetween(String key, String salt, BigDecimal minRate, BigDecimal maxRate) {
        BigDecimal unit = BigDecimal.valueOf(seed(key, salt) % 10000L)
                .divide(BigDecimal.valueOf(10000L), 4, RoundingMode.HALF_UP);
        return minRate.add(maxRate.subtract(minRate).multiply(unit)).setScale(4, RoundingMode.HALF_UP);
    }

    /** 以万元为单位生成稳定金额，保留 2 位小数 */
    public static BigDecimal baseAmount(String key, String salt, int minWan, int maxWan) {
        return BigDecimal.valueOf(intBetween(key, salt, minWan, maxWan))
                .multiply(BigDecimal.valueOf(10000L))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** 金额统一保留 2 位，四舍五入 */
    public static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    /** 安全除法：分子或分母为空、分母为零时返回 null，避免数据缺失导致计算异常 */
    public static BigDecimal divide(BigDecimal numerator, BigDecimal denominator, int scale) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, scale, RoundingMode.HALF_UP);
    }

    /** 读取工具入参中的金额，类型不合法时返回 null */
    public static BigDecimal decimalParam(ToolRequest request, String name) {
        return toDecimal(request.params().get(name));
    }

    /** 读取工具入参中的长整型，类型不合法时返回 null */
    public static Long longParam(ToolRequest request, String name) {
        Object value = request.params().get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
