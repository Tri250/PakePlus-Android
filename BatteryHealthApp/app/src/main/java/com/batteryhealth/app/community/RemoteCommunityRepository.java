package com.batteryhealth.app.community;

import android.util.Log;

import com.batteryhealth.app.ui.community.CommunityFragment.Post;

import java.util.ArrayList;
import java.util.List;

/**
 * 远程社区仓库 - 存根实现
 * <p>
 * 预留给未来远程后端对接。当前所有操作均返回空数据或失败回调。
 */
public class RemoteCommunityRepository implements CommunityRepository {

    private static final String TAG = "RemoteCommunityRepo";

    @Override
    public List<Post> loadPosts() {
        Log.w(TAG, "RemoteCommunityRepository not implemented, returning empty list");
        return new ArrayList<>();
    }

    @Override
    public void savePosts(List<Post> posts) {
        Log.w(TAG, "RemoteCommunityRepository not implemented, savePosts ignored");
    }

    @Override
    public void addPost(Post post) {
        Log.w(TAG, "RemoteCommunityRepository not implemented, addPost ignored");
    }

    @Override
    public void likePost(String postId, OnCompleteListener listener) {
        Log.w(TAG, "RemoteCommunityRepository not implemented, likePost ignored");
        if (listener != null) {
            listener.onComplete(false);
        }
    }
}
