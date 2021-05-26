package com.rick.fataar

import java.util.*

/**
 * Author: 嘿嘿抛物线
 * Date  : 5/16/21
 * Email : easygoingrickking@gmail.com
 * Desc  : 计算执行时间的工具类
 */
internal class CountTimeUtils private constructor() {

    private val suffix = "-TOTAL"
    private val durationMap = HashMap<String, Long>()

    fun start(tag: String) {
        durationMap[tag] = System.currentTimeMillis()
    }

    @JvmOverloads
    fun end(tag: String, isCalTotal: Boolean = false) {
        val startTime = durationMap[tag]
        if (startTime != null) {
            val runTime = System.currentTimeMillis() - startTime
            if (isCalTotal) {
                var time = durationMap[tag + suffix]
                if (time == null) {
                    time = 0L
                }
                time += runTime
                durationMap[tag + suffix] = time
                LogUtils.logAnytime("-----> ${tag + suffix} executes for $time millis")
            }
            LogUtils.logAnytime("-----> $tag executes for $runTime millis")
        } else {
            throw RuntimeException("miss call method start($tag)")
        }
    }

    companion object {
        @JvmStatic
        val countTimeUtils = CountTimeUtils()
    }
}