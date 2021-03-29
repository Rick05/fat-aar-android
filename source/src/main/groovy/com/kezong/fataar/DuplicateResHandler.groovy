package com.kezong.fataar

import com.android.ide.common.symbols.ResourceDirectoryParseException
import com.android.resources.ResourceFolderType
import com.google.common.base.Charsets
import com.google.common.io.Files
import groovy.xml.XmlUtil
import org.gradle.api.Project

/**
 * 处理重复资源的类，重复资源以主aar为准，其中values目录下的文件需要特殊处理,其他同级的目录暂时不需要处理
 */
class DuplicateResHandler {

    private Project mProject

    /**
     * 主aar的res/values目录下的所有文件中节点的map，key是节点名称，value是该类节点的集合
     */
    private Map<String, Set<String>> mMainValuesMap

    /**
     * 构造函数
     */
    DuplicateResHandler(Project project) {
        mProject = project
        init()
    }

    /**
     * 初始化
     */
    private void init() {
        String mainResPath = mProject.projectDir.path + File.separator + "src" + File.separator + "main" + File.separator + "res"
        iterateAllFiles(mainResPath)
    }

    /**
     * 遍历所有文件
     */
    private void iterateAllFiles(String resPath) {
        File[] files = new File(resPath).listFiles()
        for (file in files) {
            if (file == null || !file.isDirectory()) {
                continue
            }
            assert (file.isDirectory())
            ResourceFolderType type = ResourceFolderType.getFolderType(file.name)
            if (type == null) {
                continue
            }
            if (type == ResourceFolderType.VALUES) {
                // 处理values目录
                readMainAarValues(file.path)
            } else {
                Set<String> fileNameSet = mMainDirectoryMap.get(type)
                if (fileNameSet != null) {
                    File[] childFiles = file.listFiles()
                    childFiles.each { childFile ->
                        fileNameSet.add(childFile.name)
                    }
                }
            }
        }
    }

    /**
     * 读取主aar的values目录下的文件
     */
    private void readMainAarValues(String path) {
        XmlParser xmlParser = new XmlParser()
        // 按类型取出values目录下所有的key，进行保存
        for (File resFile : FileUtils.getFileArray(path)) {
            Node allNode = xmlParser.parse(resFile)
            if (allNode != null) {
                NodeList nodeList = (NodeList)allNode.value()
                if (nodeList) {
                    nodeList.each { node ->
                        Set<String> hashSet = mMainValuesMap.get(node.name())
                        if (hashSet == null) {
                            hashSet = new HashSet<>()
                            mMainResMap.put(node.name(), hashSet)
                        }
                        hashSet.add(node.attributes().get("name"))
                    }
                }
            }
        }
    }

    /**
     * 删除重复资源
     * @param aarPath 跟目录
     */
    void deleteDuplicateRes(String aarPath) {
        File[] files = new File(aarPath).listFiles()
        if (files == null) {
            return
        }

        for (File resourceDirectory : files) {
            if (!resourceDirectory.isDirectory()) {
                throw new ResourceDirectoryParseException(
                        resourceDirectory.getAbsolutePath() + " is not a directory")
            }

            assert (resourceDirectory.isDirectory())
            File[] listFiles = resourceDirectory.listFiles()
            if (listFiles == null) {
                continue
            }

            ResourceFolderType folderResourceType = ResourceFolderType.getFolderType(resourceDirectory.getName())
            if (folderResourceType == ResourceFolderType.VALUES) {
                deleteValuesAttribute(listFiles)
            } else {
                deleteDuplicateFiles(folderResourceType, listFiles)
            }
        }
    }

    /**
     * 删除与主aar中重名的文件，除了values目录
     * @param type 文件类型
     * @param fileArray 文件数组
     */
    private void deleteDuplicateFiles(ResourceFolderType type, File[] fileArray) {
        Set<String> mainFileSet = mMainDirectoryMap.get(type)
        if (mainFileSet == null || mainFileSet.isEmpty()) {
            return
        }

        for (int i = 0; i < fileArray.length; i++) {
            File file = fileArray[i]
            if (file.isDirectory()) {
                continue
            }

            // file可能不存在
            if (file.isFile()) {
                for (fileName in mainFileSet) {
                    if (file.name == fileName) {
                        file.delete()
                        i--
                        break
                    }
                }
            }
        }
    }

    /**
     * 在values.xml文件中删除与主aar中重名的资源
     * @param fileArray 文件数组
     */
    private void deleteValuesAttribute(File[] fileArray) {
        for (File maybeResourceFile : fileArray) {
            if (maybeResourceFile.isDirectory()) {
                continue
            }

            if (!maybeResourceFile.isFile()) {
                throw new ResourceDirectoryParseException(
                        "${maybeResourceFile.absolutePath} is not a file nor directory")
            }

            Node wholeNode = new XmlParser().parse(maybeResourceFile)
            int removeCount = 0

            NodeList nodeList = new NodeList()
            nodeList.addAll(wholeNode.children())
            nodeList.each {
                Node childNode = (Node) it
                if (childNode != null && mMainValuesMap.get(childNode.name()) != null
                        && mMainValuesMap.get(childNode.name()).contains(childNode.attribute("name"))) {
                    wholeNode.remove(childNode)
                    removeCount += 1
                }
            }

            if (removeCount > 0) {
                logLevel2 "Delete " + removeCount + " values..."
                Files.asCharSink(maybeResourceFile, Charsets.UTF_8).write(XmlUtil.serialize(wholeNode))
            }
        }
    }

    def logLevel1(Object value) {
        println ">> " + value
    }

    def logLevel2(Object value) {
        println "   " + value
    }
}
