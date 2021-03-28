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

    // values目录下文件中的可定义的节点类型
    private final String NODE_TYPE_STRING = "string";
    private final String NODE_TYPE_ATTR = "attr";
    private final String NODE_TYPE_ITEM = "item";
    private final String NODE_TYPE_ARRAY = "array";
    private final String NODE_TYPE_STYLE = "style";
    private final String NODE_TYPE_DIMEN = "dimen";
    private final String NODE_TYPE_COLOR = "color";
    private final String NODE_TYPE_BOOL = "bool";
    private final String NODE_TYPE_DECLARE_STYLEABLE = "declare-styleable";
    private final String NODE_TYPE_DRAWABLE = "drawable";
    private final String NODE_TYPE_EAT_COMMENT = "eat-comment";
    private final String NODE_TYPE_FRACTION = "fraction";
    private final String NODE_TYPE_INTEGER = "integer";
    private final String NODE_TYPE_INTEGER_ARRAY = "integer-array";
    private final String NODE_TYPE_PLURALS = "plurals";
    private final String NODE_TYPE_STRING_ARRAY = "string-array";

    private Project mProject

    /**
     * 主aar的res目录下的文件夹map，key是文件夹类型，value是该文件夹里面所有文件的文件名集合，除了values
     */
    private Map<ResourceFolderType, Set<String>> mMainDirectoryMap
    /**
     * 主aar的res/values目录下的所有文件中节点的map，key是节点名称，value是该类节点的集合
     */
    private Map<String, Set<String>> mMainValuesMap

    // 主aar的values目录下资源name集合
    private Set<String> mStringSet = new HashSet<>()
    private Set<String> mAttrSet = new HashSet<>()
    private Set<String> mItemSet = new HashSet<>()
    private Set<String> mArraySet = new HashSet<>()
    private Set<String> mStyleSet = new HashSet<>()
    private Set<String> mDimenSet = new HashSet<>()
    private Set<String> mColorSet = new HashSet<>()
    private Set<String> mBoolSet = new HashSet<>()
    private Set<String> mDeclareStyleableSet = new HashSet<>()
    private Set<String> mDrawableSet = new HashSet<>()
    private Set<String> mEatCommentSet = new HashSet<>()
    private Set<String> mFractionSet = new HashSet<>()
    private Set<String> mIntegerSet = new HashSet<>()
    private Set<String> mIntegerArraySet = new HashSet<>()
    private Set<String> mPluralsSet = new HashSet<>()
    private Set<String> mStringArraySet = new HashSet<>()

    /**
     * 构造函数
     */
    DuplicateResHandler(Project project) {
        mProject = project
        init();
    }

    /**
     * 初始化
     */
    private void init() {
        initMainDirectory()
        initMainValuesMap()
        iterateAllFiles(mProject.projectDir.path + "/src/main/res")
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

    private void initMainDirectory() {
        mMainDirectoryMap = new HashMap<>()
        mMainDirectoryMap.put(ResourceFolderType.ANIM, new HashSet<String>())
        mMainDirectoryMap.put(ResourceFolderType.ANIMATOR, new HashSet<String>())
        mMainDirectoryMap.put(ResourceFolderType.COLOR, new HashSet<String>())
        mMainDirectoryMap.put(ResourceFolderType.DRAWABLE, new HashSet<String>())
        mMainDirectoryMap.put(ResourceFolderType.FONT, new HashSet<String>())
        mMainDirectoryMap.put(ResourceFolderType.INTERPOLATOR, new HashSet<String>())
        mMainDirectoryMap.put(ResourceFolderType.LAYOUT, new HashSet<String>())
        mMainDirectoryMap.put(ResourceFolderType.MENU, new HashSet<String>())
        mMainDirectoryMap.put(ResourceFolderType.MIPMAP, new HashSet<String>())
        mMainDirectoryMap.put(ResourceFolderType.NAVIGATION, new HashSet<String>())
        mMainDirectoryMap.put(ResourceFolderType.RAW, new HashSet<String>())
        mMainDirectoryMap.put(ResourceFolderType.TRANSITION, new HashSet<String>())
        mMainDirectoryMap.put(ResourceFolderType.XML, new HashSet<String>())
    }

    private void initMainValuesMap() {
        mMainValuesMap = new HashMap<>()
        mMainValuesMap.put(NODE_TYPE_STRING, mStringSet)
        mMainValuesMap.put(NODE_TYPE_ATTR, mAttrSet)
        mMainValuesMap.put(NODE_TYPE_ITEM, mItemSet)
        mMainValuesMap.put(NODE_TYPE_ARRAY, mArraySet)
        mMainValuesMap.put(NODE_TYPE_STYLE, mStyleSet)
        mMainValuesMap.put(NODE_TYPE_DIMEN, mDimenSet)
        mMainValuesMap.put(NODE_TYPE_COLOR, mColorSet)
        mMainValuesMap.put(NODE_TYPE_BOOL, mBoolSet)
        mMainValuesMap.put(NODE_TYPE_DECLARE_STYLEABLE, mDeclareStyleableSet)
        mMainValuesMap.put(NODE_TYPE_DRAWABLE, mDrawableSet)
        mMainValuesMap.put(NODE_TYPE_EAT_COMMENT, mEatCommentSet)
        mMainValuesMap.put(NODE_TYPE_FRACTION, mFractionSet)
        mMainValuesMap.put(NODE_TYPE_INTEGER, mIntegerSet)
        mMainValuesMap.put(NODE_TYPE_INTEGER_ARRAY, mIntegerArraySet)
        mMainValuesMap.put(NODE_TYPE_PLURALS, mPluralsSet)
        mMainValuesMap.put(NODE_TYPE_STRING_ARRAY, mStringArraySet)
    }

    /**
     * 读取主aar的values目录下的文件
     */
    private void readMainAarValues(String path) {
        File[] files = new File(path).listFiles()

        for (File valueFile : files) {
            if (valueFile == null || !valueFile.isFile()) {
                continue
            }
            try {
                Node allNode = new XmlParser().parse(valueFile)
                if (allNode) {
                    NodeList nodeList = (NodeList) allNode.children()
                    if (nodeList) {
                        nodeList.each {
                            Node childNode = (Node) it
                            Set<String> set = mMainValuesMap.get(childNode.name())
                            if (set != null) {
                                set.add(childNode.attribute("name"))
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logLevel1(e.getMessage())
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
