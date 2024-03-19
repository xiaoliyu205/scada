package com.example.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.OpcUaAddress;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@DS("assembly_order")
public interface OpcUaAddressMapper extends BaseMapper<OpcUaAddress> {
}
