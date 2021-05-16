package com.rick.fataar

import org.gradle.api.Project

/**
 * Author: 嘿嘿抛物线
 * Date  : 5/16/21
 * Email : easygoingrickking@gmail.com
 * Desc  : 无
 */
internal object FatUtils {
    private var sProject: Project? = null
    fun attach(p: Project?) {
        sProject = p
    }

    fun logError(msg: String?) {
        sProject!!.logger.error("[fat-aar]$msg")
    }

    fun logInfo(msg: String?) {
        sProject!!.logger.info("[fat-aar]$msg")
    }

    fun logAnytime(msg: String?) {
        println("[fat-aar]$msg")
    }
}