-- ==============================
-- Redis Key 定义
-- ==============================

-- Redis Hash Key（存储 workerId 和 datacenterId）
local hashKey = 'snowflake_work_id_key'

-- Hash 字段：数据中心ID
local dataCenterIdKey = 'dataCenterId'

-- Hash 字段：工作节点ID
local workIdKey = 'workId'


-- ==============================
-- 1. 初始化逻辑（首次启动）
-- ==============================

-- 如果 key 不存在（说明系统第一次运行）
if (redis.call('exists', hashKey) == 0) then

    -- 初始化 dataCenterId = 0
    redis.call('hincrby', hashKey, dataCenterIdKey, 0)

    -- 初始化 workId = 0
    redis.call('hincrby', hashKey, workIdKey, 0)

    -- 返回 (0, 0)
    return { 0, 0 }
end


-- ==============================
-- 2. 读取当前值
-- ==============================

-- 获取当前 dataCenterId
local dataCenterId = tonumber(redis.call('hget', hashKey, dataCenterIdKey))

-- 获取当前 workId
local workId = tonumber(redis.call('hget', hashKey, workIdKey))


-- ==============================
-- 3. Snowflake 限制
-- ==============================

-- 5 bit 最大值（2^5 - 1）
local max = 31

-- 结果变量
local resultWorkId = 0
local resultDataCenterId = 0


-- ==============================
-- 4. 分配策略（核心逻辑）
-- ==============================

-- 情况1：datacenterId 和 workId 都已经达到最大值
if (dataCenterId == max and workId == max) then

    -- 重置为 0（循环使用）
    redis.call('hset', hashKey, dataCenterIdKey, '0')
    redis.call('hset', hashKey, workIdKey, '0')

    resultWorkId = 0
    resultDataCenterId = 0


-- 情况2：workId 未满（优先增加 workerId）
elseif (workId ~= max) then

    -- workerId +1
    resultWorkId = redis.call('hincrby', hashKey, workIdKey, 1)

    -- dataCenterId 保持不变
    resultDataCenterId = dataCenterId


-- 情况3：workId 已满，但 datacenterId 未满
elseif (dataCenterId ~= max) then

    -- workerId 重置为 0
    resultWorkId = 0

    -- dataCenterId +1
    resultDataCenterId = redis.call('hincrby', hashKey, dataCenterIdKey, 1)

    -- 同时重置 workId
    redis.call('hset', hashKey, workIdKey, '0')
end


-- ==============================
-- 5. 返回结果
-- ==============================

-- 返回分配的 workerId 和 datacenterId
return { resultWorkId, resultDataCenterId }
