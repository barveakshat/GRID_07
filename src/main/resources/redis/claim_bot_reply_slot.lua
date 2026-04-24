local counter_key = KEYS[1]
local cap = tonumber(ARGV[1])

local current = redis.call('GET', counter_key)
if not current then
    redis.call('SET', counter_key, 1)
    return 1
end

local current_count = tonumber(current)
if current_count >= cap then
    return 0
end

redis.call('INCR', counter_key)
return 1

