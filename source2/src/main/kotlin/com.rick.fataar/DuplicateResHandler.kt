package com.rick.fataar

import com.android.build.gradle.api.LibraryVariant
import com.android.ide.common.symbols.ResourceDirectoryParseException
import com.android.resources.ResourceFolderType
import com.google.common.base.Charsets
import com.google.common.io.Files
import com.rick.fataar.CountTimeUtils.Companion.countTimeUtils
import groovy.util.Node
import groovy.util.NodeList
import groovy.util.XmlParser
import groovy.xml.XmlUtil
import org.codehaus.groovy.runtime.typehandling.GroovyCastException
import org.gradle.api.Project
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.util.*
import javax.xml.parsers.ParserConfigurationException

/**
 * 处理重复资源的类，重复资源以主aar为准，其中values目录下的文件需要特殊处理,其他同级的目录暂时不需要处理
 */
class DuplicateResHandler(private val mProject: Project) {
    /**
     * 主aar的res目录下的文件夹map，key是文件夹类型，value是该文件夹里面所有文件的文件名集合，除了values
     */
    private val mMainDirectoryMap: MutableMap<ResourceFolderType, HashSet<String>> = HashMap()

    /**
     * 主aar的res/values目录下的所有文件中节点的map，key是节点名称，value是该类节点的集合
     */
    private val mMainValuesMap: MutableMap<String, HashSet<String?>> = HashMap()
    private val mMainAssetsSet: MutableSet<String> = HashSet()

    /**
     * 初始化
     */
    init {
        val mainResPath = mProject.projectDir.path + File.separator + "src" + File.separator + "main" + File.separator + "res"
        iterateResFiles(mainResPath)
        val mainAssetsPath = mProject.projectDir.path + File.separator + "src" + File.separator + "main" + File.separator + "assets"
        iterateAssetsFiles(mainAssetsPath)
    }

    /**
     * 遍历Res文件
     */
    private fun iterateResFiles(resPath: String) {
        countTimeUtils.start("iterateMainResFiles")
        for (file in FileUtils.getFileArray(resPath)) {
            if (!file.isDirectory) {
                continue
            }
            assert(file.isDirectory)
            val type = ResourceFolderType.getFolderType(file.name) ?: continue
            if (type == ResourceFolderType.VALUES) {
                // 处理values目录
                readMainValues(file.path)
            } else {
                readMainResDirectory(type, file)
            }
        }
        countTimeUtils.end("iterateMainResFiles")
    }

    /**
     * 遍历Assets文件
     */
    private fun iterateAssetsFiles(assetsPath: String) {
        countTimeUtils.start("iterateMainAssetsFiles")
        for (fileName in FileUtils.getFileNameArray(assetsPath)) {
            mMainAssetsSet.add(fileName)
        }
        countTimeUtils.end("iterateMainAssetsFiles")
    }

    /**
     * 读取主包的values目录下的文件
     */
    private fun readMainValues(path: String) {
        countTimeUtils.start("readMainValues $path")
        var xmlParser: XmlParser? = null
        try {
            xmlParser = XmlParser()
        } catch (e: ParserConfigurationException) {
            LogUtils.logError(e.message)
        } catch (e: SAXException) {
            LogUtils.logError(e.message)
        }

        // 按类型取出values目录下所有的key，进行保存
        for (resFile in FileUtils.getFileArray(path)) {
            var allNode: Node? = null
            try {
                allNode = xmlParser?.parse(resFile)
            } catch (e: IOException) {
                LogUtils.logError(e.message)
            } catch (e: SAXException) {
                LogUtils.logError(e.message)
            }

            allNode?.children()?.forEach {
                if (it is Node) {
                    var hashSet = mMainValuesMap[it.name()]
                    if (hashSet == null) {
                        hashSet = HashSet()
                        mMainValuesMap[it.name() as String] = hashSet
                    }
                    hashSet.add(it.attributes()["name"] as String?)
                } else {
                    throw GroovyCastException("in the file $path, ${it.toString()} cannot cast Node")
                }
            }
        }
        countTimeUtils.end("readMainValues $path")
    }

    /**
     * 读取主包的res目录下各个文件夹中的文件列表
     */
    private fun readMainResDirectory(type: ResourceFolderType, file: File) {
        countTimeUtils.start("readMainResDirectory ${file.name}")
        var fileNameSet = mMainDirectoryMap[type]
        if (fileNameSet == null) {
            fileNameSet = HashSet()
            mMainDirectoryMap[type] = fileNameSet
        }
        file.listFiles()?.forEach {
            fileNameSet.add(it.name)
        }
        countTimeUtils.end("readMainResDirectory ${file.name}")
    }

    /**
     * 删除重复资源
     *
     * @param variant
     */
    fun deleteDuplicate(variant: LibraryVariant) {
        // 首先判断根目录是否存在
        val explodedRootDir = mProject.file("${mProject.buildDir}/intermediates/exploded-aar/")
                ?: return

        // 遍历处理文件
        val fileTreeWalk: FileTreeWalk = explodedRootDir.walk()
        fileTreeWalk.filter { it.name == variant.name && it.isDirectory && it.exists() }.forEach { dir ->
            // assets目录
            dir.walk().maxDepth(1).filter { it.name == "assets" }.forEach { assetsDir ->
                deleteDuplicateAssetsFiles(assetsDir)
            }
            // res目录
            dir.walk().maxDepth(1).filter { it.name == "res" }.forEach { resDir ->
                deleteDuplicateRes(resDir)
            }
        }
    }

    /**
     * 删除与主包重复的assets文件
     */
    private fun deleteDuplicateAssetsFiles(dir: File?) {
        countTimeUtils.start("deleteDuplicateAssetsFiles ${dir?.path}")
        if (dir?.isDirectory == true && dir.exists()) {
            val path = dir.absolutePath
            FileUtils.getFileNameArray(path).filter { mMainAssetsSet.contains(it) }.forEach {
                val file = File(path + File.separator + it)
                if (file.exists()) {
                    file.delete()
                }
                LogUtils.logAnytime("Delete $it from the $path")
            }
        }
        countTimeUtils.end("deleteDuplicateAssetsFiles ${dir?.path}")
    }

    /**
     * 删除res重复资源
     */
    private fun deleteDuplicateRes(dir: File?) {
        countTimeUtils.start("deleteDuplicateRes ${dir?.path}")
        if (dir?.isDirectory == true && dir.exists()) {
            dir.listFiles()?.filter { it.isDirectory }?.forEach {
                it.listFiles()?.let { listFiles ->
                    val folderResourceType = ResourceFolderType.getFolderType(it.name)
                    if (folderResourceType == ResourceFolderType.VALUES) {
                        deleteValuesAttribute(listFiles)
                    } else {
                        deleteDuplicateFiles(folderResourceType, listFiles)
                    }
                }
            }
        }
        countTimeUtils.end("deleteDuplicateRes ${dir?.path}")
    }

    /**
     * 删除与主aar中重名的文件，除了values目录
     *
     * @param type      文件类型
     * @param fileArray 文件数组
     */
    private fun deleteDuplicateFiles(type: ResourceFolderType, fileArray: Array<File>) {
        val mainFileSet: Set<String>? = mMainDirectoryMap[type]
        if (mainFileSet == null || mainFileSet.isEmpty()) {
            return
        }

        var i = 0
        while (i < fileArray.size) {
            i++
            val file = fileArray[i]
            if (file.isDirectory) {
                continue
            }

            // file可能不存在
            if (file.isFile && file.exists()) {
                for (fileName in mainFileSet) {
                    if (file.name === fileName) {
                        file.delete()
                        i--
                        LogUtils.logAnytime("Delete $fileName from the ${type.name}")
                        break
                    }
                }
            }
        }
    }

    /**
     * 在values.xml文件中删除与主aar中重名的资源
     *
     * @param fileArray 文件数组
     */
    private fun deleteValuesAttribute(fileArray: Array<File>) {
        var xmlParser: XmlParser? = null
        try {
            xmlParser = XmlParser()
        } catch (e: ParserConfigurationException) {
            LogUtils.logError(e.message)
        } catch (e: SAXException) {
            LogUtils.logError(e.message)
        }

        fileArray.filter { it.isFile }.forEach { file ->
            var wholeNode: Node? = null
            try {
                wholeNode = xmlParser?.parse(file)
            } catch (e: IOException) {
                LogUtils.logError(e.message)
            } catch (e: SAXException) {
                LogUtils.logError(e.message)
            }

            var removeCount = 0
            wholeNode?.run {
                children().filterIsInstance<Node>().filter { it.name() != null }.forEach { node ->
                    val hashSet: HashSet<String?>? = mMainValuesMap[node.name()]
                    if (hashSet?.contains(node.attribute("name")) == true) {
                        removeCount += 1
                        remove(node)
                    }
                }
            }
            if (removeCount > 0) {
                try {
                    Files.asCharSink(file, Charsets.UTF_8).write(XmlUtil.serialize(wholeNode))
                } catch (e: IOException) {
                    LogUtils.logError(e.message)
                }
            }
        }
    }
}