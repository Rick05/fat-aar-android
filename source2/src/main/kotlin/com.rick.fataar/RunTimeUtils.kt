package com.rick.fataar

import java.util.*

/**
 * Author: 嘿嘿抛物线
 * Date  : 5/16/21
 * Email : easygoingrickking@gmail.com
 * Desc  : 无
 */
internal class RunTimeUtils private constructor() {
    private val SUFFIX = "-TOTAL"
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
                var time = durationMap[tag + SUFFIX]
                if (time == null) {
                    time = 0L
                }
                time += runTime
                durationMap[tag + SUFFIX] = time
                FatUtils.logAnytime("-----> ${tag + SUFFIX} executes for $time millis")
            }
            FatUtils.logAnytime("-----> $tag executes for $runTime millis")
        } else {
            throw RuntimeException("miss call method start($tag)")
        }
    }

    companion object {
        @JvmStatic
        val instance = RunTimeUtils()
    }
}