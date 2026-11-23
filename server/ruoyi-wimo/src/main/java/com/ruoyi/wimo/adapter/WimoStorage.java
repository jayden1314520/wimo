package com.ruoyi.wimo.adapter;

/** 素材存储适配器：local（默认，本地磁盘）/ remote（对象存储网关） */
public interface WimoStorage
{
    String store(byte[] data, String originalName, Long userId) throws Exception;

    String provider();
}
