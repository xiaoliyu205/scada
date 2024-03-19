package com.example.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("opcua_address")
public class OpcUaAddress {

    @TableField(value = "config_name")
    private String configName;

    @TableField(value = "datapoint")
    private String dataPoint;

    @TableField(value = "plc_name")
    private String plcName;

    @TableField(value = "opcua_address")
    private String address;

    @TableField(value = "url")
    private String url;

    @TableField(value = "user_name")
    private String userName;

    @TableField(value = "password")
    private String password;

}
