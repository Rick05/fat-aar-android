package com.kezong.fataar;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Tomze
 * @time 2021年03月26日 12:59
 * @desc
 */
public class FileUtils {

    /**
     * 获取文件夹下所有文件
     * @param filePath
     * @return
     */
    public static File[] getFileArray(String filePath) {
        File file = new File(filePath);
        return file.listFiles();
    }

    public static List<String> getFileNameArray(String filePath) {
        List<String> fileNameArray = new ArrayList<>();
        return getFileNameArray(fileNameArray, filePath, "");
    }

    /**
     * 获取文件夹下所有文件名
     * @param array 文件名称数组
     * @param filePath 文件路径
     * @param prefixPath 文件前缀 ##子目录下文件名前添加目录名称
     * @return
     */
    public static List<String> getFileNameArray(List<String> array, String filePath, String prefixPath) {
        File[] fileArray = getFileArray(filePath);
        if (fileArray == null) {
            return array;
        }
        for (File file: fileArray) {
            if (file.isDirectory()) {
                getFileNameArray(array, file.getPath(), prefixPath + File.separator + file.getName() + File.separator);
            } else {
                array.add(prefixPath + file.getName());
            }
        }
        return array;
    }
}
