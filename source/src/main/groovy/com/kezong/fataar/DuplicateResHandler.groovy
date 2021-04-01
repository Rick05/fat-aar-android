package com.kezong.fataar

import com.android.ide.common.symbols.ResourceDirectoryParseException
import com.android.resources.ResourceFolderType
import com.google.common.base.Charsets
import com.google.common.io.Files
import groovy.xml.XmlUtil
import org.apache.http.util.TextUtils
import org.gradle.api.Project

/**
 * 处理重复资源的类，重复资源以主aar为准，其中values目录下的文件需要特殊处理,其他同级的目录暂时不需要处理
 */
class DuplicateResHandler {

    private Project mProject

    /**
     * 主aar的res目录下的文件夹map，key是文件夹类型，value是该文件夹里面所有文件的文件名集合，除了values
     */
    private Map<ResourceFolderType, HashSet<String>> mMainDirectoryMap = new HashMap<>()

    /**
     * 主aar的res/values目录下的所有文件中节点的map，key是节点名称，value是该类节点的集合
     */
    private Map<String, HashSet<String>> mMainValuesMap = new HashMap<>()

    private Set<String> mMainAssetsSet = new HashSet<>()

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
        iterateResFiles(mainResPath)
        String mainAssetsPath = mProject.projectDir.path + File.separator + "src" + File.separator + "main" + File.separator + "assets"
        iterateAssetsFiles(mainAssetsPath)
    }

    /**
     * 遍历Res文件
     */
    private void iterateResFiles(String resPath) {
        for (file in FileUtils.getFileArray(resPath)) {
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
                readMainValues(file.path)
            } else {
                readMainResDirectory(type, file)
            }
        }
    }

    /**
     * 遍历Assets文件
     */
    private void iterateAssetsFiles(String assetsPath) {
        for (fileName in FileUtils.getFileNameArray(assetsPath)) {
            mMainAssetsSet.add(fileName)
        }
    }

    /**
     * 删除与主包重复的assets文件
     * @param path
     */
    void deleteDuplicateAssetsFiles(String path) {
        if (TextUtils.isEmpty(path)) {
            return
        }
        List<String> aarFileNameArray = FileUtils.getFileNameArray(path)
        for (fileName in aarFileNameArray) {
            if (mMainAssetsSet.contains(fileName)) {
                File file = new File(path + File.separator + fileName)
                if(file.exists()) {
                    file.delete()
                }
                FatUtils.logAnytime("Delete ${fileName} from the ${path}")
            }
        }
    }

    /**
     * 读取主包的values目录下的文件
     */
    private void readMainValues(String path) {
        XmlParser xmlParser = new XmlParser()
        // 按类型取出values目录下所有的key，进行保存
        for (File resFile : FileUtils.getFileArray(path)) {
            Node allNode = xmlParser.parse(resFile)
            if (allNode != null) {
                NodeList nodeList = (NodeList)allNode.children()
                if (nodeList) {
                    nodeList.each { it ->
                        Node node = (Node) it
                        HashSet<String> hashSet = mMainValuesMap.get(node.name())
                        if (hashSet == null) {
                            hashSet = new HashSet<>()
                            mMainValuesMap.put(node.name(), hashSet)
                        }
                        hashSet.add(node.attributes().get("name"))
                    }
                }
            }
        }
    }

    /**
     * 读取主包的res目录下各个文件夹中的文件列表
     */
    private void readMainResDirectory(ResourceFolderType type, File file) {
        Set<String> fileNameSet = mMainDirectoryMap.get(type)
        if (fileNameSet == null) {
            fileNameSet = new HashSet<>()
            mMainDirectoryMap.put(type, fileNameSet)
        }
        file.listFiles().each { childFile ->
            fileNameSet.add(childFile.name)
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
            if (file.isFile() && file.exists()) {
                for (fileName in mainFileSet) {
                    if (file.name == fileName) {
                        file.delete()
                        i--
                        FatUtils.logAnytime("Delete ${fileName} from the ${type.name}")
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
        XmlParser xmlParser = new XmlParser()
        for (File maybeResourceFile : fileArray) {
            if (maybeResourceFile.isDirectory()) {
                continue
            }
            if (!maybeResourceFile.isFile()) {
                throw new ResourceDirectoryParseException(
                         "${maybeResourceFile.absolutePath} is not a file nor directory")
            }

            Node wholeNode = xmlParser.parse(maybeResourceFile)
            int removeCount = 0
            if (wholeNode != null) {
                NodeList nodeList = new NodeList()
                nodeList.addAll(wholeNode.children())
                nodeList.each {
                    Node childNode = (Node) it
                    if (childNode != null) {
                        HashSet<String> hashSet = mMainValuesMap.get(childNode.name())
                        if (hashSet != null) {
                            if (hashSet.contains(childNode.attribute("name"))) {
                                removeCount += 1
                                wholeNode.remove(childNode)
                            }
                        }
                    }
                }
            }
            if (removeCount > 0) {
                FatUtils.logAnytime("Delete ${removeCount} values from ${maybeResourceFile.name}")
                Files.asCharSink(maybeResourceFile, Charsets.UTF_8).write(XmlUtil.serialize(wholeNode))
            }
        }
    }
}
