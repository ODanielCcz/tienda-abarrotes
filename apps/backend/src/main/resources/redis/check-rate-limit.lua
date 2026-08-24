local fallbackWindowMillis = tonumber(ARGV[4])
local blockedDimension = 0
local longestTtlMillis = 0

for index = 1, 3 do
    local current = tonumber(redis.call('GET', KEYS[index]) or '0')
    local limit = tonumber(ARGV[index])

    if current >= limit then
        local ttlMillis = redis.call('PTTL', KEYS[index])
        if ttlMillis < 0 then
            ttlMillis = fallbackWindowMillis
        end
        if blockedDimension == 0 or ttlMillis > longestTtlMillis then
            blockedDimension = index
            longestTtlMillis = ttlMillis
        end
    end
end

if blockedDimension > 0 then
    local retrySeconds = math.max(1, math.ceil(longestTtlMillis / 1000))
    return '0:' .. retrySeconds .. ':' .. blockedDimension
end

for index = 1, 3 do
    local attempts = redis.call('INCR', KEYS[index])
    if attempts == 1 then
        redis.call('PEXPIRE', KEYS[index], fallbackWindowMillis)
    end
end

return '1:0:0'
