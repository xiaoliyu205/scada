package org.example.datapoint;

import jakarta.annotation.PostConstruct;
import org.example.annotation.DataPointType;
import org.example.exception.InvalidDptException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class DpChangeFactory {

    @Autowired
    private ApplicationContext applicationContext;

    private static Map<String, DpChangeService> map;

    @PostConstruct
    public void init() {
        map = applicationContext.getBeansWithAnnotation(DataPointType.class).values().stream()
                .collect(Collectors.toMap(
                        bean -> bean.getClass().getAnnotation(DataPointType.class).value(),
                        bean -> (DpChangeService) bean));
    }

    public static DpChangeService getDpChangeService(String dptName) {
        DpChangeService client = map.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(dptName))
                .map(Map.Entry::getValue)
                .findAny().orElse(null);

        if (Objects.isNull(client)) {
            throw new InvalidDptException("invalid client dpt name: " + dptName);
        }
        return client;
    }
}

