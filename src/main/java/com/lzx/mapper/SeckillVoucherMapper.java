package com.lzx.mapper;

import com.lzx.entity.SeckillVoucher;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 Mapper 接口
 * </p>
 *
 * @author 李正星
 * @since 2025-09-18
 */
@Mapper
public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {

    /**
     * 根据秒杀券 ID 查询详细信息进行判断（悲观锁）
     * 这里的 FOR UPDATE 会在查询时加行锁（前提是有索引，并且命中行），直到事务提交才释放
     */
    @Select("SELECT * FROM tb_seckill_voucher WHERE voucher_id = #{id} FOR UPDATE")
    SeckillVoucher selectByIdForUpdate(Long id);
}
