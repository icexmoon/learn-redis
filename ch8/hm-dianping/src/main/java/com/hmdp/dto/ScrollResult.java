package com.hmdp.dto;

import lombok.Data;

import java.util.List;

@Data
public class ScrollResult<E> {
    private List<E> list;
    private Long minTime;
    private Integer offset;
}
