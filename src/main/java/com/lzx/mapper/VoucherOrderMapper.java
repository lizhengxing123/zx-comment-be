package com.lzx.mapper;

import com.lzx.entity.VoucherOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author 李正星
 * @since 2025-09-18
 */
@Mapper
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {

    /**
     * 根据用户 ID 和优惠券 ID 查询订单数量，使用 FOR UPDATE 锁定记录
     * SELECT ... FOR UPDATE 会在查询时对符合条件的记录加行锁
     * 其他事务如果要查询相同条件的记录，会被阻塞，直到当前事务提交或回滚
     * 这保证了查询和插入的原子性
     *
     * @param userId    用户 ID
     * @param voucherId 优惠券 ID
     * @return 订单数量
     */
    @Select("SELECT COUNT(*) FROM tb_voucher_order WHERE user_id = #{userId} AND voucher_id = #{voucherId} FOR UPDATE")
    Long countByUserIdAndVoucherId(Long userId, Long voucherId);
}
