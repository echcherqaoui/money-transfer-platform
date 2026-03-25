-- KEYS[1]: senderId
-- ARGV[1]: score (epoch millis)
-- ARGV[2]: member (unique UUID)
-- ARGV[3]: windowStart (remove entries before this)
-- ARGV[4]: maxTransactions limit
-- ARGV[5]: TTL in seconds

-- Remove stale entries
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[3])

-- Add this request (record it regardless of outcome for audit)
redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])

local currentCount = redis.call('ZCARD', KEYS[1])

-- Reset TTL
redis.call('EXPIRE', KEYS[1], ARGV[5])

--Return 1 if this request exceeded limit
if currentCount > tonumber(ARGV[4]) then return 1
else return 0 end