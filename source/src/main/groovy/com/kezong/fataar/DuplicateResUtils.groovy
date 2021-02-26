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

    static def logLevel1(Object value) {
        println ">> " + value
    }

    static def logLevel2(Object value) {
        println "   " + value
    }

    /**
     * Delete app_name attribute from values.xml
     * @param aarPath The root directory
     */
    static void deleteAppNameAttribute(Set<String> mainStringNameSet, String aarPath) {
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
                NodeList string_node = (NodeList) node.get("string")

                int removeCount = 0

                if (string_node) {
                    string_node.each {
                        Node childNode = (Node) it
                        if (mainStringNameSet.contains(childNode.attribute("name"))) {
                            node.remove(childNode)
                            removeCount++
                        }
//                        if (childNode.attribute("name") in mainStringNameList) {
//                            logLevel2 "Found value [app_name] in [" + maybeResourceFile.getAbsolutePath() + "]"
//                            node.remove(childNode)
//                            removeCount++
//                        }
                    }
                }

                if (removeCount > 0) {
                    logLevel2 "Delete " + removeCount + " values..."
                    Files.asCharSink(maybeResourceFile, Charsets.UTF_8).write(XmlUtil.serialize(node))
                }
            }
        }
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
