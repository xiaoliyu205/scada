package org.example.rabbitmq;

import lombok.extern.slf4j.Slf4j;
import org.example.constant.DptConstant;
import org.example.datapoint.DpChangeDriver;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RabbitConsumer {

    private static final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            24,
            999,
            60,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(10),
            new ThreadPoolExecutor.CallerRunsPolicy());

    @RabbitListener(queues = DptConstant.DPT_PREDIX + "*")
    public void receive(Message message) {
        log.info("...RabbitMQ Received: {}", message);
        executor.execute(new DpChangeDriver(message));
    }
}
