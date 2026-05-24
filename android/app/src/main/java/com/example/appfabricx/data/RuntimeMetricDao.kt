package com.example.appfabricx.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface RuntimeMetricDao {

    @Insert
    fun insertDevice1(metric: Device1RuntimeMetric): Long

    @Insert
    fun insertDevice2(metric: Device2RuntimeMetric): Long

    @Insert
    fun insertDevice3(metric: Device3RuntimeMetric): Long

    @Transaction
    fun insertAllDevices(
        device1: Device1RuntimeMetric,
        device2: Device2RuntimeMetric,
        device3: Device3RuntimeMetric,
    ): Long {
        insertDevice1(device1)
        insertDevice2(device2)
        return insertDevice3(device3)
    }

    @Query("SELECT COUNT(*) FROM device1_runtime_metrics")
    fun countDevice1(): Int

    @Query("SELECT COUNT(*) FROM device2_runtime_metrics")
    fun countDevice2(): Int

    @Query("SELECT COUNT(*) FROM device3_runtime_metrics")
    fun countDevice3(): Int

    @Query(
        """
        SELECT * FROM device1_runtime_metrics
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    fun recentDevice1(limit: Int): List<Device1RuntimeMetric>

    @Query(
        """
        SELECT * FROM device2_runtime_metrics
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    fun recentDevice2(limit: Int): List<Device2RuntimeMetric>

    @Query(
        """
        SELECT * FROM device3_runtime_metrics
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    fun recentDevice3(limit: Int): List<Device3RuntimeMetric>

    @Query(
        """
        SELECT runtime_state AS runtimeState, COUNT(*) AS count
        FROM device1_runtime_metrics
        GROUP BY runtime_state
        """
    )
    fun stateDistributionDevice1(): List<RuntimeStateCount>

    @Query(
        """
        SELECT runtime_state AS runtimeState, COUNT(*) AS count
        FROM device2_runtime_metrics
        GROUP BY runtime_state
        """
    )
    fun stateDistributionDevice2(): List<RuntimeStateCount>

    @Query(
        """
        SELECT runtime_state AS runtimeState, COUNT(*) AS count
        FROM device3_runtime_metrics
        GROUP BY runtime_state
        """
    )
    fun stateDistributionDevice3(): List<RuntimeStateCount>

    @Query(
        """
        SELECT AVG(cpu_usage) FROM device1_runtime_metrics
        WHERE timestamp >= :sinceMs
        """
    )
    fun avgCpuDevice1Since(sinceMs: Long): Float?

    @Query(
        """
        SELECT AVG(cpu_usage) FROM device2_runtime_metrics
        WHERE timestamp >= :sinceMs
        """
    )
    fun avgCpuDevice2Since(sinceMs: Long): Float?

    @Query(
        """
        SELECT AVG(cpu_usage) FROM device3_runtime_metrics
        WHERE timestamp >= :sinceMs
        """
    )
    fun avgCpuDevice3Since(sinceMs: Long): Float?

    @Query(
        """
        SELECT * FROM device1_runtime_metrics
        WHERE record_id > :afterRecordId
        ORDER BY record_id ASC
        LIMIT :limit
        """
    )
    fun device1AfterRecordId(afterRecordId: Long, limit: Int): List<Device1RuntimeMetric>

    @Query(
        """
        SELECT * FROM device2_runtime_metrics
        WHERE record_id > :afterRecordId
        ORDER BY record_id ASC
        LIMIT :limit
        """
    )
    fun device2AfterRecordId(afterRecordId: Long, limit: Int): List<Device2RuntimeMetric>

    @Query(
        """
        SELECT * FROM device3_runtime_metrics
        WHERE record_id > :afterRecordId
        ORDER BY record_id ASC
        LIMIT :limit
        """
    )
    fun device3AfterRecordId(afterRecordId: Long, limit: Int): List<Device3RuntimeMetric>
}

data class RuntimeStateCount(
    val runtimeState: String,
    val count: Int,
)
