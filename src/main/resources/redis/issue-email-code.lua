-- KEYS: cooldown, daily, code, attempts
-- ARGV: cooldown seconds, daily limit, daily TTL, code TTL, code (empty for unmatched account)
if redis.call('EXISTS', KEYS[1]) == 1 then
    return -1
end
local count = tonumber(redis.call('GET', KEYS[2]) or '0')
if count >= tonumber(ARGV[2]) then
    return -2
end
redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
local nextCount = redis.call('INCR', KEYS[2])
if nextCount == 1 then
    redis.call('EXPIRE', KEYS[2], ARGV[3])
end
redis.call('DEL', KEYS[3], KEYS[4])
if ARGV[5] ~= '' then
    redis.call('SET', KEYS[3], ARGV[5], 'EX', ARGV[4])
    redis.call('SET', KEYS[4], '0', 'EX', ARGV[4])
end
return 1
