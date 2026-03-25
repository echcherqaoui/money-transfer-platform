-- KEYS[1]: idempotency key
-- ARGV[1]: status_value (e.g., "PENDING" or "SUCCESS")
-- ARGV[2]: TTL in seconds
-- Returns 1 if key was set (first time seen)
-- Returns 0 if key already existed (duplicate)

local result = redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2])
if result then return 1
else return 0 end