package com.teriteri.backend.utils;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.UUID;

@Slf4j
@Component
public class OssUtil {
    @Value("${oss.bucket}")
    private String OSS_BUCKET;

    @Value("${oss.bucketUrl}")
    private String OSS_BUCKET_URL;

    @Value("${directory.chunk}")
    private String CHUNK_DIRECTORY;   // 分片存储目录

    @Autowired
    private OSS ossClient;

    private static final List<String> IMAGE_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png");
    private static final List<String> VIDEO_EXTENSIONS = Collections.singletonList("mp4");
    private static final int MAX_MULTIPART_PARTS = 10000;

    /**
     * 往阿里云对象存储上传单张图片
     * @param file 图片文件
     * @param type 图片类型：COVER / AVATAR
     * @return  图片的URL地址
     * @throws IOException
     */
    public String uploadImage(@NonNull MultipartFile file, @NonNull FileType type) throws IOException {
        if (type != FileType.COVER && type != FileType.AVATAR) {
            throw new IllegalArgumentException("uploadImage 只支持 COVER / AVATAR");
        }
        if (file.isEmpty()) {
            throw new IOException("文件为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new IOException("文件名不能为空");
        }
        String ext = FilenameUtils.getExtension(originalFilename);
        if (ext == null || ext.isEmpty()) {
            throw new IOException("文件缺少扩展名");
        }
        ext = ext.toLowerCase(Locale.ROOT);
        if (!IMAGE_EXTENSIONS.contains(ext)) {
            throw new IOException("不支持的图片格式: " + ext);
        }

        // 生成文件名
        String uuid = System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "");
        String fileName = uuid + "." + ext;
        // 完整路径名
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date()).replace("-", "");
        String filePathName = date + "/img/" + type.getType() + "/" + fileName;
        try (InputStream in = file.getInputStream()) {
            ossClient.putObject(
                    OSS_BUCKET, // 仓库名
                    filePathName,   // 文件名（含路径）
                    in   // 数据流
            );
        } catch (OSSException oe) {
            log.error("OSS出错了:" + oe.getErrorMessage());
            throw oe;
        } catch (ClientException ce) {
            log.error("OSS连接出错了:" + ce.getMessage());
            throw ce;
        }
        return OSS_BUCKET_URL + filePathName;
    }

    /**
     * 往阿里云对象存储上传单个视频，简单上传，使用了 putObject()，将整个视频作为一个对象一次性上传，最大 5GB
     * @param file 视频文件
     * @return  视频的URL地址
     * @throws IOException
     */
    public String uploadVideo(@NonNull MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("文件为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new IOException("文件名不能为空");
        }
        String ext = FilenameUtils.getExtension(originalFilename);
        if (ext == null || ext.isEmpty()) {
            throw new IOException("文件缺少扩展名");
        }
        ext = ext.toLowerCase(Locale.ROOT);
        if (!VIDEO_EXTENSIONS.contains(ext)) {
            throw new IOException("不支持的视频格式: " + ext);
        }

        // 生成文件名
        String uuid = System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "");
        String fileName = uuid + "." + ext;
        // 完整路径名
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date()).replace("-", "");
        String filePathName = date + "/" +  FileType.VIDEO.getType() + "/" + fileName;
        try (InputStream in = file.getInputStream()) {
            ossClient.putObject(
                    OSS_BUCKET, // 仓库名
                    filePathName,   // 文件名（含路径）
                    in   // 数据流
            );
        } catch (OSSException oe) {
            log.error("OSS出错了:" + oe.getErrorMessage());
            throw oe;
        } catch (ClientException ce) {
            log.error("OSS连接出错了:" + ce.getMessage());
            throw ce;
        }
        return OSS_BUCKET_URL + filePathName;
    }

    /**
     * 将本地分片按 OSS Multipart Upload 合并成视频对象。
     * 分片仍保存在本地 public/chunk/，合并完成后才删除。
     * @param hash 视频的 hash 值，用于检索对应分片
     * @return 视频在 OSS 的 URL；没有分片时返回 null
     * @throws IOException 分片不连续、分片为空或 OSS 操作失败
     */
    public String multipartUploadVideo(@NonNull String hash) throws IOException {
        if (!hash.matches("^[a-zA-Z0-9]{1,64}$")) {
            log.error("hash值不合法，已拒绝合并分片");
            return null;
        }

        List<File> chunks = listContiguousChunks(hash);
        if (chunks.isEmpty()) {
            log.error("没找到任何相关分片文件");
            return null;
        }
        if (chunks.size() > MAX_MULTIPART_PARTS) {
            throw new IOException("分片数量超过 OSS Multipart 上限: " + MAX_MULTIPART_PARTS);
        }

        // 生成最终对象 key
        String uuid = System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "");
        String fileName = uuid + ".mp4";
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date()).replace("-", "");
        String filePathName = date + "/" + FileType.VIDEO.getType() + "/" + fileName;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("video/mp4");

        String uploadId = null;
        List<PartETag> partETags = new ArrayList<>();
        try {
            InitiateMultipartUploadRequest initiateRequest =
                    new InitiateMultipartUploadRequest(OSS_BUCKET, filePathName, metadata);
            uploadId = ossClient.initiateMultipartUpload(initiateRequest).getUploadId();

            for (int i = 0; i < chunks.size(); i++) {
                File chunkFile = chunks.get(i);
                if (chunkFile.length() == 0) {
                    throw new IOException("分片为空: " + chunkFile.getName());
                }

                UploadPartRequest partRequest = new UploadPartRequest();
                partRequest.setBucketName(OSS_BUCKET);
                partRequest.setKey(filePathName);
                partRequest.setUploadId(uploadId);
                partRequest.setPartNumber(i + 1);   // OSS partNumber 从 1 开始
                partRequest.setPartSize(chunkFile.length());

                try (InputStream in = new FileInputStream(chunkFile)) {
                    partRequest.setInputStream(in);
                    UploadPartResult partResult = ossClient.uploadPart(partRequest);
                    partETags.add(partResult.getPartETag());
                }
            }

            CompleteMultipartUploadRequest completeRequest =
                    new CompleteMultipartUploadRequest(OSS_BUCKET, filePathName, uploadId, partETags);
            ossClient.completeMultipartUpload(completeRequest);
        } catch (Exception e) {
            if (uploadId != null) {
                try {
                    ossClient.abortMultipartUpload(
                            new AbortMultipartUploadRequest(OSS_BUCKET, filePathName, uploadId));
                } catch (Exception abortException) {
                    log.error("取消 OSS 多段上传失败，uploadId=" + uploadId, abortException);
                }
            }
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("OSS 分片合并失败", e);
        }

        // 本地分片不在这里删除：等调用方把 MySQL 写成功后再删，
        // 避免"OSS 已合并、数据库失败、本地分片又没了"导致无法重试。
        return OSS_BUCKET_URL + filePathName;
    }

    /**
     * 扫描并校验本地分片：必须从 0 开始连续编号。
     * 中间缺片时直接抛异常，避免静默生成截断视频。
     */
    private List<File> listContiguousChunks(String hash) throws IOException {
        File chunkDir = new File(CHUNK_DIRECTORY);
        File[] files = chunkDir.listFiles((dir, name) -> name.startsWith(hash + "-"));
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }

        TreeMap<Integer, File> indexToFile = new TreeMap<>();
        for (File file : files) {
            String name = file.getName();
            String suffix = name.substring(hash.length() + 1);
            int index;
            try {
                index = Integer.parseInt(suffix);
            } catch (NumberFormatException e) {
                throw new IOException("非法分片文件名: " + name, e);
            }
            if (index < 0) {
                throw new IOException("非法分片序号: " + name);
            }
            if (indexToFile.put(index, file) != null) {
                throw new IOException("重复分片序号: " + index);
            }
        }

        int expected = 0;
        for (Integer index : indexToFile.keySet()) {
            if (index != expected) {
                throw new IOException("分片不连续，缺少分片 " + expected);
            }
            expected++;
        }
        return new ArrayList<>(indexToFile.values());
    }

    /**
     * 删除某个 hash 对应的全部本地分片。
     * 由调用方在数据库写入成功后调用，避免"OSS 已合并、数据库失败、本地分片又没了"。
     */
    public void deleteLocalChunks(@NonNull String hash) {
        File chunkDir = new File(CHUNK_DIRECTORY);
        File[] files = chunkDir.listFiles((dir, name) -> name.startsWith(hash + "-"));
        if (files == null || files.length == 0) {
            return;
        }
        for (File file : files) {
            if (!file.delete()) {
                log.warn("本地分片删除失败: {}", file.getAbsolutePath());
            }
        }
    }

    /**
     * 按 OSS URL 删除单个对象（best effort）。
     * 用于"OSS 合并成功、但数据库写入失败"时清理刚生成的对象。
     */
    public void deleteObjectByUrl(String url) {
        if (url == null || !url.startsWith(OSS_BUCKET_URL)) {
            log.warn("不是当前 bucket 的 URL，跳过删除: {}", url);
            return;
        }
        String key = url.substring(OSS_BUCKET_URL.length());
        try {
            ossClient.deleteObject(OSS_BUCKET, key);
            log.info("已删除 OSS 对象: {}", key);
        } catch (OSSException oe) {
            log.error("删除 OSS 对象失败: " + key + ", " + oe.getErrorMessage());
        } catch (ClientException ce) {
            log.error("删除 OSS 对象连接失败: " + key + ", " + ce.getMessage());
        }
    }

    /**
     * 往阿里云对象存储上传单个视频分片文件，在阿里云上设置了3天的生命周期，超过三天未合并使用的分片将被自动删除
     * 建议只在分布式系统使用，单体系统还是存在本地更好存取以及节省流量
     * @param file  分片文件
     * @param name  分片名，以 hash-index 命名
     * @return  true：分片已存在，无需上传  false：分片不存在，上传成功
     * @throws IOException
     */
    // tk：doesObjectExist()：判断阿里云 oss 上是否有这个分片文件
    public boolean ensureChunkExists(@NonNull MultipartFile file, @NonNull String name) throws IOException {
        String fileName = "chunk/" + name;  // 分片文件在OSS的存储路径名
        ObjectMetadata metadata = new ObjectMetadata();
        // 对象已存在时让 OSS 返回 FileAlreadyExists，而不是静默覆盖
        metadata.setHeader("x-oss-forbid-overwrite", true);
        try (InputStream in = file.getInputStream()) {
            ossClient.putObject(OSS_BUCKET, fileName, in, metadata);
            return false; // 上传成功（之前不存在）
        } catch (OSSException oe) {
            if ("FileAlreadyExists".equals(oe.getErrorCode())) {
                return true; // 已存在，无需上传
            }
            log.error("OSS出错了:" + oe.getErrorMessage());
            throw oe;
        } catch (ClientException ce) {
            log.error("OSS连接出错了:" + ce.getMessage());
            throw ce;
        }
    }

    /**
     * 查询指定目录下，指定前缀的文件数量，阿里云限制了单次查询最多100条
     * @param prefix  要筛选的文件名前缀，包括目录路径，如果为空字符串，则查询全部文件
     * @return  指定目录下，指定前缀的文件数量
     */
    public int countFiles(@NonNull String prefix) {
        int count = 0;
        String nextMarker = null;
        try {
            ObjectListing objectListing;
            do {
                ListObjectsRequest listObjectsRequest = new ListObjectsRequest(OSS_BUCKET)
                        .withPrefix(prefix)
                        .withMarker(nextMarker);
                objectListing = ossClient.listObjects(listObjectsRequest);
                count += objectListing.getObjectSummaries().size();
                nextMarker = objectListing.getNextMarker();
            } while (objectListing.isTruncated());
        } catch (OSSException oe) {
            log.error("OSS出错了:" + oe.getErrorMessage());
            throw oe;
        } catch (ClientException ce) {
            log.error("OSS连接出错了:" + ce.getMessage());
            throw ce;
        }
        return count;
    }

    /**
     * 删除指定目录下指定前缀的所有文件，不允许目录和前缀都是空字符串
     * @param prefix    要筛选的文件名前缀，包括目录路径，不允许为空字符串
     */
    public void deleteFiles(@NonNull String prefix) {
        if (prefix.equals("")) {
            log.warn("你正试图删除整个bucket，已拒绝该危险操作");
            return;
        }
        try {
            // 列举所有包含指定前缀的文件并删除。
            String nextMarker = null;
            ObjectListing objectListing;
            do {
                ListObjectsRequest listObjectsRequest = new ListObjectsRequest(OSS_BUCKET).withPrefix(prefix).withMarker(nextMarker);
                objectListing = ossClient.listObjects(listObjectsRequest);
                if (objectListing.getObjectSummaries().size() > 0) {
                    List<String> keys = new ArrayList<>();
                    for (OSSObjectSummary s : objectListing.getObjectSummaries()) {
                        keys.add(s.getKey());
                    }
                    DeleteObjectsRequest deleteObjectsRequest = new DeleteObjectsRequest(OSS_BUCKET).withKeys(keys).withEncodingType("url");
                    DeleteObjectsResult deleteObjectsResult = ossClient.deleteObjects(deleteObjectsRequest);
                    List<String> deletedObjects = deleteObjectsResult.getDeletedObjects();
                    log.info("删除 OSS 对象 {} 个，prefix={}", deletedObjects.size(), prefix);
                    //  逐个打印已删除文件
                    /*try {
                        for(String obj : deletedObjects) {
                            String deleteObj =  URLDecoder.decode(obj, "UTF-8");
                           log.info("删除文件：" + deleteObj);
                        }
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }*/
                }
                nextMarker = objectListing.getNextMarker();
            } while (objectListing.isTruncated());
        } catch (OSSException oe) {
            log.error("OSS出错了:" + oe.getErrorMessage());
        } catch (ClientException ce) {
            log.error("OSS连接出错了:" + ce.getMessage());
        }
    }
}
