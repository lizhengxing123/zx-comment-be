package com.lzx.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 滚动查询结果类
 *
 * @param <T> 泛型类型，用于指定列表中元素的类型
 */
@Data
@AllArgsConstructor
public class ScrollResult<T> {
    /**
     * 列表数据
     */
    private List<T> list;
    /**
     * 最小时间戳
     */
    private Long minTimeStamp;
    /**
     * 偏移量
     */
    private Integer offset;

    /**
     * 空参构造方法
     */
    public ScrollResult() {
        this.list = List.of();
        this.minTimeStamp = 0L;
        this.offset = 0;
    }
}
