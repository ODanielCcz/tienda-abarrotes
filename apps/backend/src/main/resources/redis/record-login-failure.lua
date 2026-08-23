local windowMillis = tonumber(ARGV[1])

for index = 1, #KEYS do
    local failures = redis.call('INCR', KEYS[index])
    if failures == 1 then
        redis.call('PEXPIRE', KEYS[index], windowMillis)
    end
end

return 1
