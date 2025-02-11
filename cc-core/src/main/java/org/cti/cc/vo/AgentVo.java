package org.cti.cc.vo;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Created by caoliang on 2021/4/21
 */
public class AgentVo {

    private Long id;

    /**
     * 企业ID
     */
    private Long companyId;

    /**
     * 坐席工号
     */
    private Long agentId;

    /**
     * 坐席账户
     */
    @NotNull(message = "坐席不能为空")
    @Size(min = 4, max = 16, message = "坐席工号必须在4,16个字符")
    private String agentKey;

    /**
     * rest 接口回调地址
     */
    @NotNull(message = "回调地址不能为空")
    @Size(min = 15, max = 150, message = "回调地址超过150字符限制")
    private String callBackUrl;

    /**
     * 坐席名称
     */
    private String agentName;

    /**
     * 坐席分机号
     */
    private String agentCode;

    /**
     * 1：坐席sip号
     * 2：webrtc
     * 3：坐席手机号
     */
    @NotNull(message = "登录方式不能为空")
    private Integer loginType;

    /**
     *
     */
    private Integer workType;

    /**
     * 座席密码
     */
    @NotNull(message = "坐席密码不能为空")
    @Size(min = 32, max = 64, message = "密码长度不对")
    private String passwd;

    /**
     * 绑定的电话号码
     */
    private String sipPhone;

    /**
     * 话后自动空闲间隔时长
     */
    private Integer afterInterval;

    /**
     * 主叫显号
     */
    private String diaplay;

    /**
     * 振铃时长
     */
    private Integer ringTime;


    /**
     * 扩展1
     */
    private String ext1;

    /**
     * 扩展2
     */
    private String ext2;

    /**
     * 扩展3
     */
    private String ext3;

    public String getCallBackUrl() {
        return callBackUrl;
    }

    public void setCallBackUrl(String callBackUrl) {
        this.callBackUrl = callBackUrl;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getAgentKey() {
        return agentKey;
    }

    public void setAgentKey(String agentKey) {
        this.agentKey = agentKey;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public Integer getLoginType() {
        return loginType;
    }

    public void setLoginType(Integer loginType) {
        this.loginType = loginType;
    }

    public Integer getWorkType() {
        return workType;
    }

    public void setWorkType(Integer workType) {
        this.workType = workType;
    }

    public String getPasswd() {
        return passwd;
    }

    public void setPasswd(String passwd) {
        this.passwd = passwd;
    }

    public String getSipPhone() {
        return sipPhone;
    }

    public void setSipPhone(String sipPhone) {
        this.sipPhone = sipPhone;
    }

    public Integer getAfterInterval() {
        return afterInterval;
    }

    public void setAfterInterval(Integer afterInterval) {
        this.afterInterval = afterInterval;
    }

    public String getDiaplay() {
        return diaplay;
    }

    public void setDiaplay(String diaplay) {
        this.diaplay = diaplay;
    }

    public Integer getRingTime() {
        return ringTime;
    }

    public void setRingTime(Integer ringTime) {
        this.ringTime = ringTime;
    }

    public String getExt1() {
        return ext1;
    }

    public void setExt1(String ext1) {
        this.ext1 = ext1;
    }

    public String getExt2() {
        return ext2;
    }

    public void setExt2(String ext2) {
        this.ext2 = ext2;
    }

    public String getExt3() {
        return ext3;
    }

    public void setExt3(String ext3) {
        this.ext3 = ext3;
    }

    @Override
    public String toString() {
        return "AgentVo{" +
                "id=" + id +
                ", companyId=" + companyId +
                ", agentId=" + agentId +
                ", agentKey='" + agentKey + '\'' +
                ", agentName='" + agentName + '\'' +
                ", agentCode='" + agentCode + '\'' +
                ", loginType=" + loginType +
                ", workType=" + workType +
                ", passwd='" + passwd + '\'' +
                ", sipPhone='" + sipPhone + '\'' +
                ", afterInterval=" + afterInterval +
                ", diaplay='" + diaplay + '\'' +
                ", ringTime=" + ringTime +
                ", ext1='" + ext1 + '\'' +
                ", ext2='" + ext2 + '\'' +
                ", ext3='" + ext3 + '\'' +
                '}';
    }
}
