local ipAttempts = tonumber(redis.call('GET', KEYS[1]) or '0')

if ipAttempts <= 1 then
    redis.call('DEL', KEYS[1])
else
    redis.call('DECR', KEYS[1])
end

redis.call('DEL', KEYS[2], KEYS[3])
return 1
