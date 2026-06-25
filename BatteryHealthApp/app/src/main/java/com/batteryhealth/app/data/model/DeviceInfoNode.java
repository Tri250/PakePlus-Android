package com.batteryhealth.app.data.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备信息树节点
 * 用于构建设备信息的树形展示结构
 */
public class DeviceInfoNode {

    public static final int TYPE_CATEGORY = 0;
    public static final int TYPE_ITEM = 1;

    private int type;
    private String title;
    private String value;
    private String summary;
    private int iconResId;
    private List<DeviceInfoNode> children;
    private boolean expanded;

    public DeviceInfoNode() {
        this.children = new ArrayList<>();
        this.expanded = false;
    }

    public DeviceInfoNode(int type, String title, String value) {
        this();
        this.type = type;
        this.title = title;
        this.value = value;
    }

    public DeviceInfoNode(int type, String title, String value, int iconResId) {
        this(type, title, value);
        this.iconResId = iconResId;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public int getIconResId() {
        return iconResId;
    }

    public void setIconResId(int iconResId) {
        this.iconResId = iconResId;
    }

    public List<DeviceInfoNode> getChildren() {
        return children;
    }

    public void setChildren(List<DeviceInfoNode> children) {
        this.children = children;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public void addChild(DeviceInfoNode child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
    }

    public int getChildCount() {
        return children != null ? children.size() : 0;
    }

    public DeviceInfoNode getChild(int index) {
        return children != null && index >= 0 && index < children.size() ? children.get(index) : null;
    }

    public void toggleExpanded() {
        this.expanded = !this.expanded;
    }

    public int getTotalVisibleCount() {
        int count = 1;
        if (expanded && children != null) {
            for (DeviceInfoNode child : children) {
                count += child.getTotalVisibleCount();
            }
        }
        return count;
    }
}
