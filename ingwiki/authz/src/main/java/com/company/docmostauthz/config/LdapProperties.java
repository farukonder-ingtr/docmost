package com.company.docmostauthz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "docmost.ldap")
public class LdapProperties {

    private String userSearchBase;
    private String userSearchFilter;
    private String groupSearchBase;
    private String groupSearchFilter;
    private long groupsCacheTtlSeconds = 60;

    public String getUserSearchBase() {
        return userSearchBase;
    }

    public void setUserSearchBase(String userSearchBase) {
        this.userSearchBase = userSearchBase;
    }

    public String getUserSearchFilter() {
        return userSearchFilter;
    }

    public void setUserSearchFilter(String userSearchFilter) {
        this.userSearchFilter = userSearchFilter;
    }

    public String getGroupSearchBase() {
        return groupSearchBase;
    }

    public void setGroupSearchBase(String groupSearchBase) {
        this.groupSearchBase = groupSearchBase;
    }

    public String getGroupSearchFilter() {
        return groupSearchFilter;
    }

    public void setGroupSearchFilter(String groupSearchFilter) {
        this.groupSearchFilter = groupSearchFilter;
    }

    public long getGroupsCacheTtlSeconds() {
        return groupsCacheTtlSeconds;
    }

    public void setGroupsCacheTtlSeconds(long groupsCacheTtlSeconds) {
        this.groupsCacheTtlSeconds = groupsCacheTtlSeconds;
    }
}
