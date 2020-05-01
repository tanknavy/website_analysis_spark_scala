package com.tanknavy.spark.domain;

/**
 * 
 * @author admin
 * 自定义的，和官方同名类的javabean
 */
public class UserAgentInfo {
	
	private String browserName;
	private String browserVersion;
	private String osName;
	private String osVersion;
	
	
	public String getBrowserName() {
		return browserName;
	}
	public void setBrowserName(String browserName) {
		this.browserName = browserName;
	}
	public String getBrowserVersion() {
		return browserVersion;
	}
	public void setBrowserVersion(String browserVersion) {
		this.browserVersion = browserVersion;
	}
	public String getOsName() {
		return osName;
	}
	public void setOsName(String osName) {
		this.osName = osName;
	}
	public String getOsVersion() {
		return osVersion;
	}
	public void setOsVersion(String osVersion) {
		this.osVersion = osVersion;
	}
	
	@Override
	public String toString() {
		return "UserAgentInfo {browserName=" + browserName + ", browserVersion=" + browserVersion + ", osName=" + osName
				+ ", osVersion=" + osVersion + "}";
	}
	

	
}
