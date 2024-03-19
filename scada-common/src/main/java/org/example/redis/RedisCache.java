package org.example.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class RedisCache {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String RELEASE_LOCK_LUA_SCRIPT = "local v = redis.call('get', KEYS[1]) local r = 1 if v == ARGV[1] or v == false then redis.call('set', KEYS[1], ARGV[2]) else r = 0 end return r";

    @FunctionalInterface
    public interface ConvertValue {
        String value(String str);
    }


    public void set(String key, String value, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    public Set<String> keys(String pattern) {
        return stringRedisTemplate.keys(pattern);
    }

    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    public void delete(Set<String> key) {
        stringRedisTemplate.delete(key);
    }

    public String compareAndSet(String key, ConvertValue convertValue) {
        String oldValue;
        String newValue;
        do {
            oldValue = get(key);
            newValue = convertValue.value(oldValue);
        } while (!compareAndSet(key, oldValue, newValue));
        return oldValue;
    }

    private Boolean compareAndSet(String key, String oldValue, String newValue) {
        // 指定 lua 脚本，并且指定返回值类型
        DefaultRedisScript<Boolean> redisScript = new DefaultRedisScript<>(RELEASE_LOCK_LUA_SCRIPT, Boolean.class);
        // 参数一：redisScript，参数二：key列表，参数三：arg（可多个）
        return stringRedisTemplate.execute(redisScript, Collections.singletonList(key), oldValue, newValue);
    }
}
