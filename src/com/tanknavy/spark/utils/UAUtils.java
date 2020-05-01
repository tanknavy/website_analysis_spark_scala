package com.tanknavy.spark.utils;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;

import com.tanknavy.spark.domain.UserAgentInfo;
import cz.mallat.uasparser.OnlineUpdater;
import cz.mallat.uasparser.UASparser;

//https://geo.ipify.org/docs  地址解析
public class UAUtils {
	
	// 单例使用
	private static UASparser parser = null;
	// 静态代码块初始化赋值
	static {
		try {
			parser = new UASparser(OnlineUpdater.getVendoredInputStream());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	//管方解析UserAgent转为自己的需要的字段
	public static UserAgentInfo getUserAgentInfo(String ua){
		UserAgentInfo info = null;  // 没有结果则为空
		try {
			if(StringUtils.isNoneEmpty(ua)){ // org.apache.commons.lang3.StringUtils解析
				cz.mallat.uasparser.UserAgentInfo tmp = parser.parse(ua);
				
				if(tmp != null){
					info = new UserAgentInfo();
					info.setBrowserName(tmp.getUaFamily());
					info.setBrowserVersion(tmp.getBrowserVersionInfo());
					info.setOsName(tmp.getOsFamily());
					info.setOsVersion(tmp.getOsName());
				}
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return info;
	}
	
	/*
	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub
	
		//cz.mallat.uasparser.UserAgentInfo info = parser.parse("Mozilla/4.0 (compatible; MSIE 7.0; Windows NT 5.1; )");
		UserAgentInfo info = getUserAgentInfo("Mozilla/4.0 (compatible; MSIE 7.0; Windows NT 5.1; )");//使用自己的类
        System.out.println(info);
	}
	*/
}
