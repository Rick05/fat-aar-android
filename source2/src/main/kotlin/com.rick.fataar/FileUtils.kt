package com.rick.fataar

import java.io.File
import java.util.*

/**
 * Author: 嘿嘿抛物线
 * Date  : 5/16/21
 * Email : easygoingrickking@gmail.com
 * Desc  : 文件操作工具类
 */
object FileUtils {
    /**
     * 获取文件夹下所有文件
     * @param filePath
     * @return
     */
    fun getFileArray(filePath: String?): Array<File> {
        if (filePath == null) {
            return arrayOf()
        }
        val file = File(filePath)
        return file.listFiles() ?: arrayOf()
    }

    fun getFileNameArray(filePath: String?): List<String> {
        val fileNameArray: MutableList<String> = ArrayList()
        return getFileNameArray(fileNameArray, filePath, "")
    }

    /**
     * 获取文件夹下所有文件名
     * @param array 文件名称数组
     * @param filePath 文件路径
     * @param prefixPath 文件前缀 ##子目录下文件名前添加目录名称
     * @return
     */
    fun getFileNameArray(array: MutableList<String>, filePath: String?, prefixPath: String): List<String> {
        val fileArray = getFileArray(filePath)
                ?: return array
        for (file in fileArray) {
            if (file.isDirectory) {
                getFileNameArray(array, file.path, prefixPath + File.separator + file.name + File.separator)
            } else {
                array.add(prefixPath + file.name)
            }
        }
        return array
    }
}