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

    /**
     * 获取文件夹下所有文件名 **待扩展添加下级目录
     * @param filePath
     * @return
     */
    public static List<String> getFileNameArray(String filePath) {
        File[] fileArray = getFileArray(filePath);
        List<String> fileNameArray = new ArrayList<>();
        for (File file: fileArray) {
            if (file.isDirectory()) {
//                fileNameArray.addAll(getFileNameArray(file.getPath()));
            } else {
                fileNameArray.add(file.getName());
            }
        }
        return fileNameArray;
    }
}
