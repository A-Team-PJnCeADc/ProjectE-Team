package com.projecte.team;

import java.math.BigDecimal;
import java.math.BigInteger;

import cpw.mods.fml.common.Loader;

/**
 * 团队 EMC 的存储模式(软依赖 ProjectE-Expansion-GTNH)。
 *
 * <p>
 * 装了 PE-E-GTNH 时:团队池用 **BigInteger** 存(不限制上限),由对方解除 ProjectE 容器的 2^30 夹取,
 * 因此高价物品可以按精确值累加。
 *
 * <p>
 * 没装时:完全保持原行为 —— {@code TeamManager} 仍然把团队池夹在 ProjectE 的
 * {@code Constants.TILE_MAX_EMC}(2^30)上,避免"团队池超上限 -> 成员容器 hasMaxedEmc() 为真 ->
 * shift-click 存不进去"。
 *
 * <p>
 * 检测只做一次并缓存;整个判定只依赖 modid,不用编译期依赖。
 */
public final class TeamEmcMode {

    /** 提供 BigInteger 团队池能力的 modid。 */
    public static final String BIG_EMC_MODID = "projectexpansiongtnh";

    private static Boolean big;

    private TeamEmcMode() {}

    public static boolean isBigIntegerMode() {
        if (big == null) {
            big = Loader.isModLoaded(BIG_EMC_MODID);
        }
        return big;
    }

    /** double -> BigInteger(与 PE-E-GTNH 的换算保持一致:取整数部分)。 */
    public static BigInteger fromDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return BigInteger.ZERO;
        }
        return BigDecimal.valueOf(value)
            .toBigInteger();
    }

    /** BigInteger -> double(供 ProjectE 的 double 接口使用,超量程夹到 Double.MAX_VALUE)。 */
    public static double toDouble(BigInteger value) {
        if (value == null || value.signum() <= 0) {
            return 0D;
        }
        return new BigDecimal(value).min(BigDecimal.valueOf(Double.MAX_VALUE))
            .doubleValue();
    }
}
