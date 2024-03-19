package org.example.datapoint;

import com.alibaba.fastjson2.JSON;
import org.springframework.amqp.core.Message;

public class DpChangeDriver implements Runnable {

    private final Message message;

    public DpChangeDriver(Message message) {
        this.message = message;
    }

    @Override
    public void run() {
        String dptKey = message.getMessageProperties().getReceivedRoutingKey();
        DpValueItem dpValueItem = JSON.parseObject(message.getBody(), DpValueItem.class);
        DpChangeFactory.getDpChangeService(dptKey).dpChange(dpValueItem);
    }
}
