package org.example.datapoint;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

@Data
@AllArgsConstructor
public class DpValueItem {
    private String dpName;
    private String value;
    private Date time;
}
