-- KEYS: code, attempts, one-time token
-- ARGV: presented code, token payload, token TTL, maximum attempts
local code = redis.call('GET', KEYS[1])
if not code then
    return 0
end
if code ~= ARGV[1] then
    local ttl = redis.call('PTTL', KEYS[1])
    local attempts = redis.call('INCR', KEYS[2])
    if ttl > 0 then
        redis.call('PEXPIRE', KEYS[2], ttl)
    end
    if attempts >= tonumber(ARGV[4]) then
        redis.call('DEL', KEYS[1], KEYS[2])
    end
    return 0
end
-- Token creation and code consumption share one atomic operation.
redis.call('SET', KEYS[3], ARGV[2], 'EX', ARGV[3])
redis.call('DEL', KEYS[1], KEYS[2])
return 1
