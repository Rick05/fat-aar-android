package com.rick.fataar

import com.android.build.gradle.api.LibraryVariant
import com.android.ide.common.symbols.ResourceDirectoryParseException
import com.android.resources.ResourceFolderType
import com.google.common.base.Charsets
import com.google.common.io.Files
import com.rick.fataar.RunTimeUtils.Companion.instance
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
        instance.start("iterateMainResFiles")
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
        instance.end("iterateMainResFiles")
    }

    /**
     * 遍历Assets文件
     */
    private fun iterateAssetsFiles(assetsPath: String) {
        instance.start("iterateMainAssetsFiles")
        for (fileName in FileUtils.getFileNameArray(assetsPath)) {
            mMainAssetsSet.add(fileName)
        }
        instance.start("iterateMainAssetsFiles")
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

        val fileTreeWalk: FileTreeWalk = explodedRootDir.walk()
        fileTreeWalk.filter { it.name == variant.name }.forEach { dir ->
            if (dir.isDirectory && dir.exists()) {
                dir.walk().maxDepth(1).filter { it -> it.name == "assets" }.forEach { assetsDir ->
                    deleteDuplicateAssetsFiles(assetsDir)
                }
                dir.walk().maxDepth(1).filter { it -> it.name == "res" }.forEach { resDir ->
                    deleteDuplicateRes(resDir)
                }
            }
        }
    }

    /**
     * 删除与主包重复的assets文件
     *
     * @param variant
     */
    fun deleteDuplicateAssetsFiles(dir: File?) {
        if (dir?.isDirectory == true && dir.exists()) {
            val path = dir.absolutePath
            val aarFileNameArray = FileUtils.getFileNameArray(path)
            for (fileName in aarFileNameArray) {
                if (mMainAssetsSet.contains(fileName)) {
                    val file = File(path + File.separator + fileName)
                    if (file.exists()) {
                        file.delete()
                    }
                    FatUtils.logAnytime("Delete $fileName from the $path")
                }
            }
        }
    }

    /**
     * 读取主包的values目录下的文件
     */
    private fun readMainValues(path: String) {
        instance.start("readMainValues \$path")
        var xmlParser: XmlParser? = null
        try {
            xmlParser = XmlParser()
        } catch (e: ParserConfigurationException) {
            e.printStackTrace()
        } catch (e: SAXException) {
            e.printStackTrace()
        }
        // 按类型取出values目录下所有的key，进行保存
        for (resFile in FileUtils.getFileArray(path)) {
            var allNode: Node? = null
            try {
                allNode = xmlParser?.parse(resFile)
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: SAXException) {
                e.printStackTrace()
            }
            if (allNode != null) {
                for (it in allNode.children()) {
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
        }
        instance.end("readMainValues \$path")
    }

    /**
     * 读取主包的res目录下各个文件夹中的文件列表
     */
    private fun readMainResDirectory(type: ResourceFolderType, file: File) {
        instance.start("readMainResDirectory ${file.name}")
        var fileNameSet = mMainDirectoryMap[type]
        if (fileNameSet == null) {
            fileNameSet = HashSet()
            mMainDirectoryMap[type] = fileNameSet
        }
        for (childFile in file.listFiles()) {
            fileNameSet.add(childFile.name)
        }
        instance.end("readMainResDirectory ${file.name}")
    }

    /**
     * 删除重复资源
     */
    fun deleteDuplicateRes(dir: File?) {
        if (dir?.isDirectory == true && dir.exists()) {
            val files = dir.listFiles() ?: return
            for (resourceDirectory in files) {
                if (!resourceDirectory.isDirectory) {
                    throw ResourceDirectoryParseException(
                            resourceDirectory.absolutePath + " is not a directory")
                }
                assert(resourceDirectory.isDirectory)
                val listFiles = resourceDirectory.listFiles() ?: continue
                val folderResourceType = ResourceFolderType.getFolderType(resourceDirectory.name)
                if (folderResourceType == ResourceFolderType.VALUES) {
                    deleteValuesAttribute(listFiles)
                } else {
                    deleteDuplicateFiles(folderResourceType, listFiles)
                }
            }
        }
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
            val file = fileArray[i]
            if (file.isDirectory) {
                i++
                continue
            }

            // file可能不存在
            if (file.isFile && file.exists()) {
                for (fileName in mainFileSet) {
                    if (file.name === fileName) {
                        file.delete()
                        i--
                        FatUtils.logAnytime("Delete $fileName from the ${type.name}")
                        break
                    }
                }
            }
            i++
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
            e.printStackTrace()
        } catch (e: SAXException) {
            e.printStackTrace()
        }
        for (maybeResourceFile in fileArray) {
            if (maybeResourceFile.isDirectory) {
                continue
            }
            if (!maybeResourceFile.isFile) {
                throw ResourceDirectoryParseException(
                        "${maybeResourceFile.absolutePath} is not a file nor directory")
            }
            var wholeNode: Node? = null
            try {
                wholeNode = xmlParser?.parse(maybeResourceFile)
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: SAXException) {
                e.printStackTrace()
            }
            var removeCount = 0
            if (wholeNode != null) {
                val nodeList = NodeList()
                nodeList.addAll(wholeNode.children())
                for (it in nodeList) {
                    if (it is Node) {
                        if (it.name() != null) {
                            val hashSet: HashSet<String?>? = mMainValuesMap[it.name()]
                            if (hashSet != null) {
                                if (hashSet.contains(it.attribute("name"))) {
                                    removeCount += 1
                                    wholeNode.remove(it)
                                }
                            }
                        }
                    } else {
                        throw GroovyCastException("in the file ${maybeResourceFile.path}, ${it.toString()} cannot cast Node")
                    }
                }
            }
            if (removeCount > 0) {
                try {
                    Files.asCharSink(maybeResourceFile, Charsets.UTF_8).write(XmlUtil.serialize(wholeNode))
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }
}