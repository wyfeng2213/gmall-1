package com.atgugu.gmall.manage.controller;

import org.apache.commons.lang3.StringUtils;
import org.csource.common.MyException;
import org.csource.fastdfs.ClientGlobal;
import org.csource.fastdfs.StorageClient;
import org.csource.fastdfs.TrackerClient;
import org.csource.fastdfs.TrackerServer;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * @Auther: wangyong
 * @Date: 2019/8/6
 * @Description:
 *
 */
public class FileUtil {
    public static String getImagePath(MultipartFile file) {
        if (file != null) {
            System.out.println("multipartFile = " + file.getName() + "|" + file.getSize());
        }
        String conf = FileUtil.class.getResource("/tracker.conf").getFile();
        String[] upload_file = null;
        try {
            ClientGlobal.init(conf);
            TrackerClient trackerClient = new TrackerClient();
            TrackerServer trackerServer = trackerClient.getConnection();
            // null 不指定storageSserver , 也可以指定
            StorageClient storageClient = new StorageClient(trackerServer, null);
            //获取文件后缀
            String filename = file.getOriginalFilename();
            String extName = StringUtils.substringAfterLast(filename, ".");
            // 返回组名group和linux image路径
            upload_file = storageClient.upload_file(file.getBytes(), extName, null);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (MyException e) {
            e.printStackTrace();
        }

        String imageUrl = "";
        if (upload_file == null) {
            return imageUrl;
        }
        for (int i = 0; i < upload_file.length; i++) {
            String s = upload_file[i];
            System.out.println("s = " + s);
            imageUrl += "/" + s;
        }
        //利用@Value 标签可以引用 application.properties 中的值
        //@Value("${fileServer.url}")
        //String fileUrl;
        //return fileUrl+imageUrl;
        return "http://192.168.81.128" + imageUrl;
    }
}
