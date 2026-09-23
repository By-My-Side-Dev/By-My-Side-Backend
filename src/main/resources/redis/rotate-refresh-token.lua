local currentToken = redis.call('GET', KEYS[1])
if not currentToken then
    return 0
end

if currentToken ~= ARGV[1] then
    return -1
end

redis.call('PSETEX', KEYS[1], ARGV[3], ARGV[2])
return 1
