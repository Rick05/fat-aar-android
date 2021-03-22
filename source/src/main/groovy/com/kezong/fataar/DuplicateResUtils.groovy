package com.kezong.fataar


import com.android.ide.common.symbols.ResourceDirectoryParseException
import com.android.ide.common.symbols.Symbol
import com.android.ide.common.symbols.SymbolTable
import com.android.ide.common.symbols.SymbolUtils
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.google.common.base.Charsets
import com.google.common.io.Files
import groovy.transform.CompileStatic
import groovy.xml.XmlUtil
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor

import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS
import static org.objectweb.asm.Opcodes.*

@CompileStatic
class DuplicateResUtils {

    public static String NODE_TYPE_STRING = "string";
    public static String NODE_TYPE_COLOR = "color";


    static def logLevel1(Object value) {
        println ">> " + value
    }

    static def logLevel2(Object value) {
        println "   " + value
    }

    /**
     * Delete attribute from values.xml
     * @param aarPath The root directory
     */
    static void deleteResAttribute(String aarPath, Set<String> stringSet, Set<String> colorSet) {
        File[] files = new File(aarPath).listFiles()
        if (files == null) return

        for (File resourceDirectory : files) {
            if (!resourceDirectory.isDirectory()) {
                throw new ResourceDirectoryParseException(
                        resourceDirectory.getAbsolutePath() + " is not a directory")
            }

            assert (resourceDirectory.isDirectory())

            ResourceFolderType folderResourceType = ResourceFolderType.getFolderType(resourceDirectory.getName())
            if (folderResourceType != ResourceFolderType.VALUES) continue

            // Iterate all files in the resource directory and handle each one.
            File[] listFiles = resourceDirectory.listFiles()
            if (listFiles == null) continue

            for (File maybeResourceFile : listFiles) {
                if (maybeResourceFile.isDirectory()) continue

                if (!maybeResourceFile.isFile()) {
                    throw new ResourceDirectoryParseException(
                            "${maybeResourceFile.absolutePath} is not a file nor directory")
                }

                Node node = new XmlParser().parse(maybeResourceFile)
                int removeCount = 0
                removeCount += compareAndDeleteNode(node, (NodeList) node.get(NODE_TYPE_STRING), stringSet)
                removeCount += compareAndDeleteNode(node, (NodeList) node.get(NODE_TYPE_COLOR), colorSet)

                if (removeCount > 0) {
                    logLevel2 "Delete " + removeCount + " values..."
                    Files.asCharSink(maybeResourceFile, Charsets.UTF_8).write(XmlUtil.serialize(node))
                }
            }
        }
    }

    /**
     * 比较节点，并删除重复的节点
     * @param wholeNode 所有节点
     * @param nodeList 制定比较的节点列表
     * @param set 比较的参照集合
     * @return 删除的节点数量
     */
    private static int compareAndDeleteNode(Node wholeNode, NodeList nodeList, Set<String> set) {
        if (!nodeList) {
            return 0
        }
        int removeCount;
        nodeList.each {
            Node childNode = (Node) it
            if (childNode != null && set.contains(childNode.attribute("name"))) {
                wholeNode.remove(childNode)
                removeCount += 1
            }
        }
        return removeCount
    }

    static int compareAndroidGradleVersion(String v1, String v2) {
        if (v1.isEmpty() || v2.isEmpty()) throw new Exception("Unable to compare empty version!")

        String[] str1 = v1.split("\\.")
        String[] str2 = v2.split("\\.")
        int minLength = str1.length <= str2.length ? str1.length : str2.length

        for (int index = 0; index < minLength; index++) {
            String s1 = str1[index]
            String s2 = str2[index]
            if (s1 != s2) return Long.valueOf(s1) > Long.valueOf(s2) ? 1 : -1
        }

        if (str1.length > minLength) {
            if (Long.valueOf(str1[minLength]) < 0)
                throw new Exception("Version [$v1] may be incorrect")
            return 1
        }

        if (str2.length > minLength) {
            if (Long.valueOf(str2[minLength]) < 0)
                throw new Exception("Version [$v2] may be incorrect")
            return -1
        }

        return 0
    }

    static byte[] generateOuterRClass(EnumSet<ResourceType> resourceTypes, String packageR) {
        ClassWriter cw = new ClassWriter(COMPUTE_MAXS)
        cw.visit(
                V1_8,
                ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
                packageR, null,
                "java/lang/Object", null)

        for (rt in resourceTypes) {
            cw.visitInnerClass(
                    packageR + "\$" + rt.getName(),
                    packageR,
                    rt.getName(),
                    ACC_PUBLIC + ACC_FINAL + ACC_STATIC)
        }

        // Constructor
        MethodVisitor mv = cw.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null)
        mv.visitCode()
        mv.visitVarInsn(ALOAD, 0)
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        mv.visitInsn(RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()

        cw.visitEnd()

        return cw.toByteArray()
    }

    static byte[] generateResourceTypeClass(SymbolTable table, String packageName, String targetPackageName, ResourceType resType) {
        List<Symbol> symbols = table.getSymbolByResourceType(resType)
        if (symbols.isEmpty()) {
            return null
        }

        ClassWriter cw = new ClassWriter(COMPUTE_MAXS)
        String internalName = generateInternalName(packageName, resType)
        cw.visit(
                V1_8,
                ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
                internalName, null,
                "java/lang/Object", null)

        cw.visitInnerClass(
                internalName,
                generateInternalName(packageName, null),
                resType.getName(),
                ACC_PUBLIC + ACC_FINAL + ACC_STATIC)

        for (s in symbols) {
            cw.visitField(
                    ACC_PUBLIC + ACC_STATIC,
                    s.name,
                    s.javaType.desc,
                    null,
                    null
            )

            if (s instanceof Symbol.StyleableSymbol) {
                List<String> children = s.children
                for (int i = 0; i < children.size(); i++) {
                    String child = children.get(i)

                    cw.visitField(
                            ACC_PUBLIC + ACC_STATIC,
                            "${s.name}_${SymbolUtils.canonicalizeValueResourceName(child)}",
                            "I",
                            null,
                            null)
                }
            }
        }

        // Constructor
        MethodVisitor init = cw.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null)
        init.visitCode()
        init.visitVarInsn(ALOAD, 0)
        init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        init.visitInsn(RETURN)
        init.visitMaxs(0, 0)
        init.visitEnd()

        // init method
        MethodVisitor clinit = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null)
        clinit.visitCode()
        for (s in symbols) {

            String targetInternalName = generateInternalName(targetPackageName, resType)
            clinit.visitFieldInsn(GETSTATIC, targetInternalName, s.name, s.javaType.desc)
            clinit.visitFieldInsn(PUTSTATIC, internalName, s.name, s.javaType.desc)

            if (s instanceof Symbol.StyleableSymbol) {
                s.children.each {
                    String name = "${s.name}_${SymbolUtils.canonicalizeValueResourceName(it)}"
                    clinit.visitFieldInsn(GETSTATIC, targetInternalName, name, "I")
                    clinit.visitFieldInsn(PUTSTATIC, internalName, name, "I")
                }
            }
        }
        clinit.visitInsn(RETURN)
        clinit.visitMaxs(0, 0)
        clinit.visitEnd()

        cw.visitEnd()

        return cw.toByteArray()
    }

    static String generateInternalName(String packageName, ResourceType type) {
        String className
        if (type == null) {
            className = "R"
        } else {
            className = "R\$" + type.getName()
        }

        if (packageName.isEmpty()) {
            return className
        } else {
            return packageName.replace(".", "/") + "/" + className
        }
    }
}
